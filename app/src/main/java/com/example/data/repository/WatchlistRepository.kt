package com.example.data.repository

import com.example.data.local.WatchlistDao
import com.example.data.local.WatchlistStockEntity
import com.example.data.universe.FnoUniverse
import com.example.domain.model.OptionSide
import com.example.domain.model.StockScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchlistRepository(
    private val watchlistDao: WatchlistDao,
) {
    val watchlistFlow: Flow<List<WatchlistStockEntity>> = watchlistDao.observeWatchlist()

    val watchlistSymbolsFlow: Flow<Set<String>> = watchlistDao.observeWatchlistSymbols()
        .map { symbols -> symbols.map { it.uppercase() }.toSet() }

    suspend fun getWatchlistOnce(): List<WatchlistStockEntity> = watchlistDao.getWatchlistOnce()

    /**
     * Toggles [stock] inside Room watchlist.
     * @return true if added/saved, false if removed.
     */
    suspend fun toggleStock(stock: StockScanResult, preferredSide: OptionSide): Boolean {
        val cleanSymbol = stock.symbol.trim().uppercase()
        return if (watchlistDao.isSaved(cleanSymbol)) {
            watchlistDao.deleteBySymbol(cleanSymbol)
            false
        } else {
            watchlistDao.upsert(
                WatchlistStockEntity(
                    symbol = cleanSymbol,
                    companyName = stock.companyName,
                    sector = stock.sector,
                    badgeColorHex = stock.badgeColorHex,
                    preferredSide = preferredSide.name,
                )
            )
            true
        }
    }

    suspend fun saveSymbol(
        symbol: String,
        preferredSide: OptionSide = OptionSide.CE,
    ) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty()) return
        val meta = FnoUniverse.resolveMeta(clean)
        watchlistDao.upsert(
            WatchlistStockEntity(
                symbol = clean,
                companyName = meta.companyName,
                sector = meta.sector,
                badgeColorHex = meta.badgeColorHex,
                preferredSide = preferredSide.name,
            )
        )
    }

    suspend fun removeSymbol(symbol: String) {
        watchlistDao.deleteBySymbol(symbol.trim().uppercase())
    }

    suspend fun clearAll() {
        watchlistDao.clearAll()
    }
}
