package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
  @Query("SELECT * FROM published_businesses ORDER BY updatedAt DESC")
  fun observePublishedBusinesses(): Flow<List<PublishedBusinessEntity>>

  @Query("SELECT * FROM published_businesses WHERE firestoreDocId = :docId LIMIT 1")
  suspend fun findByFirestoreDocId(docId: String): PublishedBusinessEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPublishedBusiness(entity: PublishedBusinessEntity): Long

  @Query("DELETE FROM published_businesses WHERE id = :id")
  suspend fun deletePublishedBusiness(id: Long)

  @Query("UPDATE published_businesses SET isOpenNow = :isOpen, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateOpenStatus(id: Long, isOpen: Boolean, updatedAt: Long = System.currentTimeMillis())

  @Query("UPDATE published_businesses SET hasDelivery = :hasDelivery, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateDeliveryStatus(
    id: Long,
    hasDelivery: Boolean,
    updatedAt: Long = System.currentTimeMillis(),
  )

  @Query("SELECT businessId FROM favorite_businesses")
  fun observeFavoriteIds(): Flow<List<String>>

  @Query("SELECT * FROM favorite_businesses ORDER BY savedAt DESC")
  fun observeFavorites(): Flow<List<FavoriteBusinessEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFavorite(favorite: FavoriteBusinessEntity)

  @Query("DELETE FROM favorite_businesses WHERE businessId = :businessId")
  suspend fun deleteFavorite(businessId: String)

  @Query("SELECT * FROM osm_search_cache WHERE cacheKey = :cacheKey LIMIT 1")
  suspend fun getCachedSearch(cacheKey: String): OsmCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCachedSearch(entity: OsmCacheEntity)

  @Query("DELETE FROM osm_search_cache WHERE cachedAtMillis < :minTimestamp")
  suspend fun purgeExpiredCache(minTimestamp: Long)

  @Query("DELETE FROM osm_search_cache")
  suspend fun clearOsmCache()
}
