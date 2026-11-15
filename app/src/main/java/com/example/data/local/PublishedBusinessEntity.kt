package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.BusinessSource
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.PatagonianLocalities

@Entity(tableName = "published_businesses")
data class PublishedBusinessEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val categoryId: String,
  val localityId: String,
  val address: String,
  val latitude: Double,
  val longitude: Double,
  val phone: String,
  val whatsapp: String,
  val website: String,
  val description: String,
  val isOpenNow: Boolean,
  val hasDelivery: Boolean,
  val updatedAt: Long = System.currentTimeMillis(),
) {
  fun toDirectoryBusiness(distanceMeters: Float? = null): DirectoryBusiness {
    val category = DirectoryCategories.findById(categoryId)
    val locality = PatagonianLocalities.findById(localityId)
    return DirectoryBusiness(
      id = "local_$id",
      name = name,
      categoryId = category.id,
      categoryName = category.name,
      localityName = locality.name,
      address = address,
      latitude = latitude,
      longitude = longitude,
      phoneNumber = phone.ifBlank { null },
      whatsappNumber = whatsapp.ifBlank { null },
      websiteUri = website.ifBlank { null },
      description = description.ifBlank { null },
      isOpenNow = isOpenNow,
      openingHoursRaw = null,
      openingHoursSummary =
        listOf(if (isOpenNow) "Atendiendo ahora" else "Cerrado temporalmente"),
      hasDelivery = hasDelivery,
      source = BusinessSource.USER_PUBLISHED,
      distanceMeters = distanceMeters,
      markerColorArgb = category.markerColorArgb,
    )
  }
}
