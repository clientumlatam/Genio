package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
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
  val ownerUserId: String = "",
  val firestoreDocId: String = "",
  val createdAtMillis: Long = System.currentTimeMillis(),
  val status: String = BusinessStatus.PENDING.name,
) {
  val moderationStatus: BusinessStatus
    get() = BusinessStatus.fromString(status, BusinessStatus.PENDING)

  fun toDirectoryBusiness(distanceMeters: Float? = null): DirectoryBusiness {
    val category = DirectoryCategories.findById(categoryId)
    val locality = PatagonianLocalities.findById(localityId)
    val resolvedId = firestoreDocId.ifBlank { "local_$id" }
    return DirectoryBusiness(
      id = resolvedId,
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
      status = moderationStatus,
      distanceMeters = distanceMeters,
      markerColorArgb = category.markerColorArgb,
      ownerUserId = ownerUserId.ifBlank { null },
      firestoreDocId = firestoreDocId.ifBlank { null },
      createdAtMillis = createdAtMillis,
    )
  }
}
