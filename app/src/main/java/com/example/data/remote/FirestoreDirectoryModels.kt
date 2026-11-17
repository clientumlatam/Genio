package com.example.data.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.data.local.PublishedBusinessEntity
import com.example.domain.model.BusinessCategory
import com.example.domain.model.BusinessReview
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
import com.example.domain.model.CategorySector
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.PatagonianLocalities
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Nombres canónicos de colecciones y subcolecciones Firestore para Guía Genio.
 */
object FirestoreCollections {
  const val CATEGORIES = "categories"
  const val BUSINESSES = "businesses"
  const val REVIEWS = "reviews"
  const val USERS = "users"
  const val FAVORITES = "favorites"
}

/**
 * Documento Firestore para representar un rubro o categoría comercial dentro de
 * la colección `categories/{categoryId}`.
 */
@IgnoreExtraProperties
data class FirestoreCategory(
  @DocumentId val id: String = "",
  val name: String = "",
  val shortName: String = "",
  val sector: String = CategorySector.COMERCIOS.name,
  val placesQueryTerm: String = "",
  val description: String = "",
  val accentColorHex: String = "#FF1546A6",
  val markerColorArgb: Long = 0xFF1546A6L,
  val osmFilterClauses: List<String> = emptyList(),
  val displayOrder: Long = 0L,
  @get:PropertyName("isActive")
  @PropertyName("isActive")
  val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDomain(): BusinessCategory {
    val localTemplate = DirectoryCategories.findById(id)
    val parsedSector =
      CategorySector.entries.firstOrNull { it.name.equals(sector, ignoreCase = true) }
        ?: localTemplate.sector
    val resolvedColor = Color(markerColorArgb.toInt())

    return BusinessCategory(
      id = id.ifBlank { localTemplate.id },
      name = name.ifBlank { localTemplate.name },
      shortName = shortName.ifBlank { localTemplate.shortName },
      sector = parsedSector,
      placesQueryTerm = placesQueryTerm.ifBlank { localTemplate.placesQueryTerm },
      description = description.ifBlank { localTemplate.description },
      icon = localTemplate.icon,
      accentColor = if (markerColorArgb != 0L) resolvedColor else localTemplate.accentColor,
      osmFilterClauses = osmFilterClauses.ifEmpty { localTemplate.osmFilterClauses },
    )
  }

  fun toMap(isCreate: Boolean = true): Map<String, Any> {
    val raw =
      mutableMapOf<String, Any?>(
        "name" to name.trim(),
        "shortName" to shortName.trim(),
        "sector" to sector,
        "placesQueryTerm" to placesQueryTerm.trim(),
        "description" to description.trim(),
        "accentColorHex" to accentColorHex,
        "markerColorArgb" to markerColorArgb,
        "osmFilterClauses" to osmFilterClauses,
        "displayOrder" to displayOrder,
        "isActive" to isActive,
        "createdAt" to if (isCreate || createdAt == null) FieldValue.serverTimestamp() else createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  companion object {
    fun fromDomain(
      category: BusinessCategory,
      displayOrder: Int = 0,
    ): FirestoreCategory {
      val argbInt = category.accentColor.toArgb()
      val hex = String.format("#%08X", argbInt)
      return FirestoreCategory(
        id = category.id,
        name = category.name,
        shortName = category.shortName,
        sector = category.sector.name,
        placesQueryTerm = category.placesQueryTerm,
        description = category.description,
        accentColorHex = hex,
        markerColorArgb = argbInt.toLong() and 0xFFFFFFFFL,
        osmFilterClauses = category.osmFilterClauses,
        displayOrder = displayOrder.toLong(),
        isActive = true,
      )
    }

    fun fromSnapshot(snapshot: DocumentSnapshot): FirestoreCategory? =
      snapshot.toObject(
        FirestoreCategory::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
  }
}

/**
 * Documento Firestore para representar un comercio publicado o sincronizado en
 * la colección `businesses/{businessId}`.
 */
@IgnoreExtraProperties
data class FirestoreBusinessListing(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val categoryId: String = "",
  val categoryName: String = "",
  val localityId: String = PatagonianLocalities.NeuquenCapital.id,
  val localityName: String = PatagonianLocalities.NeuquenCapital.name,
  val address: String = "",
  val latitude: Double = PatagonianLocalities.NeuquenCapital.latitude,
  val longitude: Double = PatagonianLocalities.NeuquenCapital.longitude,
  val phoneNumber: String? = null,
  val whatsappNumber: String? = null,
  val websiteUri: String? = null,
  val description: String? = null,
  val openingHoursRaw: String? = null,
  val openingHoursSummary: List<String> = emptyList(),
  @get:PropertyName("isOpenNow")
  @PropertyName("isOpenNow")
  val isOpenNow: Boolean? = null,
  val hasDelivery: Boolean = false,
  val source: String = "user_published",
  val markerColorArgb: Long = 0xFF1546A6L,
  val status: String = BusinessStatus.PENDING.name,
  @get:PropertyName("isActive")
  @PropertyName("isActive")
  val isActive: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedModerationStatus(): BusinessStatus =
    BusinessStatus.fromString(
      status,
      fallback = if (isActive) BusinessStatus.APPROVED else BusinessStatus.PENDING,
    )

  fun toDirectoryBusiness(distanceMeters: Float? = null): DirectoryBusiness {
    val category = DirectoryCategories.findById(categoryId)
    val locality = PatagonianLocalities.findById(localityId)
    val resolvedSource =
      if (source.equals("openstreetmap", ignoreCase = true)) {
        BusinessSource.OPENSTREETMAP
      } else {
        BusinessSource.USER_PUBLISHED
      }
    val resolvedColor =
      if (markerColorArgb != 0L) markerColorArgb.toInt() else category.markerColorArgb
    val resolvedStatus = resolvedModerationStatus()

    return DirectoryBusiness(
      id = id,
      name = name,
      categoryId = categoryId.ifBlank { category.id },
      categoryName = categoryName.ifBlank { category.name },
      localityName = localityName.ifBlank { locality.name },
      address = address,
      latitude = latitude,
      longitude = longitude,
      phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
      whatsappNumber = whatsappNumber?.takeIf { it.isNotBlank() },
      websiteUri = websiteUri?.takeIf { it.isNotBlank() },
      description = description?.takeIf { it.isNotBlank() },
      isOpenNow = isOpenNow,
      openingHoursRaw = openingHoursRaw?.takeIf { it.isNotBlank() },
      openingHoursSummary =
        openingHoursSummary.ifEmpty {
          when (isOpenNow) {
            true -> listOf("Atendiendo ahora")
            false -> listOf("Cerrado temporalmente")
            null -> emptyList()
          }
        },
      hasDelivery = hasDelivery,
      source = resolvedSource,
      status = resolvedStatus,
      distanceMeters = distanceMeters,
      markerColorArgb = resolvedColor,
      ownerUserId = userId.takeIf { it.isNotBlank() },
      firestoreDocId = id.takeIf { it.isNotBlank() },
      createdAtMillis = createdAt?.toDate()?.time,
    )
  }

  fun toMap(ownerUid: String, isCreate: Boolean = true): Map<String, Any> {
    val resolvedStatus =
      if (isCreate) BusinessStatus.PENDING
      else BusinessStatus.fromString(status, BusinessStatus.PENDING)
    val raw =
      mutableMapOf<String, Any?>(
        "userId" to ownerUid,
        "name" to name.trim(),
        "categoryId" to categoryId,
        "categoryName" to categoryName.trim(),
        "localityId" to localityId,
        "localityName" to localityName.trim(),
        "address" to address.trim(),
        "latitude" to latitude,
        "longitude" to longitude,
        "phoneNumber" to phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
        "whatsappNumber" to whatsappNumber?.trim()?.takeIf { it.isNotEmpty() },
        "websiteUri" to websiteUri?.trim()?.takeIf { it.isNotEmpty() },
        "description" to description?.trim()?.takeIf { it.isNotEmpty() },
        "openingHoursRaw" to openingHoursRaw?.trim()?.takeIf { it.isNotEmpty() },
        "openingHoursSummary" to openingHoursSummary,
        "isOpenNow" to isOpenNow,
        "hasDelivery" to hasDelivery,
        "source" to source.lowercase(),
        "markerColorArgb" to markerColorArgb,
        "status" to resolvedStatus.name,
        "isActive" to (resolvedStatus == BusinessStatus.APPROVED),
        "createdAt" to if (isCreate || createdAt == null) FieldValue.serverTimestamp() else createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  companion object {
    fun fromDirectoryBusiness(
      business: DirectoryBusiness,
      userId: String,
      localityId: String = PatagonianLocalities.NeuquenCapital.id,
    ): FirestoreBusinessListing =
      FirestoreBusinessListing(
        id = business.firestoreDocId ?: business.id,
        userId = userId,
        name = business.name,
        categoryId = business.categoryId,
        categoryName = business.categoryName,
        localityId = localityId,
        localityName = business.localityName,
        address = business.address,
        latitude = business.latitude,
        longitude = business.longitude,
        phoneNumber = business.phoneNumber,
        whatsappNumber = business.whatsappNumber,
        websiteUri = business.websiteUri,
        description = business.description,
        openingHoursRaw = business.openingHoursRaw,
        openingHoursSummary = business.openingHoursSummary,
        isOpenNow = business.isOpenNow,
        hasDelivery = business.hasDelivery,
        source =
          if (business.source == BusinessSource.OPENSTREETMAP) {
            "openstreetmap"
          } else {
            "user_published"
          },
        markerColorArgb = business.markerColorArgb.toLong() and 0xFFFFFFFFL,
        status = business.status.name,
        isActive = business.status == BusinessStatus.APPROVED,
      )

    fun fromPublishedEntity(
      entity: PublishedBusinessEntity,
      userId: String,
    ): FirestoreBusinessListing {
      val category = DirectoryCategories.findById(entity.categoryId)
      val locality = PatagonianLocalities.findById(entity.localityId)
      val resolvedStatus = entity.moderationStatus
      return FirestoreBusinessListing(
        id = entity.firestoreDocId.ifBlank { if (entity.id > 0L) "local_${entity.id}" else "" },
        userId = entity.ownerUserId.ifBlank { userId },
        name = entity.name,
        categoryId = category.id,
        categoryName = category.name,
        localityId = locality.id,
        localityName = locality.name,
        address = entity.address,
        latitude = entity.latitude,
        longitude = entity.longitude,
        phoneNumber = entity.phone.ifBlank { null },
        whatsappNumber = entity.whatsapp.ifBlank { null },
        websiteUri = entity.website.ifBlank { null },
        description = entity.description.ifBlank { null },
        openingHoursSummary =
          listOf(if (entity.isOpenNow) "Atendiendo ahora" else "Cerrado temporalmente"),
        isOpenNow = entity.isOpenNow,
        hasDelivery = entity.hasDelivery,
        source = "user_published",
        markerColorArgb = category.markerColorArgb.toLong() and 0xFFFFFFFFL,
        status = resolvedStatus.name,
        isActive = resolvedStatus == BusinessStatus.APPROVED,
      )
    }

    fun fromSnapshot(snapshot: DocumentSnapshot): FirestoreBusinessListing? =
      snapshot.toObject(
        FirestoreBusinessListing::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
  }
}

/**
 * Documento Firestore para reseñas y calificaciones con estrellas en
 * `businesses/{businessId}/reviews/{reviewId}`.
 */
@IgnoreExtraProperties
data class FirestoreReview(
  @DocumentId val id: String = "",
  val businessId: String = "",
  val userId: String = "",
  val authorName: String = "",
  val rating: Long = 5L,
  val comment: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDomain(): BusinessReview =
    BusinessReview(
      id = id,
      businessId = businessId,
      userId = userId,
      authorName = authorName.ifBlank { "Usuario" },
      rating = rating.toInt().coerceIn(1, 5),
      comment = comment,
      createdAtMillis = createdAt?.toDate()?.time,
    )

  fun toMap(ownerUid: String, isCreate: Boolean = true): Map<String, Any> {
    val raw =
      mutableMapOf<String, Any?>(
        "businessId" to businessId.trim(),
        "userId" to ownerUid,
        "authorName" to authorName.trim().ifBlank { "Usuario" }.take(80),
        "rating" to rating.coerceIn(1L, 5L),
        "comment" to comment.trim().take(600),
        "createdAt" to if (isCreate || createdAt == null) FieldValue.serverTimestamp() else createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  companion object {
    fun fromSnapshot(snapshot: DocumentSnapshot): FirestoreReview? =
      snapshot.toObject(
        FirestoreReview::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
  }
}

/**
 * Documento Firestore para comercios favoritos en `users/{userId}/favorites/{businessId}`.
 */
@IgnoreExtraProperties
data class FirestoreFavorite(
  @DocumentId val id: String = "",
  val userId: String = "",
  val businessId: String = "",
  val name: String = "",
  val categoryId: String = "",
  val categoryName: String = "",
  val localityName: String = "",
  val address: String = "",
  val latitude: Double = PatagonianLocalities.NeuquenCapital.latitude,
  val longitude: Double = PatagonianLocalities.NeuquenCapital.longitude,
  val phoneNumber: String? = null,
  val whatsappNumber: String? = null,
  val websiteUri: String? = null,
  val description: String? = null,
  val hasDelivery: Boolean = false,
  val source: String = "openstreetmap",
  val markerColorArgb: Long = 0xFF1546A6L,
  val status: String = BusinessStatus.APPROVED.name,
  val ownerUserId: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDirectoryBusiness(): DirectoryBusiness {
    val category = DirectoryCategories.findById(categoryId)
    val resolvedSource =
      if (source.equals("user_published", ignoreCase = true)) {
        BusinessSource.USER_PUBLISHED
      } else {
        BusinessSource.OPENSTREETMAP
      }
    val resolvedColor =
      if (markerColorArgb != 0L) markerColorArgb.toInt() else category.markerColorArgb
    return DirectoryBusiness(
      id = businessId.ifBlank { id },
      name = name,
      categoryId = categoryId.ifBlank { category.id },
      categoryName = categoryName.ifBlank { category.name },
      localityName = localityName.ifBlank { PatagonianLocalities.NeuquenCapital.name },
      address = address,
      latitude = latitude,
      longitude = longitude,
      phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
      whatsappNumber = whatsappNumber?.takeIf { it.isNotBlank() },
      websiteUri = websiteUri?.takeIf { it.isNotBlank() },
      description = description?.takeIf { it.isNotBlank() },
      hasDelivery = hasDelivery,
      source = resolvedSource,
      status = BusinessStatus.fromString(status, BusinessStatus.APPROVED),
      markerColorArgb = resolvedColor,
      ownerUserId = ownerUserId?.takeIf { it.isNotBlank() },
      firestoreDocId = businessId.takeIf { it.isNotBlank() },
    )
  }

  fun toMap(ownerUid: String, isCreate: Boolean = true): Map<String, Any> {
    val raw =
      mutableMapOf<String, Any?>(
        "userId" to ownerUid,
        "businessId" to businessId.trim(),
        "name" to name.trim(),
        "categoryId" to categoryId,
        "categoryName" to categoryName.trim(),
        "localityName" to localityName.trim(),
        "address" to address.trim(),
        "latitude" to latitude,
        "longitude" to longitude,
        "phoneNumber" to phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
        "whatsappNumber" to whatsappNumber?.trim()?.takeIf { it.isNotEmpty() },
        "websiteUri" to websiteUri?.trim()?.takeIf { it.isNotEmpty() },
        "description" to description?.trim()?.takeIf { it.isNotEmpty() },
        "hasDelivery" to hasDelivery,
        "source" to source.lowercase(),
        "markerColorArgb" to markerColorArgb,
        "status" to BusinessStatus.fromString(status, BusinessStatus.APPROVED).name,
        "ownerUserId" to ownerUserId?.takeIf { it.isNotBlank() },
        "createdAt" to if (isCreate || createdAt == null) FieldValue.serverTimestamp() else createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  companion object {
    fun fromDirectoryBusiness(business: DirectoryBusiness, userId: String): FirestoreFavorite =
      FirestoreFavorite(
        id = business.id,
        userId = userId,
        businessId = business.id,
        name = business.name,
        categoryId = business.categoryId,
        categoryName = business.categoryName,
        localityName = business.localityName,
        address = business.address,
        latitude = business.latitude,
        longitude = business.longitude,
        phoneNumber = business.phoneNumber,
        whatsappNumber = business.whatsappNumber,
        websiteUri = business.websiteUri,
        description = business.description,
        hasDelivery = business.hasDelivery,
        source =
          if (business.source == BusinessSource.USER_PUBLISHED) "user_published" else "openstreetmap",
        markerColorArgb = business.markerColorArgb.toLong() and 0xFFFFFFFFL,
        status = business.status.name,
        ownerUserId = business.ownerUserId,
      )

    fun fromSnapshot(snapshot: DocumentSnapshot): FirestoreFavorite? =
      snapshot.toObject(
        FirestoreFavorite::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
  }
}

/**
 * Documento Firestore para preferencias del usuario autenticado en `users/{userId}`.
 */
@IgnoreExtraProperties
data class FirestoreUserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val preferredLocalityId: String = PatagonianLocalities.NeuquenCapital.id,
  val favoriteBusinessIds: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toMap(ownerUid: String, isCreate: Boolean = true): Map<String, Any> {
    val raw =
      mutableMapOf<String, Any?>(
        "userId" to ownerUid,
        "displayName" to displayName.trim(),
        "preferredLocalityId" to preferredLocalityId,
        "favoriteBusinessIds" to favoriteBusinessIds.take(50),
        "createdAt" to if (isCreate || createdAt == null) FieldValue.serverTimestamp() else createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  companion object {
    fun fromSnapshot(snapshot: DocumentSnapshot): FirestoreUserProfile? =
      snapshot.toObject(
        FirestoreUserProfile::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
  }
}
