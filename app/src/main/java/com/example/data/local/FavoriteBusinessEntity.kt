package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.BusinessStatus

@Entity(tableName = "favorite_businesses")
data class FavoriteBusinessEntity(
  @PrimaryKey val businessId: String,
  val name: String,
  val categoryName: String,
  val localityName: String,
  val address: String,
  val phone: String?,
  val whatsapp: String?,
  val latitude: Double,
  val longitude: Double,
  val savedAt: Long = System.currentTimeMillis(),
  val status: String = BusinessStatus.APPROVED.name,
)
