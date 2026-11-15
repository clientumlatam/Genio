package com.example.domain.model

enum class BusinessSource {
  OPENSTREETMAP,
  USER_PUBLISHED,
}

data class DirectoryBusiness(
  val id: String,
  val name: String,
  val categoryId: String,
  val categoryName: String,
  val localityName: String,
  val address: String,
  val latitude: Double,
  val longitude: Double,
  val phoneNumber: String? = null,
  val whatsappNumber: String? = null,
  val websiteUri: String? = null,
  val description: String? = null,
  val isOpenNow: Boolean? = null,
  val openingHoursRaw: String? = null,
  val openingHoursSummary: List<String> = emptyList(),
  val hasDelivery: Boolean = false,
  val source: BusinessSource = BusinessSource.OPENSTREETMAP,
  val distanceMeters: Float? = null,
  val markerColorArgb: Int = 0xFF1546A6.toInt(),
) {
  val latLng: GeoPointCoord
    get() = GeoPointCoord(latitude, longitude)

  val hasWhatsApp: Boolean
    get() = !whatsappNumber.isNullOrBlank()

  val hasPhone: Boolean
    get() = !phoneNumber.isNullOrBlank()
}
