package com.example

import com.example.domain.indicator.RsiFrequencyAnalyzer
import com.example.domain.indicator.RsiTriggerEvent
import com.example.domain.indicator.TrendPersistence
import com.example.domain.model.CandleBar
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.OptionSide
import com.example.domain.model.StockScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsiFrequencyAnalyzerTest {

    @Test
    fun `distinguishes sustained multi-trigger momentum from fleeting single spike over 60 minutes`() {
        val baseSec = 1_700_000_000L

        // MCX: 4 triggers across 1m & 5m in the last 60 minutes + 16 minutes of 1m RSI > 70 dwell -> SUSTAINED
        val mcxCandles1m = (0 until 60).map { min ->
            CandleBar(baseSec + min * 60L, 100.0, 101.0, 99.0, 100.5, 1000L)
        }
        val mcxRsi1m = (0 until 60).map { min ->
            when {
                min in 6..10 -> 72.0   // cross 1 at min 6 + dwell
                min in 20..25 -> 73.5  // cross 2 at min 20 + dwell
                min in 42..48 -> 74.0  // cross 3 at min 42 + dwell
                else -> 64.0
            }
        }
        val mcxCandles5m = (0 until 12).map { b ->
            CandleBar(baseSec + b * 300L, 100.0, 101.0, 99.0, 100.5, 5000L)
        }
        val mcxRsi5m = listOf(62.0, 65.0, 68.0, 71.5, 72.0, 69.0, 68.0, 69.5, 70.8, 72.2, 73.0, 71.0) // 2 crosses at index 3 & 8

        val mcx = makeStock(
            symbol = "MCX",
            changePct = 4.5,
            candles1m = mcxCandles1m,
            rsi1mSeries = mcxRsi1m,
            candles5m = mcxCandles5m,
            rsi5mSeries = mcxRsi5m,
        )

        // OIL: only 1 isolated spike > 70 lasting 2 minutes -> FLEETING
        val oilCandles1m = mcxCandles1m
        val oilRsi1m = (0 until 60).map { min ->
            if (min in 50..51) 70.6 else 61.0
        }
        val oil = makeStock(
            symbol = "OIL",
            changePct = 1.2,
            candles1m = oilCandles1m,
            rsi1mSeries = oilRsi1m,
        )

        // INFY: Bearish PE <30 triggers across multiple 5-min blocks -> PE SUSTAINED
        val infyRsi1m = (0 until 60).map { min ->
            when {
                min in 5..12 -> 27.5
                min in 24..31 -> 26.8
                min in 46..52 -> 25.4
                else -> 36.0
            }
        }
        val infy = makeStock(
            symbol = "INFY",
            changePct = -3.8,
            candles1m = mcxCandles1m,
            rsi1mSeries = infyRsi1m,
        )

        val stats = RsiFrequencyAnalyzer.analyze(
            stocks = listOf(oil, infy, mcx),
            nowEpochMs = (baseSec + 59 * 60L) * 1000L,
        )

        assertEquals(3, stats.size)
        // Sorted by highest trigger frequency first
        assertEquals("MCX", stats[0].symbol)
        assertEquals(5, stats[0].totalTriggers60m)
        assertEquals(OptionSide.CE, stats[0].dominantSide)
        assertEquals(TrendPersistence.SUSTAINED, stats[0].persistence)
        assertEquals(18, stats[0].extremeDwellMinutes1m)
        assertEquals(12, stats[0].buckets12.size)

        val infyStat = stats.first { it.symbol == "INFY" }
        assertEquals(OptionSide.PE, infyStat.dominantSide)
        assertEquals(3, infyStat.peTriggers60m)
        assertEquals(TrendPersistence.SUSTAINED, infyStat.persistence)

        val oilStat = stats.first { it.symbol == "OIL" }
        assertEquals(1, oilStat.totalTriggers60m)
        assertEquals(2, oilStat.extremeDwellMinutes1m)
        assertEquals(TrendPersistence.FLEETING, oilStat.persistence)
    }

    @Test
    fun `ignores triggers older than 60 minutes and merges live events without double counting`() {
        val baseSec = 1_700_000_000L
        // 120 minutes of candles; trigger happens only at minute 10 (outside rolling 60m window ending at minute 119)
        val candles1m = (0 until 120).map { min ->
            CandleBar(baseSec + min * 60L, 100.0, 101.0, 99.0, 100.0, 1000L)
        }
        val rsi1m = (0 until 120).map { min ->
            if (min in 10..12) 72.0 else 58.0
        }
        val oldOnlyStock = makeStock(
            symbol = "OLDONLY",
            changePct = 0.5,
            candles1m = candles1m,
            rsi1mSeries = rsi1m,
        )

        val endMs = (baseSec + 119 * 60L) * 1000L
        val emptyStats = RsiFrequencyAnalyzer.analyze(listOf(oldOnlyStock), nowEpochMs = endMs)
        assertTrue(emptyStats.isEmpty())

        // Adding a recent live intra-bar event inside the 60m window counts accurately once
        val liveEv = RsiTriggerEvent(
            symbol = "OLDONLY",
            side = OptionSide.CE,
            timeframe = ChartTimeframe.M1,
            timestampEpochMs = endMs - 120_000L,
            rsiValue = 71.2,
        )
        val withLive = RsiFrequencyAnalyzer.analyze(
            stocks = listOf(oldOnlyStock),
            liveEvents = listOf(liveEv, liveEv), // duplicate in same minute deduplicated
            nowEpochMs = endMs,
        )
        assertEquals(1, withLive.size)
        assertEquals(1, withLive[0].totalTriggers60m)
        assertEquals(TrendPersistence.FLEETING, withLive[0].persistence)
    }

    private fun makeStock(
        symbol: String,
        changePct: Double,
        candles1m: List<CandleBar>,
        rsi1mSeries: List<Double?>,
        candles5m: List<CandleBar> = emptyList(),
        rsi5mSeries: List<Double?> = emptyList(),
    ) = StockScanResult(
        symbol = symbol,
        yahooTicker = "$symbol.NS",
        companyName = "$symbol Ltd",
        sector = "F&O",
        badgeColorHex = 0xFF26A69A,
        lastPrice = 1000.0,
        previousClose = 990.0,
        change = 10.0,
        changePercent = changePct,
        sharesVolume = 200_000L,
        latest5mVolume = 15_000L,
        rvol = 2.0,
        rsi1h = 65.0,
        rsi30m = 66.0,
        rsi15m = 68.0,
        rsi5m = rsi5mSeries.filterNotNull().lastOrNull() ?: 64.0,
        rsi1m = rsi1mSeries.filterNotNull().lastOrNull(),
        candles1m = candles1m,
        candles5m = candles5m,
        candles15m = emptyList(),
        candles30m = emptyList(),
        candles1h = emptyList(),
        rsi1mSeries = rsi1mSeries,
        rsi5mSeries = rsi5mSeries,
        rsi15mSeries = emptyList(),
        rsi30mSeries = emptyList(),
        rsi1hSeries = emptyList(),
    )
}
