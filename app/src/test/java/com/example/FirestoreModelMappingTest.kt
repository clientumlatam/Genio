package com.example

import com.example.data.local.PublishedBusinessEntity
import com.example.data.remote.FirestoreBusinessListing
import com.example.data.remote.FirestoreCategory
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.data.remote.FirestoreFavorite
import com.example.data.remote.FirestoreReview
import com.example.data.remote.FirestoreUserProfile
import com.example.domain.model.BusinessSource
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.PatagonianLocalities
import com.example.ui.DirectoryViewModel
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
  fun businessListingRoundTripsWithDomainOwnerAndFiltersNullFields() {
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
        ownerUserId = "uid_123",
        firestoreDocId = "doc_ferreteria_7",
      )

    val listing = FirestoreBusinessListing.fromPublishedEntity(entity, userId = "uid_123")
    val map = listing.toMap(ownerUid = "uid_123", isCreate = true)

    assertFalse("Map must not contain 'id'", map.containsKey("id"))
    assertEquals("uid_123", map["userId"])
    assertEquals("2994421100", map["phoneNumber"])
    assertNull("Blank optional whatsapp must be filtered from Firestore map", map["whatsappNumber"])
    assertNull("Blank optional website must be filtered from Firestore map", map["websiteUri"])
    assertEquals(FieldValue.serverTimestamp(), map["createdAt"])

    val updateMap = FirestoreDirectoryRepository.buildBusinessUpdateMap(listing)
    assertFalse("Update map must not mutate userId", updateMap.containsKey("userId"))
    assertFalse("Update map must not overwrite createdAt", updateMap.containsKey("createdAt"))
    assertEquals(FieldValue.serverTimestamp(), updateMap["updatedAt"])

    val backToDomain = listing.toDirectoryBusiness()
    assertEquals("doc_ferreteria_7", backToDomain.id)
    assertEquals("uid_123", backToDomain.ownerUserId)
    assertEquals("doc_ferreteria_7", backToDomain.firestoreDocId)
    assertEquals("Ferretería Confluencia", backToDomain.name)
    assertEquals(BusinessSource.USER_PUBLISHED, backToDomain.source)
    assertTrue(backToDomain.hasPhone)
    assertFalse(backToDomain.hasWhatsApp)
    assertEquals(-38.9516, backToDomain.latitude, 0.0001)
    assertEquals(-68.0591, backToDomain.longitude, 0.0001)
  }

  @Test
  fun reviewModelMapsToDomainAndClampsRatingSafely() {
    val review =
      FirestoreReview(
        id = "rev_1",
        businessId = "biz_1",
        userId = "uid_reviewer",
        authorName = "Laura Neuquén",
        rating = 5L,
        comment = "Excelente atención y stock completo",
      )

    val map = review.toMap(ownerUid = "uid_reviewer", isCreate = true)
    assertFalse(map.containsKey("id"))
    assertEquals("biz_1", map["businessId"])
    assertEquals("uid_reviewer", map["userId"])
    assertEquals(5L, map["rating"])
    assertEquals(FieldValue.serverTimestamp(), map["createdAt"])

    val domain = review.toDomain()
    assertEquals(5, domain.rating)
    assertEquals("Laura Neuquén", domain.authorName)

    val outOfBounds = review.copy(rating = 9L).toDomain()
    assertEquals(5, outOfBounds.rating)
  }

  @Test
  fun favoriteModelRoundTripsWithDirectoryBusiness() {
    val business =
      DirectoryBusiness(
        id = "biz_neuquen_1",
        name = "Café del Limay",
        categoryId = "gastronomia",
        categoryName = "Gastronomía",
        localityName = "Neuquén Capital",
        address = "Río Negro 120",
        latitude = -38.955,
        longitude = -68.058,
        phoneNumber = "2994410000",
        hasDelivery = true,
        source = BusinessSource.USER_PUBLISHED,
        ownerUserId = "owner_99",
      )

    val fav = FirestoreFavorite.fromDirectoryBusiness(business, userId = "uid_fav")
    val map = fav.toMap(ownerUid = "uid_fav", isCreate = true)
    assertFalse(map.containsKey("id"))
    assertEquals("uid_fav", map["userId"])
    assertEquals("biz_neuquen_1", map["businessId"])
    assertEquals(FieldValue.serverTimestamp(), map["createdAt"])

    val restored = fav.toDirectoryBusiness()
    assertEquals("biz_neuquen_1", restored.id)
    assertEquals("Café del Limay", restored.name)
    assertEquals("owner_99", restored.ownerUserId)
    assertTrue(restored.hasDelivery)
  }

  @Test
  fun mapSearchFiltersByBusinessNameOrCategory() {
    val items =
      listOf(
        DirectoryBusiness(
          id = "1",
          name = "Parrilla Patagónica",
          categoryId = "gastronomia",
          categoryName = "Gastronomía",
          localityName = "Neuquén Capital",
          address = "Av. Argentina 100",
          latitude = -38.95,
          longitude = -68.05,
        ),
        DirectoryBusiness(
          id = "2",
          name = "Repuestos del Valle",
          categoryId = "vaca_muerta_industria",
          categoryName = "Industria y Vaca Muerta",
          localityName = "Añelo",
          address = "Ruta 7 Km 90",
          latitude = -38.35,
          longitude = -68.78,
        ),
      )

    val byName = DirectoryViewModel.filterBusinessesByQuery(items, "parrilla")
    assertEquals(1, byName.size)
    assertEquals("1", byName.first().id)

    val byCategory = DirectoryViewModel.filterBusinessesByQuery(items, "vaca muerta")
    assertEquals(1, byCategory.size)
    assertEquals("2", byCategory.first().id)
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
