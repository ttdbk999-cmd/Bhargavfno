package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyFnoMetricDao {

    @Query("SELECT * FROM daily_fno_metrics WHERE symbol = :symbol ORDER BY tradeDate DESC LIMIT :limit")
    fun observeSymbolHistory(symbol: String, limit: Int = 15): Flow<List<DailyFnoMetricEntity>>

    @Query("SELECT * FROM daily_fno_metrics WHERE symbol = :symbol ORDER BY tradeDate DESC LIMIT :limit")
    suspend fun getSymbolHistoryOnce(symbol: String, limit: Int = 15): List<DailyFnoMetricEntity>

    @Query("SELECT * FROM daily_fno_metrics ORDER BY tradeDate DESC")
    fun observeAllHistory(): Flow<List<DailyFnoMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DailyFnoMetricEntity>)

    @Query(
        """
        DELETE FROM daily_fno_metrics
        WHERE symbol = :symbol
          AND tradeDate NOT IN (
            SELECT tradeDate FROM daily_fno_metrics
            WHERE symbol = :symbol
            ORDER BY tradeDate DESC
            LIMIT :keepDays
          )
        """
    )
    suspend fun pruneSymbolOlderThan(symbol: String, keepDays: Int = 15)
}
