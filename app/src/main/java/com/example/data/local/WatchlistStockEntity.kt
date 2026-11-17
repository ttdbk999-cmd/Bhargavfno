package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.OptionSide

@Entity(tableName = "watchlist_stocks")
data class WatchlistStockEntity(
    @PrimaryKey
    val symbol: String,
    val companyName: String,
    val sector: String,
    val badgeColorHex: Long,
    val preferredSide: String = OptionSide.CE.name,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
)
