package com.example.data.places

import android.content.Context
import android.location.Location
import com.example.data.local.BusinessDao
import com.example.data.local.GuiaGenioDatabase
import com.example.data.local.OsmCacheEntity
import com.example.domain.model.BusinessCategory
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.GeoPointCoord
import com.example.domain.model.LocalityRegion
import com.example.domain.model.OsmBoundingBox
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class PlaceAutocompleteSuggestion(
  val placeId: String,
  val primaryText: String,
  val secondaryText: String,
  val business: DirectoryBusiness? = null,
)

class PlacesRepository(
  context: Context,
  private val businessDao: BusinessDao = GuiaGenioDatabase.getInstance(context).businessDao(),
  private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(14, TimeUnit.SECONDS)
      .build()

  private val nominatimMutex = Mutex()
  private var lastNominatimRequestMs: Long = 0L

  /** Autocompleta lugares reales mediante Photon (komoot) centrado en la localidad elegida. */
  suspend fun autocompletePlaces(
    query: String,
    locality: LocalityRegion,
  ): Result<List<PlaceAutocompleteSuggestion>> =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.length < 2) return@withContext Result.success(emptyList())

      try {
        val encodedQuery = URLEncoder.encode("$trimmed ${locality.name}", "UTF-8")
        // Regional bbox covering Neuquén, Alto Valle, Vaca Muerta y Cordillera neuquina
        val url =
          "https://photon.komoot.io/api/" +
            "?q=$encodedQuery" +
            "&lat=${locality.latitude}" +
            "&lon=${locality.longitude}" +
            "&bbox=-71.70,-40.60,-66.40,-36.80" +
            "&limit=6" +
            "&lang=es"

        val request =
          Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("Photon respondió HTTP ${response.code}")
            )
          }
          val body = response.body?.string().orEmpty()
          Result.success(parsePhotonFeatures(body, locality))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Geocodifica una dirección puntual usando Nominatim exclusivamente, respetando
   * el límite de 1 solicitud/segundo y User-Agent propio "GuiaGenio/1.0".
   */
  suspend fun geocodeAddressWithNominatim(
    addressQuery: String,
    locality: LocalityRegion,
  ): Result<GeoPointCoord?> =
    withContext(Dispatchers.IO) {
      val trimmed = addressQuery.trim()
      if (trimmed.length < 3) return@withContext Result.success(null)

      nominatimMutex.withLock {
        val elapsed = clockMs() - lastNominatimRequestMs
        if (lastNominatimRequestMs > 0L && elapsed < NOMINATIM_MIN_INTERVAL_MS) {
          delay(NOMINATIM_MIN_INTERVAL_MS - elapsed)
        }
        lastNominatimRequestMs = clockMs()

        try {
          val bbox = locality.boundingBox()
          val encoded = URLEncoder.encode("$trimmed, ${locality.name}, ${locality.province}, Argentina", "UTF-8")
          val url =
            "https://nominatim.openstreetmap.org/search" +
              "?q=$encoded" +
              "&format=jsonv2" +
              "&countrycodes=ar" +
              "&limit=1" +
              "&viewbox=${bbox.west},${bbox.north},${bbox.east},${bbox.south}"

          val request =
            Request.Builder()
              .url(url)
              .header("User-Agent", USER_AGENT)
              .header("Accept", "application/json")
              .get()
              .build()

          httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              return@withContext Result.failure(
                IllegalStateException("Nominatim respondió HTTP ${response.code}")
              )
            }
            val array = JSONArray(response.body?.string().orEmpty())
            val first = array.optJSONObject(0) ?: return@withContext Result.success(null)
            val lat = first.optString("lat").toDoubleOrNull()
            val lon = first.optString("lon").toDoubleOrNull()
            if (lat == null || lon == null) {
              Result.success(null)
            } else {
              Result.success(GeoPointCoord(lat, lon))
            }
          }
        } catch (e: Exception) {
          Result.failure(e)
        }
      }
    }

  /**
   * Consulta comercios reales en Overpass API (por bbox de localidad), con caché en Room TTL 24 h,
   * respaldo rápido por Photon OSM y uso de caché previa si no hay red.
   */
  suspend fun searchBusinesses(
    query: String,
    locality: LocalityRegion,
    category: BusinessCategory,
    openNowOnly: Boolean,
    deliveryHint: Boolean,
    searchCenterOverride: GeoPointCoord? = null,
  ): Result<List<DirectoryBusiness>> =
    withContext(Dispatchers.IO) {
      val center = searchCenterOverride ?: locality.latLng
      val effectiveLocality =
        if (category.id == DirectoryCategories.AllCategory.id && query.isBlank()) {
          locality.copy(radiusMeters = locality.radiusMeters.coerceAtMost(4500.0))
        } else {
          locality
        }
      val bbox = effectiveLocality.boundingBox(searchCenterOverride)
      val cacheKey = buildCacheKey(center, category.id, query, deliveryHint)
      val nowMs = clockMs()

      val cachedRow = runCatching { businessDao.getCachedSearch(cacheKey) }.getOrNull()
      if (cachedRow != null && (nowMs - cachedRow.cachedAtMillis) < CACHE_TTL_MS) {
        val restored = deserializeBusinesses(cachedRow.payloadJson, center)
        val filteredCached =
          if (openNowOnly) restored.filter { it.isOpenNow == true } else restored
        return@withContext Result.success(filteredCached)
      }

      val overpassQuery = buildOverpassBboxQuery(query.trim(), bbox, category, deliveryHint)

      var lastError: Exception? = null
      for (endpoint in OVERPASS_ENDPOINTS) {
        try {
          val formBody = FormBody.Builder().add("data", overpassQuery).build()
          val request =
            Request.Builder()
              .url(endpoint)
              .header("User-Agent", USER_AGENT)
              .header("Accept", "application/json")
              .post(formBody)
              .build()

          httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              throw IllegalStateException("Overpass API respondió HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val root = JSONObject(raw)
            val elements = root.optJSONArray("elements") ?: JSONArray()
            val parsed = parseOverpassElements(elements, locality, category, center)

            if (parsed.isNotEmpty()) {
              runCatching {
                businessDao.purgeExpiredCache(nowMs - CACHE_TTL_MS)
                businessDao.upsertCachedSearch(
                  OsmCacheEntity(
                    cacheKey = cacheKey,
                    payloadJson = serializeBusinesses(parsed),
                    cachedAtMillis = nowMs,
                  )
                )
              }

              val finalResults =
                if (openNowOnly) parsed.filter { it.isOpenNow == true } else parsed
              return@withContext Result.success(finalResults)
            }
            lastError = null
            break
          }
        } catch (e: Exception) {
          lastError = e
        }
      }

      // Respaldo 1: consultar puntos reales OpenStreetMap vía Photon si Overpass agota tiempo, limita o devuelve vacío
      val photonFallback =
        fetchPhotonBusinessesFallback(
          query = query,
          locality = locality,
          category = category,
          center = center,
        )
      if (photonFallback.isNotEmpty()) {
        runCatching {
          businessDao.upsertCachedSearch(
            OsmCacheEntity(
              cacheKey = cacheKey,
              payloadJson = serializeBusinesses(photonFallback),
              cachedAtMillis = nowMs,
            )
          )
        }
        val finalResults =
          if (openNowOnly) photonFallback.filter { it.isOpenNow == true } else photonFallback
        return@withContext Result.success(finalResults)
      }

      // Respaldo 2: devolver resultados previos en Room incluso si expiró el TTL de 24 h
      if (cachedRow != null) {
        val restored = deserializeBusinesses(cachedRow.payloadJson, center)
        val filteredCached =
          if (openNowOnly) restored.filter { it.isOpenNow == true } else restored
        return@withContext Result.success(filteredCached)
      }

      if (lastError == null) {
        return@withContext Result.success(emptyList())
      }

      Result.failure(
        lastError ?: IllegalStateException("No pudimos consultar comercios en OpenStreetMap.")
      )
    }

  private fun fetchPhotonBusinessesFallback(
    query: String,
    locality: LocalityRegion,
    category: BusinessCategory,
    center: GeoPointCoord,
  ): List<DirectoryBusiness> {
    val searchTerms =
      if (query.isNotBlank()) {
        listOf("${query.trim()} ${locality.name}")
      } else if (category.id == DirectoryCategories.AllCategory.id) {
        listOf("restaurante ${locality.name}", "farmacia ${locality.name}", "supermercado ${locality.name}")
      } else {
        val primaryTerm = category.placesQueryTerm.split(" ").firstOrNull().orEmpty().ifBlank { category.shortName }
        listOf("$primaryTerm ${locality.name}")
      }

    val collected = LinkedHashMap<String, DirectoryBusiness>()
    val maxDistanceMeters = maxOf(locality.radiusMeters * 2.5, 25_000.0).toFloat()

    for (term in searchTerms) {
      runCatching {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val url =
          "https://photon.komoot.io/api/" +
            "?q=$encoded" +
            "&lat=${center.latitude}" +
            "&lon=${center.longitude}" +
            "&bbox=-71.70,-40.60,-66.40,-36.80" +
            "&limit=12" +
            "&lang=es"
        val request =
          Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@use
          val body = response.body?.string().orEmpty()
          parsePhotonFeatures(body, locality).forEach { suggestion ->
            val biz = suggestion.business ?: return@forEach
            val dist = FloatArray(1)
            Location.distanceBetween(center.latitude, center.longitude, biz.latitude, biz.longitude, dist)
            if (dist[0] <= maxDistanceMeters) {
              val adjusted =
                if (category.id != DirectoryCategories.AllCategory.id && biz.categoryId == DirectoryCategories.AllCategory.id) {
                  biz.copy(
                    categoryId = category.id,
                    categoryName = category.name,
                    markerColorArgb = category.markerColorArgb,
                    distanceMeters = dist[0],
                  )
                } else {
                  biz.copy(distanceMeters = dist[0])
                }
              collected.putIfAbsent(adjusted.id, adjusted)
            }
          }
        }
      }
    }

    return collected.values.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
  }

  private fun buildCacheKey(
    center: GeoPointCoord,
    categoryId: String,
    query: String,
    deliveryHint: Boolean,
  ): String =
    String.format(
      Locale.US,
      "%.3f_%.3f_%s_%s_%b",
      center.latitude,
      center.longitude,
      categoryId,
      query.trim().lowercase(Locale.ROOT),
      deliveryHint,
    )

  companion object {
    const val USER_AGENT = "GuiaGenio/1.0 (com.aistudio.guiagenio.nqnvma)"
    const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    const val NOMINATIM_MIN_INTERVAL_MS = 1050L

    private val OVERPASS_ENDPOINTS =
      listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
      )

    fun buildOverpassBboxQuery(
      query: String,
      bbox: OsmBoundingBox,
      category: BusinessCategory,
      deliveryHint: Boolean,
    ): String {
      val bboxClause = "(${bbox.toOverpassBbox()})"
      val escapedName = escapeOverpassRegex(query)
      val deliveryFilter = if (deliveryHint) """["delivery"="yes"]""" else ""

      val clauses =
        if (escapedName.isNotEmpty() && category.id == DirectoryCategories.AllCategory.id) {
          listOf("""["name"~"$escapedName",i]""")
        } else if (escapedName.isNotEmpty()) {
          category.osmFilterClauses.map { clause -> """$clause["name"~"$escapedName",i]""" }
        } else {
          category.osmFilterClauses.map { clause -> """$clause["name"]""" }
        }

      val statements =
        clauses.joinToString("\n  ") { clause -> "nwr$clause$deliveryFilter$bboxClause;" }

      return """
        [out:json][timeout:18];
        (
          $statements
        );
        out center 45;
      """.trimIndent()
    }

    fun parseOverpassElements(
      elements: JSONArray,
      locality: LocalityRegion,
      fallbackCategory: BusinessCategory,
      referenceCenter: GeoPointCoord,
    ): List<DirectoryBusiness> {
      val seenIds = HashSet<String>()
      val businesses = mutableListOf<DirectoryBusiness>()

      for (i in 0 until elements.length()) {
        val el = elements.optJSONObject(i) ?: continue
        val tagsObj = el.optJSONObject("tags") ?: continue
        val tags = jsonObjectToMap(tagsObj)
        val name = tags["name"]?.trim().orEmpty()
        if (name.isBlank()) continue

        val centerObj = el.optJSONObject("center")
        val lat =
          if (el.has("lat")) el.optDouble("lat")
          else centerObj?.optDouble("lat") ?: Double.NaN
        val lon =
          if (el.has("lon")) el.optDouble("lon")
          else centerObj?.optDouble("lon") ?: Double.NaN
        if (lat.isNaN() || lon.isNaN()) continue

        val osmType = el.optString("type", "node")
        val osmId = el.optLong("id", i.toLong())
        val uniqueId = "osm_${osmType}_$osmId"
        if (!seenIds.add(uniqueId)) continue

        val inferredCategory = DirectoryCategories.inferFromOsmTags(tags, fallbackCategory)
        val street = tags["addr:street"].orEmpty().trim()
        val houseNumber = tags["addr:housenumber"].orEmpty().trim()
        val city = tags["addr:city"]?.trim()?.takeIf { it.isNotEmpty() } ?: locality.name
        val address =
          when {
            street.isNotEmpty() && houseNumber.isNotEmpty() -> "$street $houseNumber"
            street.isNotEmpty() -> street
            else -> "$city, ${locality.province}"
          }

        val phone =
          tags["contact:phone"] ?: tags["phone"] ?: tags["contact:mobile"] ?: tags["mobile"]
        val whatsapp =
          normalizeTagWhatsApp(tags["contact:whatsapp"])
            ?: extractArgentineWhatsAppCandidate(phone, phone)
        val website = tags["contact:website"] ?: tags["website"] ?: tags["url"]
        val hoursRaw = tags["opening_hours"]?.trim()?.takeIf { it.isNotEmpty() }
        val hasDelivery =
          tags["delivery"].equals("yes", ignoreCase = true) ||
            tags["takeaway"].equals("yes", ignoreCase = true)

        val distanceResult = FloatArray(1)
        Location.distanceBetween(
          referenceCenter.latitude,
          referenceCenter.longitude,
          lat,
          lon,
          distanceResult,
        )

        businesses.add(
          DirectoryBusiness(
            id = uniqueId,
            name = name,
            categoryId = inferredCategory.id,
            categoryName = inferredCategory.name,
            localityName = city,
            address = address,
            latitude = lat,
            longitude = lon,
            phoneNumber = phone?.trim()?.takeIf { it.isNotEmpty() },
            whatsappNumber = whatsapp,
            websiteUri = website?.trim()?.takeIf { it.isNotEmpty() },
            description = tags["description"] ?: tags["cuisine"]?.replace(";", ", "),
            isOpenNow = evaluateOsmOpeningHours(hoursRaw),
            openingHoursRaw = hoursRaw,
            openingHoursSummary = listOfNotNull(hoursRaw),
            hasDelivery = hasDelivery,
            source = BusinessSource.OPENSTREETMAP,
            distanceMeters = distanceResult[0],
            markerColorArgb = inferredCategory.markerColorArgb,
          )
        )
      }

      return businesses.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
    }

    fun parsePhotonFeatures(
      rawGeoJson: String,
      locality: LocalityRegion,
    ): List<PlaceAutocompleteSuggestion> {
      if (rawGeoJson.isBlank()) return emptyList()
      val root = JSONObject(rawGeoJson)
      val features = root.optJSONArray("features") ?: return emptyList()
      val suggestions = mutableListOf<PlaceAutocompleteSuggestion>()

      for (i in 0 until features.length()) {
        val feature = features.optJSONObject(i) ?: continue
        val geometry = feature.optJSONObject("geometry") ?: continue
        val coords = geometry.optJSONArray("coordinates") ?: continue
        if (coords.length() < 2) continue
        val lon = coords.optDouble(0, Double.NaN)
        val lat = coords.optDouble(1, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) continue

        val props = feature.optJSONObject("properties") ?: continue
        val street = props.optString("street").trim()
        val houseNumber = props.optString("housenumber").trim()
        val name =
          props.optString("name").trim().ifBlank {
            listOf(street, houseNumber).filter { it.isNotBlank() }.joinToString(" ")
          }
        if (name.isBlank()) continue

        val city =
          props.optString("city").ifBlank {
            props.optString("town").ifBlank {
              props.optString("county").ifBlank { locality.name }
            }
          }
        val state = props.optString("state").ifBlank { locality.province }
        val streetAddr = listOf(street, houseNumber).filter { it.isNotBlank() }.joinToString(" ")
        val secondary =
          listOf(streetAddr, city, state).filter { it.isNotBlank() }.joinToString(", ")

        val osmKey = props.optString("osm_key").trim()
        val osmValue = props.optString("osm_value").trim()
        val tags =
          buildMap {
            if (osmKey.isNotBlank() && osmValue.isNotBlank()) {
              put(osmKey, osmValue)
            }
          }
        val inferredCategory =
          DirectoryCategories.inferFromOsmTags(tags, DirectoryCategories.AllCategory)

        val dist = FloatArray(1)
        Location.distanceBetween(locality.latitude, locality.longitude, lat, lon, dist)

        val osmType = props.optString("osm_type", "N")
        val osmId = props.optLong("osm_id", i.toLong())
        val placeId = "photon_${osmType}_$osmId"

        val business =
          DirectoryBusiness(
            id = placeId,
            name = name,
            categoryId = inferredCategory.id,
            categoryName = inferredCategory.name,
            localityName = city,
            address = streetAddr.ifBlank { "$city, $state" },
            latitude = lat,
            longitude = lon,
            phoneNumber = null,
            whatsappNumber = null,
            websiteUri = null,
            description = osmValue.takeIf { it.isNotBlank() }?.replace('_', ' '),
            isOpenNow = null,
            openingHoursRaw = null,
            openingHoursSummary = emptyList(),
            hasDelivery = false,
            source = BusinessSource.OPENSTREETMAP,
            distanceMeters = dist[0],
            markerColorArgb = inferredCategory.markerColorArgb,
          )

        suggestions.add(
          PlaceAutocompleteSuggestion(
            placeId = placeId,
            primaryText = name,
            secondaryText = secondary,
            business = business,
          )
        )
      }
      return suggestions
    }

    fun serializeBusinesses(list: List<DirectoryBusiness>): String {
      val array = JSONArray()
      list.forEach { b ->
        val obj =
          JSONObject()
            .put("id", b.id)
            .put("name", b.name)
            .put("categoryId", b.categoryId)
            .put("categoryName", b.categoryName)
            .put("localityName", b.localityName)
            .put("address", b.address)
            .put("latitude", b.latitude)
            .put("longitude", b.longitude)
            .put("phoneNumber", b.phoneNumber ?: "")
            .put("whatsappNumber", b.whatsappNumber ?: "")
            .put("websiteUri", b.websiteUri ?: "")
            .put("description", b.description ?: "")
            .put("openingHoursRaw", b.openingHoursRaw ?: "")
            .put("hasDelivery", b.hasDelivery)
            .put("status", b.status.name)
            .put("markerColorArgb", b.markerColorArgb)
        array.put(obj)
      }
      return array.toString()
    }

    fun deserializeBusinesses(
      payloadJson: String,
      referenceCenter: GeoPointCoord,
    ): List<DirectoryBusiness> {
      if (payloadJson.isBlank()) return emptyList()
      val array = JSONArray(payloadJson)
      val out = ArrayList<DirectoryBusiness>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val lat = obj.optDouble("latitude", Double.NaN)
        val lon = obj.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) continue
        val hoursRaw = obj.optString("openingHoursRaw").takeIf { it.isNotBlank() }
        val dist = FloatArray(1)
        Location.distanceBetween(
          referenceCenter.latitude,
          referenceCenter.longitude,
          lat,
          lon,
          dist,
        )
        out.add(
          DirectoryBusiness(
            id = obj.optString("id"),
            name = obj.optString("name"),
            categoryId = obj.optString("categoryId"),
            categoryName = obj.optString("categoryName"),
            localityName = obj.optString("localityName"),
            address = obj.optString("address"),
            latitude = lat,
            longitude = lon,
            phoneNumber = obj.optString("phoneNumber").takeIf { it.isNotBlank() },
            whatsappNumber = obj.optString("whatsappNumber").takeIf { it.isNotBlank() },
            websiteUri = obj.optString("websiteUri").takeIf { it.isNotBlank() },
            description = obj.optString("description").takeIf { it.isNotBlank() },
            isOpenNow = evaluateOsmOpeningHours(hoursRaw),
            openingHoursRaw = hoursRaw,
            openingHoursSummary = listOfNotNull(hoursRaw),
            hasDelivery = obj.optBoolean("hasDelivery", false),
            source = BusinessSource.OPENSTREETMAP,
            status = BusinessStatus.fromString(obj.optString("status"), BusinessStatus.APPROVED),
            distanceMeters = dist[0],
            markerColorArgb = obj.optInt("markerColorArgb", 0xFF1546A6.toInt()),
          )
        )
      }
      return out.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
    }

    private fun jsonObjectToMap(obj: JSONObject?): Map<String, String> {
      if (obj == null) return emptyMap()
      val map = LinkedHashMap<String, String>()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val value = obj.optString(key)
        if (value.isNotBlank()) map[key] = value
      }
      return map
    }

    private fun escapeOverpassRegex(input: String): String {
      return input.replace(Regex("""[\\^$.|?*+()\[\]{}"]""")) { "\\${it.value}" }
    }

    private fun normalizeTagWhatsApp(raw: String?): String? {
      if (raw.isNullOrBlank()) return null
      val digits = raw.filter { it.isDigit() }
      if (digits.length < 8) return null
      return when {
        digits.startsWith("549") -> "+$digits"
        digits.startsWith("54") -> "+549${digits.removePrefix("54")}"
        digits.startsWith("0") -> "+549${digits.drop(1)}"
        else -> "+549$digits"
      }
    }

    fun extractArgentineWhatsAppCandidate(intlPhone: String?, nationalPhone: String?): String? {
      val source = intlPhone ?: nationalPhone ?: return null
      val digits = source.filter { it.isDigit() }
      if (digits.length < 8) return null
      return when {
        digits.startsWith("549") && digits.length >= 12 -> "+$digits"
        digits.startsWith("54") && digits.length >= 12 && digits[2] == '9' -> "+$digits"
        nationalPhone?.contains("15-") == true || nationalPhone?.contains(" 15 ") == true ->
          "+549$digits"
        else -> null
      }
    }

    fun evaluateOsmOpeningHours(raw: String?, now: Calendar = Calendar.getInstance()): Boolean? {
      val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
      if (text.equals("24/7", ignoreCase = true)) return true
      if (text.equals("off", ignoreCase = true) || text.equals("closed", ignoreCase = true)) {
        return false
      }

      val dayMap =
        mapOf(
          Calendar.MONDAY to 0,
          Calendar.TUESDAY to 1,
          Calendar.WEDNESDAY to 2,
          Calendar.THURSDAY to 3,
          Calendar.FRIDAY to 4,
          Calendar.SATURDAY to 5,
          Calendar.SUNDAY to 6,
        )
      val todayIdx = dayMap[now.get(Calendar.DAY_OF_WEEK)] ?: return null
      val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

      val dayAliases =
        mapOf(
          "mo" to 0, "lu" to 0,
          "tu" to 1, "ma" to 1,
          "we" to 2, "mi" to 2,
          "th" to 3, "ju" to 3,
          "fr" to 4, "vi" to 4,
          "sa" to 5, "sá" to 5,
          "su" to 6, "do" to 6,
        )

      val rules = text.split(";").map { it.trim() }.filter { it.isNotEmpty() }
      var anyRuleRecognized = false

      for (rule in rules) {
        val lower = rule.lowercase(Locale.US)
        if (lower.startsWith("ph ") || lower == "ph off") {
          anyRuleRecognized = true
          continue
        }

        val timeRegex = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""")
        val timeMatches = timeRegex.findAll(lower).toList()

        if (timeMatches.isEmpty()) {
          if (lower.endsWith("off") || lower.endsWith("closed")) {
            anyRuleRecognized = true
          }
          continue
        }

        val dayPart = lower.substringBefore(timeMatches.first().value).trim()
        val hasValidDayTokens =
          dayPart.isBlank() || hasRecognizedDayTokens(dayPart, dayAliases)
        if (!hasValidDayTokens) continue
        anyRuleRecognized = true

        val appliesToday = matchesDayPart(dayPart, todayIdx, dayAliases)
        if (appliesToday) {
          for (m in timeMatches) {
            val startMin = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            val endMin = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            val inside =
              if (endMin >= startMin) nowMinutes in startMin..endMin
              else nowMinutes >= startMin || nowMinutes <= endMin
            if (inside) return true
          }
        }
      }

      return if (anyRuleRecognized) false else null
    }

    private fun hasRecognizedDayTokens(dayPart: String, dayAliases: Map<String, Int>): Boolean {
      return dayPart.split(",").any { chunk ->
        val token = chunk.trim()
        if (token.contains("-")) {
          val startStr = token.substringBefore("-").trim().take(2)
          val endStr = token.substringAfter("-").trim().take(2)
          dayAliases.containsKey(startStr) && dayAliases.containsKey(endStr)
        } else {
          dayAliases.containsKey(token.take(2))
        }
      }
    }

    private fun matchesDayPart(
      dayPart: String,
      todayIdx: Int,
      dayAliases: Map<String, Int>,
    ): Boolean {
      if (dayPart.isBlank()) return true
      return dayPart.split(",").any { chunk ->
        val token = chunk.trim()
        if (token.contains("-")) {
          val startStr = token.substringBefore("-").trim().take(2)
          val endStr = token.substringAfter("-").trim().take(2)
          val startIdx = dayAliases[startStr]
          val endIdx = dayAliases[endStr]
          if (startIdx != null && endIdx != null) {
            if (startIdx <= endIdx) todayIdx in startIdx..endIdx
            else todayIdx >= startIdx || todayIdx <= endIdx
          } else {
            false
          }
        } else {
          dayAliases[token.take(2)] == todayIdx
        }
      }
    }
  }
}
