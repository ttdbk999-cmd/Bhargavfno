package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FnoDatabase
import com.example.data.repository.WatchlistRepository
import com.example.domain.model.OptionSide
import com.example.domain.model.StockScanResult
import com.example.ui.scanner.ScannerUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchlistRoomTest {

    private lateinit var db: FnoDatabase
    private lateinit var repo: WatchlistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FnoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WatchlistRepository(db.watchlistDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `watchlist repository persists toggles, custom symbol saves, and clearAll via Room`() = runBlocking {
        val mcx = sampleStock("MCX", changePct = 3.2)
        val pnb = sampleStock("PNB", changePct = -1.4)

        val savedMcx = repo.toggleStock(mcx, OptionSide.CE)
        assertTrue(savedMcx)
        val savedPnb = repo.toggleStock(pnb, OptionSide.PE)
        assertTrue(savedPnb)

        val entries = repo.watchlistFlow.first()
        assertEquals(setOf("MCX", "PNB"), entries.map { it.symbol }.toSet())
        assertEquals(OptionSide.PE.name, entries.first { it.symbol == "PNB" }.preferredSide)

        // Toggle existing removes it
        val stillSaved = repo.toggleStock(mcx, OptionSide.CE)
        assertFalse(stillSaved)
        assertEquals(setOf("PNB"), repo.watchlistSymbolsFlow.first())

        // Direct saveSymbol populates metadata from FnoUniverse and persists
        repo.saveSymbol("dixon", OptionSide.CE)
        assertEquals(setOf("PNB", "DIXON"), repo.watchlistSymbolsFlow.first())

        repo.removeSymbol("PNB")
        assertEquals(setOf("DIXON"), repo.watchlistSymbolsFlow.first())

        repo.clearAll()
        assertTrue(repo.watchlistFlow.first().isEmpty())
    }

    @Test
    fun `ScannerUiState watchlistOnly isolates saved stocks on CE and PE boards`() {
        val mcx = sampleStock("MCX", changePct = 3.1)
        val dixon = sampleStock("DIXON", changePct = -2.2)
        val tcs = sampleStock("TCS", changePct = 1.5)
        val oil = sampleStock("OIL", changePct = -0.8)

        val state = ScannerUiState(
            allStocks = listOf(mcx, dixon, tcs, oil),
            savedWatchlistSymbols = setOf("MCX", "DIXON"),
            watchlistOnly = true,
        )

        assertEquals(2, state.savedWatchlistCount)
        assertEquals(setOf("MCX", "DIXON"), state.ceBoardStocks.map { it.symbol }.toSet())
        assertEquals(setOf("MCX", "DIXON"), state.peBoardStocks.map { it.symbol }.toSet())
        assertFalse(state.ceBoardStocks.any { it.symbol == "TCS" })
        assertFalse(state.peBoardStocks.any { it.symbol == "OIL" })

        val fullState = state.copy(watchlistOnly = false)
        assertTrue(fullState.ceBoardStocks.any { it.symbol == "TCS" })
        assertTrue(fullState.peBoardStocks.any { it.symbol == "OIL" })
    }

    private fun sampleStock(symbol: String, changePct: Double) = StockScanResult(
        symbol = symbol,
        yahooTicker = "$symbol.NS",
        companyName = "$symbol Ltd",
        sector = "F&O",
        badgeColorHex = 0xFF26A69A,
        lastPrice = 1000.0,
        previousClose = 1000.0 / (1.0 + changePct / 100.0),
        change = changePct * 10.0,
        changePercent = changePct,
        sharesVolume = 150_000L,
        latest5mVolume = 12_000L,
        rvol = 2.0,
        rsi1h = 65.0,
        rsi30m = 68.0,
        rsi15m = 70.0,
        rsi5m = 72.0,
        candles5m = emptyList(),
        candles15m = emptyList(),
        candles30m = emptyList(),
        candles1h = emptyList(),
        rsi5mSeries = emptyList(),
        rsi15mSeries = emptyList(),
        rsi30mSeries = emptyList(),
        rsi1hSeries = emptyList(),
    )
}
