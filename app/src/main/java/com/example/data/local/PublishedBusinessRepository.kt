package com.example.data.local

import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import kotlinx.coroutines.flow.Flow

class PublishedBusinessRepository(private val dao: BusinessDao) {
  val publishedBusinesses: Flow<List<PublishedBusinessEntity>> = dao.observePublishedBusinesses()
  val approvedPublishedBusinesses: Flow<List<PublishedBusinessEntity>> =
    dao.observePublishedBusinessesByStatus(BusinessStatus.APPROVED.name)
  val favoriteIds: Flow<List<String>> = dao.observeFavoriteIds()
  val favorites: Flow<List<FavoriteBusinessEntity>> = dao.observeFavorites()

  suspend fun findByFirestoreDocId(docId: String): PublishedBusinessEntity? =
    dao.findByFirestoreDocId(docId)

  suspend fun saveBusiness(entity: PublishedBusinessEntity): Long =
    dao.upsertPublishedBusiness(entity.copy(updatedAt = System.currentTimeMillis()))

  suspend fun deleteBusiness(id: Long) = dao.deletePublishedBusiness(id)

  suspend fun toggleOpenStatus(id: Long, isOpen: Boolean) = dao.updateOpenStatus(id, isOpen)

  suspend fun toggleDeliveryStatus(id: Long, hasDelivery: Boolean) =
    dao.updateDeliveryStatus(id, hasDelivery)

  suspend fun updateModerationStatus(id: Long, status: BusinessStatus) =
    dao.updateModerationStatus(id, status.name)

  suspend fun toggleFavorite(business: DirectoryBusiness, currentlyFavorite: Boolean) {
    if (currentlyFavorite) {
      dao.deleteFavorite(business.id)
    } else {
      dao.upsertFavorite(
        FavoriteBusinessEntity(
          businessId = business.id,
          name = business.name,
          categoryName = business.categoryName,
          localityName = business.localityName,
          address = business.address,
          phone = business.phoneNumber,
          whatsapp = business.whatsappNumber,
          latitude = business.latitude,
          longitude = business.longitude,
          status = business.status.name,
        )
      )
    }
  }
}
