package com.example.data.places

import android.content.Context
import com.example.data.local.BusinessDao
import com.example.data.local.OsmCacheEntity
import com.example.domain.model.BusinessCategory
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.GeoPointCoord
import com.example.domain.model.LocalityRegion
import com.example.domain.model.OsmBoundingBox
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class PlaceAutocompleteSuggestion(
  val placeId: String,
  val primaryText: String,
  val secondaryText: String = "",
  val business: DirectoryBusiness? = null,
)

class PlacesRepository(
  private val context: Context,
  private val businessDao: BusinessDao? = null,
  private val clockMs: () -> Long = System::currentTimeMillis,
) {
  private val client = OkHttpClient()

  suspend fun searchBusinesses(
    query: String,
    locality: LocalityRegion,
    category: BusinessCategory,
    openNowOnly: Boolean = false,
    deliveryHint: Boolean = false,
    searchCenterOverride: GeoPointCoord? = null,
  ): Result<List<DirectoryBusiness>> = withContext(Dispatchers.IO) {
    runCatching {
      val referenceCenter = searchCenterOverride ?: locality.latLng
      val cacheKey = buildCacheKey(referenceCenter, category, openNowOnly, deliveryHint)

      if (businessDao != null) {
        val cached = businessDao.getCachedSearch(cacheKey)
        if (cached != null && clockMs() - cached.cachedAtMillis <= CACHE_TTL_MS) {
          return@runCatching filterBusinesses(parseSerializedBusinesses(cached.payloadJson), query, openNowOnly, deliveryHint)
        }
      }

      val bbox = locality.boundingBox(searchCenterOverride)
      val overpassQuery = buildOverpassBboxQuery(query, bbox, category, deliveryHint)
      val request = Request.Builder()
        .url("https://overpass-api.de/api/interpreter")
        .header("User-Agent", USER_AGENT)
        .post("data=$overpassQuery".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("Overpass respondió ${response.code}")
        val elements = JSONObject(response.body?.string().orEmpty()).optJSONArray("elements") ?: JSONArray()
        val businesses = parseOverpassElements(elements, locality, category, referenceCenter)
        if (businessDao != null) {
          businessDao.upsertCachedSearch(OsmCacheEntity(cacheKey, serializeBusinesses(businesses), clockMs()))
        }
        filterBusinesses(businesses, query, openNowOnly, deliveryHint)
      }
    }
  }

  suspend fun autocompletePlaces(query: String, locality: LocalityRegion): Result<List<PlaceAutocompleteSuggestion>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@runCatching emptyList()
        val url = "https://photon.komoot.io/api/?q=$trimmed&lat=${locality.latitude}&lon=${locality.longitude}&limit=8"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw IllegalStateException("Photon respondió ${response.code}")
          parsePhotonFeatures(response.body?.string().orEmpty(), locality)
        }
      }
    }

  private fun filterBusinesses(businesses: List<DirectoryBusiness>, query: String, openNowOnly: Boolean, deliveryHint: Boolean): List<DirectoryBusiness> {
    val text = query.trim().lowercase(Locale("es", "AR"))
    return businesses.filter { business ->
      val matchesText = text.isEmpty() || business.name.lowercase(Locale("es", "AR")).contains(text) || business.categoryName.lowercase(Locale("es", "AR")).contains(text) || business.address.lowercase(Locale("es", "AR")).contains(text)
      val matchesOpen = !openNowOnly || business.isOpenNow == true
      val matchesDelivery = !deliveryHint || business.hasDelivery
      matchesText && matchesOpen && matchesDelivery
    }
  }

  companion object {
    const val USER_AGENT = "GuiaGenioAndroid/1.0 (OpenStreetMap directory)"
    const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    private fun buildCacheKey(center: GeoPointCoord, category: BusinessCategory, openNowOnly: Boolean, deliveryHint: Boolean): String {
      val lat = String.format(Locale.US, "%.3f", center.latitude)
      val lon = String.format(Locale.US, "%.3f", center.longitude)
      val openPart = if (openNowOnly) "open" else ""
      return "${lat}_${lon}_${category.id}_${openPart}_${deliveryHint}"
    }

    fun serializeBusinesses(businesses: List<DirectoryBusiness>): String {
      val array = JSONArray()
      businesses.forEach { business ->
        array.put(JSONObject().apply {
          put("id", business.id)
          put("name", business.name)
          put("categoryId", business.categoryId)
          put("categoryName", business.categoryName)
          put("localityName", business.localityName)
          put("address", business.address)
          put("latitude", business.latitude)
          put("longitude", business.longitude)
          put("phoneNumber", business.phoneNumber ?: JSONObject.NULL)
          put("whatsappNumber", business.whatsappNumber ?: JSONObject.NULL)
          put("websiteUri", business.websiteUri ?: JSONObject.NULL)
          put("description", business.description ?: JSONObject.NULL)
          put("isOpenNow", business.isOpenNow ?: JSONObject.NULL)
          put("openingHoursRaw", business.openingHoursRaw ?: JSONObject.NULL)
          put("hasDelivery", business.hasDelivery)
          put("source", business.source.name)
          put("status", business.status.name)
          put("distanceMeters", business.distanceMeters ?: JSONObject.NULL)
          put("markerColorArgb", business.markerColorArgb)
        })
      }
      return array.toString()
    }

    fun parseSerializedBusinesses(raw: String): List<DirectoryBusiness> {
      if (raw.isBlank()) return emptyList()
      return try {
        val array = JSONArray(raw)
        buildList {
          for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            val source = runCatching { BusinessSource.valueOf(json.optString("source", "OPENSTREETMAP")) }.getOrElse { BusinessSource.OPENSTREETMAP }
            val status = runCatching { BusinessStatus.valueOf(json.optString("status", "APPROVED")) }.getOrElse { BusinessStatus.APPROVED }
            fun nullableString(key: String): String? = json.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
            fun nullableFloat(key: String): Float? = json.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.toFloatOrNull()
            fun nullableBoolean(key: String): Boolean? = json.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.toBooleanStrictOrNull()
            add(DirectoryBusiness(
              id = json.optString("id"),
              name = json.optString("name"),
              categoryId = json.optString("categoryId"),
              categoryName = json.optString("categoryName"),
              localityName = json.optString("localityName"),
              address = json.optString("address"),
              latitude = json.optDouble("latitude"),
              longitude = json.optDouble("longitude"),
              phoneNumber = nullableString("phoneNumber"),
              whatsappNumber = nullableString("whatsappNumber"),
              websiteUri = nullableString("websiteUri"),
              description = nullableString("description"),
              isOpenNow = nullableBoolean("isOpenNow"),
              openingHoursRaw = nullableString("openingHoursRaw"),
              hasDelivery = json.optBoolean("hasDelivery"),
              source = source,
              status = status,
              distanceMeters = nullableFloat("distanceMeters"),
              markerColorArgb = json.optInt("markerColorArgb", 0xFF1546A6.toInt()),
            ))
          }
        }
      } catch (_: Exception) {
        emptyList()
      }
    }

    fun buildOverpassBboxQuery(query: String, bbox: OsmBoundingBox, category: BusinessCategory, deliveryHint: Boolean): String {
      val area = bbox.toOverpassBbox()
      val clauses = buildString {
        if (category.osmFilterClauses.isNotEmpty()) {
          category.osmFilterClauses.forEach { clause -> append("nwr$clause($area);") }
        } else {
          append("nwr[\"name\"]($area);")
        }
        if (deliveryHint) append("nwr[\"delivery\"=\"yes\"]($area);")
      }
      return "[out:json][timeout:25];($clauses);out center tags;"
    }

    fun parseOverpassElements(elements: JSONArray, locality: LocalityRegion, fallbackCategory: BusinessCategory, referenceCenter: GeoPointCoord): List<DirectoryBusiness> {
      val result = mutableListOf<DirectoryBusiness>()
      for (i in 0 until elements.length()) {
        val element = elements.optJSONObject(i) ?: continue
        val tags = element.optJSONObject("tags") ?: JSONObject()
        val name = tags.optString("name").trim()
        if (name.isBlank()) continue
        val center = element.optJSONObject("center")
        val latitude = element.optDouble("lat", center?.optDouble("lat", locality.latitude) ?: locality.latitude)
        val longitude = element.optDouble("lon", center?.optDouble("lon", locality.longitude) ?: locality.longitude)
        val category = DirectoryCategories.inferFromOsmTags(tagMap(tags), fallbackCategory)
        val street = tags.optString("addr:street").trim()
        val house = tags.optString("addr:housenumber").trim()
        val address = buildString {
          append(street)
          if (street.isNotBlank() && house.isNotBlank()) append(" ")
          append(house)
        }.ifBlank { tags.optString("addr:full").trim().ifBlank { "Dirección no informada" } }
        val openingHours = tags.optString("opening_hours").trim().ifBlank { null }
        val phone = tags.optString("phone").ifBlank { tags.optString("contact:phone") }.trim()
        val whatsapp = extractArgentineWhatsAppCandidate(tags.optString("contact:whatsapp").trim().ifBlank { null }, phone.ifBlank { null })
        val delivery = tags.optString("delivery").lowercase(Locale.US) in setOf("yes", "only", "designated")
        result.add(DirectoryBusiness(
          id = "osm_${element.optString("type", "nwr")}_${element.optLong("id", i.toLong())}",
          name = name,
          categoryId = category.id,
          categoryName = category.name,
          localityName = locality.name,
          address = address,
          latitude = latitude,
          longitude = longitude,
          phoneNumber = phone.ifBlank { null },
          whatsappNumber = whatsapp,
          websiteUri = tags.optString("website").trim().ifBlank { null },
          description = tags.optString("description").trim().ifBlank { null },
          isOpenNow = evaluateOsmOpeningHours(openingHours, Calendar.getInstance()),
          openingHoursRaw = openingHours,
          hasDelivery = delivery,
          source = BusinessSource.OPENSTREETMAP,
          status = BusinessStatus.APPROVED,
          distanceMeters = haversineMeters(referenceCenter, GeoPointCoord(latitude, longitude)),
          markerColorArgb = category.markerColorArgb,
        ))
      }
      return result.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }.distinctBy { it.id }
    }

    fun parsePhotonFeatures(raw: String, locality: LocalityRegion): List<PlaceAutocompleteSuggestion> {
      return try {
        val features = JSONObject(raw).optJSONArray("features") ?: JSONArray()
        buildList {
          for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            val longitude = coordinates?.optDouble(0, locality.longitude) ?: locality.longitude
            val latitude = coordinates?.optDouble(1, locality.latitude) ?: locality.latitude
            val name = properties.optString("name").trim()
            if (name.isBlank()) continue
            val street = properties.optString("street").trim()
            val house = properties.optString("housenumber").trim()
            val city = properties.optString("city").ifBlank { properties.optString("county") }.trim()
            val address = buildString {
              append(street)
              if (street.isNotBlank() && house.isNotBlank()) append(" ")
              append(house)
            }.ifBlank { city.ifBlank { locality.name } }
            val osmKey = properties.optString("osm_key")
            val osmValue = properties.optString("osm_value")
            val tags = if (osmKey.isNotBlank() && osmValue.isNotBlank()) mapOf(osmKey to osmValue) else emptyMap()
            val category = DirectoryCategories.inferFromOsmTags(tags, DirectoryCategories.AllCategory)
            val business = DirectoryBusiness(
              id = "photon_${properties.optString("osm_type")}_${properties.optLong("osm_id", i.toLong())}",
              name = name,
              categoryId = category.id,
              categoryName = category.name,
              localityName = city.ifBlank { locality.name },
              address = address,
              latitude = latitude,
              longitude = longitude,
              source = BusinessSource.OPENSTREETMAP,
              status = BusinessStatus.APPROVED,
              markerColorArgb = category.markerColorArgb,
            )
            add(PlaceAutocompleteSuggestion(
              placeId = "${properties.optString("osm_type")}_${properties.optLong("osm_id", i.toLong())}",
              primaryText = name,
              secondaryText = address,
              business = business,
            ))
          }
        }
      } catch (_: Exception) {
        emptyList()
      }
    }

    fun evaluateOsmOpeningHours(raw: String?, calendar: Calendar): Boolean? {
      val value = raw?.trim()?.lowercase(Locale.US) ?: return null
      if (value.isBlank() || value in setOf("con cita previa", "unknown", "appointment")) return null
      if (value == "24/7") return true
      if (value == "off") return false
      val dayCode = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "mo"; Calendar.TUESDAY -> "tu"; Calendar.WEDNESDAY -> "we"
        Calendar.THURSDAY -> "th"; Calendar.FRIDAY -> "fr"; Calendar.SATURDAY -> "sa"
        else -> "su"
      }
      val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
      for (section in value.split(';')) {
        val match = Regex("^([a-z]{2}(?:-[a-z]{2}|(?:,[a-z]{2})*)?)\\s+(.+)$").find(section.trim()) ?: continue
        val dayText = match.groupValues[1]
        val times = match.groupValues[2]
        val dayMatches = if (dayText.contains('-')) {
          val days = listOf("mo", "tu", "we", "th", "fr", "sa", "su")
          val bounds = dayText.split('-')
          val start = days.indexOf(bounds[0]); val end = days.indexOf(bounds[1]); val current = days.indexOf(dayCode)
          start >= 0 && end >= 0 && current in start..end
        } else if (',' in dayText) {
          dayCode in dayText.split(',').map { it.trim() }
        } else {
          dayText == dayCode
        }
        if (!dayMatches) continue
        for (range in times.split(',')) {
          val bounds = range.trim().split('-')
          if (bounds.size != 2) continue
          val start = parseClockMinutes(bounds[0]) ?: continue
          val end = parseClockMinutes(bounds[1]) ?: continue
          if (nowMinutes in start..end) return true
        }
      }
      return false
    }

    fun extractArgentineWhatsAppCandidate(whatsappCandidate: String?, alternatePhone: String?): String? {
      val explicit = whatsappCandidate?.let { normalizeWhatsApp(it) }
      if (explicit != null) return explicit
      return alternatePhone?.let { normalizeWhatsApp(it, requireExplicitMobile = true) }
    }

    private fun normalizeWhatsApp(raw: String, requireExplicitMobile: Boolean = false): String? {
      var digits = raw.filter(Char::isDigit)
      if (digits.isEmpty()) return null
      if (digits.startsWith("549") && digits.length >= 12) return "+$digits"
      if (digits.startsWith("54") && digits.length >= 12) return null
      if (digits.startsWith("0")) digits = digits.drop(1)
      if (digits.startsWith("15")) digits = digits.drop(2)
      if (digits.length == 10 && digits.startsWith("299") && (!requireExplicitMobile || raw.contains("15"))) return "+549$digits"
      return null
    }

    private fun parseClockMinutes(raw: String): Int? {
      val match = Regex("^(\\d{2}):(\\d{2})$").find(raw.trim()) ?: return null
      val hour = match.groupValues[1].toIntOrNull() ?: return null
      val minute = match.groupValues[2].toIntOrNull() ?: return null
      if (hour !in 0..23 || minute !in 0..59) return null
      return hour * 60 + minute
    }

    private fun tagMap(tags: JSONObject): Map<String, String> {
      val map = mutableMapOf<String, String>()
      tags.keys().forEach { key -> map[key] = tags.optString(key) }
      return map
    }

    private fun haversineMeters(a: GeoPointCoord, b: GeoPointCoord): Float {
      val earthRadius = 6_371_000.0
      val lat1 = Math.toRadians(a.latitude)
      val lat2 = Math.toRadians(b.latitude)
      val deltaLat = lat2 - lat1
      val deltaLon = Math.toRadians(b.longitude - a.longitude)
      val value = sin(deltaLat / 2) * sin(deltaLat / 2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
      val c = 2 * atan2(sqrt(value), sqrt(1 - value))
      return (earthRadius * c).toFloat()
    }
  }
}
