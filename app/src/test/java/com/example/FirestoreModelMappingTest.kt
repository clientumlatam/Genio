package com.example

import com.example.data.local.PublishedBusinessEntity
import com.example.data.remote.FirestoreBusinessListing
import com.example.data.remote.FirestoreCategory
import com.example.data.remote.FirestoreUserProfile
import com.example.domain.model.BusinessSource
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.PatagonianLocalities
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreModelMappingTest {

  @Test
  fun categoryModelMapsToDomainAndFirestoreMapSafely() {
    val original = DirectoryCategories.findById("vaca_muerta_industria")
    val firestoreCategory = FirestoreCategory.fromDomain(original, displayOrder = 1)
    val map = firestoreCategory.toMap(isCreate = true)

    assertFalse("Map must not include @DocumentId field 'id'", map.containsKey("id"))
    assertEquals(original.name, map["name"])
    assertEquals(original.sector.name, map["sector"])
    assertEquals(FieldValue.serverTimestamp(), map["createdAt"])
    assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])

    val restored = firestoreCategory.toDomain()
    assertEquals(original.id, restored.id)
    assertEquals(original.name, restored.name)
    assertEquals(original.sector, restored.sector)
    assertEquals(original.markerColorArgb, restored.markerColorArgb)
  }

  @Test
  fun businessListingRoundTripsWithDomainAndFiltersNullFields() {
    val entity =
      PublishedBusinessEntity(
        id = 7L,
        name = "Ferretería Confluencia",
        categoryId = "ferreterias_construccion",
        localityId = PatagonianLocalities.NeuquenCapital.id,
        address = "Av. Argentina 850",
        latitude = -38.9516,
        longitude = -68.0591,
        phone = "2994421100",
        whatsapp = "",
        website = "",
        description = "Insumos industriales",
        isOpenNow = true,
        hasDelivery = true,
      )

    val listing = FirestoreBusinessListing.fromPublishedEntity(entity, userId = "uid_123")
    val map = listing.toMap(ownerUid = "uid_123", isCreate = true)

    assertFalse("Map must not contain 'id'", map.containsKey("id"))
    assertEquals("uid_123", map["userId"])
    assertEquals("2994421100", map["phoneNumber"])
    assertNull("Blank optional whatsapp must be filtered from Firestore map", map["whatsappNumber"])
    assertNull("Blank optional website must be filtered from Firestore map", map["websiteUri"])
    assertEquals(FieldValue.serverTimestamp(), map["createdAt"])

    val backToDomain = listing.toDirectoryBusiness()
    assertEquals("local_7", backToDomain.id)
    assertEquals("Ferretería Confluencia", backToDomain.name)
    assertEquals(BusinessSource.USER_PUBLISHED, backToDomain.source)
    assertTrue(backToDomain.hasPhone)
    assertFalse(backToDomain.hasWhatsApp)
    assertEquals(-38.9516, backToDomain.latitude, 0.0001)
    assertEquals(-68.0591, backToDomain.longitude, 0.0001)
  }

  @Test
  fun userProfileLimitsFavoritesAndExcludesIdKey() {
    val favorites = (1..65).map { "biz_$it" }
    val profile =
      FirestoreUserProfile(
        id = "uid_abc",
        userId = "uid_abc",
        displayName = "Usuario Neuquén",
        preferredLocalityId = PatagonianLocalities.NeuquenCapital.id,
        favoriteBusinessIds = favorites,
      )

    val map = profile.toMap(ownerUid = "uid_abc", isCreate = true)
    assertFalse(map.containsKey("id"))
    assertEquals("uid_abc", map["userId"])
    @Suppress("UNCHECKED_CAST")
    val storedFavorites = map["favoriteBusinessIds"] as List<String>
    assertEquals(50, storedFavorites.size)
  }
}
