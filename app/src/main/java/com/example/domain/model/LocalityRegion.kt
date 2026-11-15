package com.example.domain.model

import kotlin.math.cos
import org.osmdroid.util.GeoPoint

data class GeoPointCoord(
  val latitude: Double,
  val longitude: Double,
) {
  fun toOsmGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}

data class OsmBoundingBox(
  val south: Double,
  val west: Double,
  val north: Double,
  val east: Double,
) {
  fun toOverpassBbox(): String = "$south,$west,$north,$east"
}

data class LocalityRegion(
  val id: String,
  val name: String,
  val province: String,
  val zoneLabel: String,
  val latitude: Double,
  val longitude: Double,
  val radiusMeters: Double = 8500.0,
  val isUserLocation: Boolean = false,
) {
  val latLng: GeoPointCoord
    get() = GeoPointCoord(latitude, longitude)

  fun boundingBox(centerOverride: GeoPointCoord? = null): OsmBoundingBox {
    val center = centerOverride ?: latLng
    val effectiveRadius = radiusMeters.coerceIn(3500.0, 9500.0)
    val latDelta = effectiveRadius / 111_320.0
    val cosLat = cos(Math.toRadians(center.latitude)).coerceAtLeast(0.2)
    val lonDelta = effectiveRadius / (111_320.0 * cosLat)
    return OsmBoundingBox(
      south = center.latitude - latDelta,
      west = center.longitude - lonDelta,
      north = center.latitude + latDelta,
      east = center.longitude + lonDelta,
    )
  }
}

object PatagonianLocalities {
  val NeuquenCapital =
    LocalityRegion(
      id = "neuquen_capital",
      name = "Neuquén Capital",
      province = "Neuquén",
      zoneLabel = "Confluencia",
      latitude = -38.9516,
      longitude = -68.0591,
      radiusMeters = 11000.0,
    )

  val Cipolletti =
    LocalityRegion(
      id = "cipolletti",
      name = "Cipolletti",
      province = "Río Negro",
      zoneLabel = "Alto Valle",
      latitude = -38.9339,
      longitude = -67.9903,
      radiusMeters = 8500.0,
    )

  val Plottier =
    LocalityRegion(
      id = "plottier",
      name = "Plottier",
      province = "Neuquén",
      zoneLabel = "Confluencia",
      latitude = -38.9667,
      longitude = -68.2333,
      radiusMeters = 7500.0,
    )

  val Centenario =
    LocalityRegion(
      id = "centenario",
      name = "Centenario",
      province = "Neuquén",
      zoneLabel = "Confluencia",
      latitude = -38.8294,
      longitude = -68.1322,
      radiusMeters = 7500.0,
    )

  val Anelo =
    LocalityRegion(
      id = "anelo",
      name = "Añelo",
      province = "Neuquén",
      zoneLabel = "Vaca Muerta",
      latitude = -38.3533,
      longitude = -68.7867,
      radiusMeters = 9000.0,
    )

  val RinconDeLosSauces =
    LocalityRegion(
      id = "rincon_de_los_sauces",
      name = "Rincón de los Sauces",
      province = "Neuquén",
      zoneLabel = "Vaca Muerta",
      latitude = -37.3894,
      longitude = -68.9247,
      radiusMeters = 8000.0,
    )

  val GeneralRoca =
    LocalityRegion(
      id = "general_roca",
      name = "General Roca",
      province = "Río Negro",
      zoneLabel = "Alto Valle",
      latitude = -39.0333,
      longitude = -67.5833,
      radiusMeters = 9500.0,
    )

  val Allen =
    LocalityRegion(
      id = "allen",
      name = "Allen",
      province = "Río Negro",
      zoneLabel = "Alto Valle",
      latitude = -38.9775,
      longitude = -67.8272,
      radiusMeters = 7000.0,
    )

  val CincoSaltos =
    LocalityRegion(
      id = "cinco_saltos",
      name = "Cinco Saltos",
      province = "Río Negro",
      zoneLabel = "Alto Valle",
      latitude = -38.8225,
      longitude = -68.0628,
      radiusMeters = 7000.0,
    )

  val CutralCo =
    LocalityRegion(
      id = "cutral_co",
      name = "Cutral Co",
      province = "Neuquén",
      zoneLabel = "Comarca Petrolera",
      latitude = -38.9344,
      longitude = -69.2303,
      radiusMeters = 7500.0,
    )

  val PlazaHuincul =
    LocalityRegion(
      id = "plaza_huincul",
      name = "Plaza Huincul",
      province = "Neuquén",
      zoneLabel = "Comarca Petrolera",
      latitude = -38.9333,
      longitude = -69.1889,
      radiusMeters = 7000.0,
    )

  val VillaRegina =
    LocalityRegion(
      id = "villa_regina",
      name = "Villa Regina",
      province = "Río Negro",
      zoneLabel = "Alto Valle",
      latitude = -39.1000,
      longitude = -67.0667,
      radiusMeters = 7500.0,
    )

  val Zapala =
    LocalityRegion(
      id = "zapala",
      name = "Zapala",
      province = "Neuquén",
      zoneLabel = "Centro Neuquino",
      latitude = -38.9022,
      longitude = -70.0650,
      radiusMeters = 7500.0,
    )

  val SanMartinDeLosAndes =
    LocalityRegion(
      id = "san_martin",
      name = "San Martín de los Andes",
      province = "Neuquén",
      zoneLabel = "Cordillera",
      latitude = -40.1579,
      longitude = -71.3534,
      radiusMeters = 8500.0,
    )

  val all: List<LocalityRegion> =
    listOf(
      NeuquenCapital,
      Cipolletti,
      Plottier,
      Centenario,
      Anelo,
      RinconDeLosSauces,
      GeneralRoca,
      Allen,
      CincoSaltos,
      CutralCo,
      PlazaHuincul,
      VillaRegina,
      Zapala,
      SanMartinDeLosAndes,
    )

  fun findById(id: String): LocalityRegion = all.firstOrNull { it.id == id } ?: NeuquenCapital
}
