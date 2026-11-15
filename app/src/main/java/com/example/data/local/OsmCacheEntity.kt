package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "osm_search_cache")
data class OsmCacheEntity(
  @PrimaryKey val cacheKey: String,
  val payloadJson: String,
  val cachedAtMillis: Long,
)
