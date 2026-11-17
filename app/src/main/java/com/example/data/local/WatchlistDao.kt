package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist_stocks ORDER BY addedAtEpochMs DESC")
    fun observeWatchlist(): Flow<List<WatchlistStockEntity>>

    @Query("SELECT symbol FROM watchlist_stocks ORDER BY addedAtEpochMs DESC")
    fun observeWatchlistSymbols(): Flow<List<String>>

    @Query("SELECT * FROM watchlist_stocks ORDER BY addedAtEpochMs DESC")
    suspend fun getWatchlistOnce(): List<WatchlistStockEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_stocks WHERE symbol = :symbol)")
    suspend fun isSaved(symbol: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistStockEntity)

    @Query("DELETE FROM watchlist_stocks WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("DELETE FROM watchlist_stocks")
    suspend fun clearAll()
}
