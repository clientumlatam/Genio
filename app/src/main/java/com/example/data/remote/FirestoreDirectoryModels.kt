package com.example.data.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.data.local.PublishedBusinessEntity
import com.example.domain.model.BusinessCategory
import com.example.domain.model.BusinessSource
import com.example.domain.model.CategorySector
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.PatagonianLocalities
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Nombres canónicos de colecciones Firestore para Guía Genio.
 * Se mantienen centralizados para garantizar consistencia entre modelos,
 * repositorios y reglas de seguridad.
 */
object FirestoreCollections {
  const val CATEGORIES = "categories"
  const val BUSINESSES = "businesses"
  const val USERS = "users"
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

  /**
   * Serializa el rubro a un Map apto para Firestore, omitiendo `id` (gestionado por `@DocumentId`)
   * y filtrando nulos para cumplir con validaciones estrictas de reglas.
   */
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
      snapshot.toObject(FirestoreCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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
  val isOpenNow: Boolean? = null,
  val hasDelivery: Boolean = false,
  val source: String = "user_published",
  val markerColorArgb: Long = 0xFF1546A6L,
  val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
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
      distanceMeters = distanceMeters,
      markerColorArgb = resolvedColor,
    )
  }

  /**
   * Serializa el comercio a un Map seguro para escrituras en Firestore.
   * Excluye `id` (`@DocumentId`), usa `FieldValue.serverTimestamp()` y filtra claves nulas.
   */
  fun toMap(ownerUid: String, isCreate: Boolean = true): Map<String, Any> {
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
        "isActive" to isActive,
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
        id = business.id,
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
        isActive = true,
      )

    fun fromPublishedEntity(
      entity: PublishedBusinessEntity,
      userId: String,
    ): FirestoreBusinessListing {
      val category = DirectoryCategories.findById(entity.categoryId)
      val locality = PatagonianLocalities.findById(entity.localityId)
      return FirestoreBusinessListing(
        id = if (entity.id > 0L) "local_${entity.id}" else "",
        userId = userId,
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
        isActive = true,
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
