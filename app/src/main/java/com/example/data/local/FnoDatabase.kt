package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyFnoMetricEntity::class, WatchlistStockEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FnoDatabase : RoomDatabase() {
    abstract fun dailyFnoMetricDao(): DailyFnoMetricDao
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watchlist_stocks` (
                        `symbol` TEXT NOT NULL,
                        `companyName` TEXT NOT NULL,
                        `sector` TEXT NOT NULL,
                        `badgeColorHex` INTEGER NOT NULL,
                        `preferredSide` TEXT NOT NULL,
                        `addedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`symbol`)
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var INSTANCE: FnoDatabase? = null

        fun getInstance(context: Context): FnoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FnoDatabase::class.java,
                    "fno_gapboard_cache.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
