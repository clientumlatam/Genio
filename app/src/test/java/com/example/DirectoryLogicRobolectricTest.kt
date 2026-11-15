package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GuiaGenioDatabase
import com.example.data.local.OsmCacheEntity
import com.example.data.local.PublishedBusinessEntity
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.PlacesRepository
import com.example.domain.model.BusinessSource
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.GeoPointCoord
import com.example.domain.model.PatagonianLocalities
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectoryLogicRobolectricTest {

  private lateinit var database: GuiaGenioDatabase
  private lateinit var repository: PublishedBusinessRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, GuiaGenioDatabase::class.java).build()
    repository = PublishedBusinessRepository(database.businessDao())
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `save toggle and delete published business in Room`() = runBlocking {
    val id =
      repository.saveBusiness(
        PublishedBusinessEntity(
          name = "Ferretería Vaca Muerta",
          categoryId = "ferreterias_construccion",
          localityId = "anelo",
          address = "Ruta 7 Km 94",
          latitude = PatagonianLocalities.Anelo.latitude,
          longitude = PatagonianLocalities.Anelo.longitude,
          phone = "299 442 1234",
          whatsapp = "299 512 9876",
          website = "https://ferreteriavm.com.ar",
          description = "Insumos industriales y herramientas",
          isOpenNow = true,
          hasDelivery = false,
        )
      )

    var list = repository.publishedBusinesses.first()
    assertEquals(1, list.size)
    assertEquals("Ferretería Vaca Muerta", list.first().name)
    assertTrue(list.first().isOpenNow)

    repository.toggleDeliveryStatus(id, true)
    repository.toggleOpenStatus(id, false)
    list = repository.publishedBusinesses.first()
    assertTrue(list.first().hasDelivery)
    assertEquals(false, list.first().isOpenNow)

    repository.deleteBusiness(id)
    assertTrue(repository.publishedBusinesses.first().isEmpty())
  }

  @Test
  fun `store and expire Overpass search cache in Room with 24h TTL`() = runBlocking {
    val dao = database.businessDao()
    val nowMs = 1_700_000_000_000L
    val context = ApplicationProvider.getApplicationContext<Context>()
    var fakeClock = nowMs
    val placesRepo = PlacesRepository(context, businessDao = dao, clockMs = { fakeClock })

    val sampleElements =
      JSONArray(
        """
        [
          {
            "type": "node",
            "id": 1001,
            "lat": -38.9516,
            "lon": -68.0591,
            "tags": {
              "name": "Farmacia del Valle",
              "amenity": "pharmacy",
              "addr:street": "Av. Argentina",
              "addr:housenumber": "320",
              "phone": "+54 299 442-1122",
              "contact:whatsapp": "299 555-0199",
              "website": "https://farmaciadelvalle.com.ar",
              "opening_hours": "24/7",
              "delivery": "yes"
            }
          }
        ]
        """.trimIndent()
      )

    val parsed =
      PlacesRepository.parseOverpassElements(
        elements = sampleElements,
        locality = PatagonianLocalities.NeuquenCapital,
        fallbackCategory = DirectoryCategories.AllCategory,
        referenceCenter = PatagonianLocalities.NeuquenCapital.latLng,
      )
    assertEquals(1, parsed.size)
    val farmacia = parsed.first()
    assertEquals("salud_farmacias", farmacia.categoryId)
    assertEquals("Av. Argentina 320", farmacia.address)
    assertEquals("+5492995550199", farmacia.whatsappNumber)
    assertTrue(farmacia.hasDelivery)
    assertEquals(true, farmacia.isOpenNow)
    assertEquals(BusinessSource.OPENSTREETMAP, farmacia.source)

    val cacheKey = "-38.952_-68.059_all__false"
    dao.upsertCachedSearch(
      OsmCacheEntity(
        cacheKey = cacheKey,
        payloadJson = PlacesRepository.serializeBusinesses(parsed),
        cachedAtMillis = nowMs,
      )
    )

    // Within 24h -> served straight from Room cache without network
    fakeClock = nowMs + 60_000L
    val cachedResult =
      placesRepo.searchBusinesses(
        query = "",
        locality = PatagonianLocalities.NeuquenCapital,
        category = DirectoryCategories.AllCategory,
        openNowOnly = false,
        deliveryHint = false,
      )
    assertTrue(cachedResult.isSuccess)
    assertEquals("Farmacia del Valle", cachedResult.getOrNull()?.first()?.name)

    // After 24h -> purge removes stale cache entry
    dao.purgeExpiredCache(nowMs + PlacesRepository.CACHE_TTL_MS + 1L)
    assertNull(dao.getCachedSearch(cacheKey))
  }

  @Test
  fun `parse Photon autocomplete GeoJSON features accurately`() {
    val geoJson =
      """
      {
        "type": "FeatureCollection",
        "features": [
          {
            "type": "Feature",
            "geometry": { "type": "Point", "coordinates": [-68.0591, -38.9516] },
            "properties": {
              "osm_type": "N",
              "osm_id": 99441,
              "name": "Parrilla El Neuquino",
              "street": "Olascoaga",
              "housenumber": "410",
              "city": "Neuquén",
              "state": "Neuquén",
              "osm_key": "amenity",
              "osm_value": "restaurant"
            }
          }
        ]
      }
      """.trimIndent()

    val suggestions =
      PlacesRepository.parsePhotonFeatures(geoJson, PatagonianLocalities.NeuquenCapital)
    assertEquals(1, suggestions.size)
    val item = suggestions.first()
    assertEquals("Parrilla El Neuquino", item.primaryText)
    assertEquals("gastronomia", item.business?.categoryId)
    assertEquals("Olascoaga 410", item.business?.address)
    assertNotNull(item.business)
  }

  @Test
  fun `evaluate OSM opening hours patterns for abierto ahora`() {
    val weekdayMorning =
      Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
        set(Calendar.HOUR_OF_DAY, 10)
        set(Calendar.MINUTE, 30)
      }
    val sundayAfternoon =
      Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        set(Calendar.HOUR_OF_DAY, 16)
        set(Calendar.MINUTE, 0)
      }

    assertEquals(true, PlacesRepository.evaluateOsmOpeningHours("24/7", sundayAfternoon))
    assertEquals(
      true,
      PlacesRepository.evaluateOsmOpeningHours("Mo-Fr 09:00-18:00; Sa 09:00-13:00", weekdayMorning),
    )
    assertEquals(
      false,
      PlacesRepository.evaluateOsmOpeningHours("Mo-Fr 09:00-18:00; Sa 09:00-13:00", sundayAfternoon),
    )
    assertNull(PlacesRepository.evaluateOsmOpeningHours(null, weekdayMorning))
    assertNull(PlacesRepository.evaluateOsmOpeningHours("con cita previa", weekdayMorning))
    assertFalse(PlacesRepository.evaluateOsmOpeningHours("off", weekdayMorning) ?: true)
  }

  @Test
  fun `build Overpass bbox query from locality`() {
    val bbox = PatagonianLocalities.Anelo.boundingBox()
    assertTrue(bbox.south < PatagonianLocalities.Anelo.latitude)
    assertTrue(bbox.north > PatagonianLocalities.Anelo.latitude)
    assertTrue(bbox.west < PatagonianLocalities.Anelo.longitude)
    assertTrue(bbox.east > PatagonianLocalities.Anelo.longitude)

    val ql =
      PlacesRepository.buildOverpassBboxQuery(
        query = "",
        bbox = bbox,
        category = DirectoryCategories.findById("ferreterias_construccion"),
        deliveryHint = true,
      )
    assertTrue(ql.contains("""["delivery"="yes"]"""))
    assertTrue(ql.contains(bbox.toOverpassBbox()))
  }

  @Test
  fun `extract Argentine mobile WhatsApp numbers accurately`() {
    assertEquals(
      "+5492995123456",
      PlacesRepository.extractArgentineWhatsAppCandidate("+54 9 299 512-3456", "0299 15-512-3456"),
    )
    assertNull(
      PlacesRepository.extractArgentineWhatsAppCandidate("+54 299 442-1100", "0299 442-1100")
    )
  }

  @Test
  fun `catalogs include Neuquen Alto Valle and Vaca Muerta`() {
    assertTrue(PatagonianLocalities.all.any { it.id == "neuquen_capital" })
    assertEquals(-38.9516, PatagonianLocalities.NeuquenCapital.latitude, 0.001)
    assertEquals(-68.0591, PatagonianLocalities.NeuquenCapital.longitude, 0.001)
    assertTrue(PatagonianLocalities.all.any { it.id == "anelo" && it.zoneLabel == "Vaca Muerta" })
    assertTrue(PatagonianLocalities.all.any { it.id == "cipolletti" && it.zoneLabel == "Alto Valle" })
    assertTrue(DirectoryCategories.items.any { it.id == "vaca_muerta_industria" })

    val osmCat =
      DirectoryCategories.inferFromOsmTags(
        mapOf("industrial" to "oil", "name" to "Base Vaca Muerta"),
        DirectoryCategories.AllCategory,
      )
    assertEquals("vaca_muerta_industria", osmCat.id)
  }
}
