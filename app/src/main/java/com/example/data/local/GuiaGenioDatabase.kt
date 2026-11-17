package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    PublishedBusinessEntity::class,
    FavoriteBusinessEntity::class,
    OsmCacheEntity::class,
  ],
  version = 3,
  exportSchema = false,
)
abstract class GuiaGenioDatabase : RoomDatabase() {
  abstract fun businessDao(): BusinessDao

  companion object {
    @Volatile private var INSTANCE: GuiaGenioDatabase? = null

    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `osm_search_cache` (
              `cacheKey` TEXT NOT NULL,
              `payloadJson` TEXT NOT NULL,
              `cachedAtMillis` INTEGER NOT NULL,
              PRIMARY KEY(`cacheKey`)
            )
            """.trimIndent()
          )
        }
      }

    private val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE `published_businesses` ADD COLUMN `ownerUserId` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE `published_businesses` ADD COLUMN `firestoreDocId` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE `published_businesses` ADD COLUMN `createdAtMillis` INTEGER NOT NULL DEFAULT 0"
          )
        }
      }

    fun getInstance(context: Context): GuiaGenioDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                GuiaGenioDatabase::class.java,
                "guia_genio.db",
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
