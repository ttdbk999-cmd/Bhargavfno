package com.example

import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.CandleBar
import com.example.domain.model.OptionSide
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.StockScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TechnicalIndicatorsTest {

    private val istZone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `wilder rsi returns high value on sustained uptrend`() {
        val closes = (1..30).map { 100.0 + it * 1.5 }
        val series = TechnicalIndicators.calculateRsiSeries(closes, period = 14)
        assertEquals(30, series.size)
        val latest = series.last()
        assertNotNull(latest)
        assertTrue("Expected RSI > 90 on monotonic uptrend, got $latest", latest!! > 90.0)
    }

    @Test
    fun `wilder rsi matches classic textbook smoothing value`() {
        // 15 alternating up/down bars where gains exceed losses
        val closes = listOf(
            44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42,
            45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28, 46.00,
        )
        val series = TechnicalIndicators.calculateRsiSeries(closes, period = 14)
        val rsiAt14 = series[14]!!
        assertEquals(70.46, rsiAt14, 0.5)
    }

    @Test
    fun `nse resampler aligns 15m and 60m buckets to 09_15 IST`() {
        val t1 = LocalDateTime.of(2026, 11, 13, 9, 15).atZone(istZone).toEpochSecond()
        val t2 = LocalDateTime.of(2026, 11, 13, 9, 20).atZone(istZone).toEpochSecond()
        val t3 = LocalDateTime.of(2026, 11, 13, 9, 25).atZone(istZone).toEpochSecond()
        val t4 = LocalDateTime.of(2026, 11, 13, 9, 30).atZone(istZone).toEpochSecond()

        val bars5m = listOf(
            CandleBar(t1, 100.0, 102.0, 99.0, 101.0, 10_000),
            CandleBar(t2, 101.0, 104.0, 100.0, 103.0, 15_000),
            CandleBar(t3, 103.0, 105.0, 102.0, 104.0, 20_000),
            CandleBar(t4, 104.0, 106.0, 103.0, 105.5, 12_000),
        )

        val bars15m = TechnicalIndicators.resampleNseCandles(bars5m, 15)
        assertEquals(2, bars15m.size)
        assertEquals(45_000L, bars15m[0].volume)
        assertEquals(100.0, bars15m[0].open, 0.001)
        assertEquals(105.0, bars15m[0].high, 0.001)
        assertEquals(99.0, bars15m[0].low, 0.001)
        assertEquals(104.0, bars15m[0].close, 0.001)

        val bars60m = TechnicalIndicators.resampleNseCandles(bars5m, 60)
        assertEquals(1, bars60m.size)
        assertEquals(57_000L, bars60m[0].volume)
    }

    @Test
    fun `session rvol compares cumulative volume against prior day same time`() {
        val day1t1 = LocalDateTime.of(2026, 11, 12, 9, 15).atZone(istZone).toEpochSecond()
        val day1t2 = LocalDateTime.of(2026, 11, 12, 9, 20).atZone(istZone).toEpochSecond()
        val day2t1 = LocalDateTime.of(2026, 11, 13, 9, 15).atZone(istZone).toEpochSecond()
        val day2t2 = LocalDateTime.of(2026, 11, 13, 9, 20).atZone(istZone).toEpochSecond()

        val bars = listOf(
            CandleBar(day1t1, 100.0, 101.0, 99.0, 100.5, 20_000),
            CandleBar(day1t2, 100.5, 102.0, 100.0, 101.5, 30_000),
            CandleBar(day2t1, 102.0, 104.0, 101.5, 103.5, 50_000),
            CandleBar(day2t2, 103.5, 106.0, 103.0, 105.5, 50_000),
        )

        val rvol = TechnicalIndicators.calculateSessionRvol(bars, reportedSessionVolume = 100_000L)
        assertEquals(2.0, rvol, 0.01)
    }

    @Test
    fun `ce and pe criteria evaluation respects all 5 thresholds`() {
        val thresholds = ScannerThresholds(
            minRvol = 1.5,
            minShares = 50_000L,
            minRsi1h = 60.0,
            minRsi15m = 60.0,
            minRsi5m = 65.0,
        )
        val bullishStock = StockScanResult(
            symbol = "MCX",
            yahooTicker = "MCX.NS",
            companyName = "MCX",
            sector = "Exchanges",
            badgeColorHex = 0xFF546E7A,
            lastPrice = 3676.7,
            previousClose = 3624.7,
            change = 52.0,
            changePercent = 1.43,
            sharesVolume = 303_040L,
            latest5mVolume = 18_500L,
            rvol = 2.15,
            rsi1h = 64.2,
            rsi30m = 63.1,
            rsi15m = 62.8,
            rsi5m = 69.5,
            candles5m = emptyList(),
            candles15m = emptyList(),
            candles30m = emptyList(),
            candles1h = emptyList(),
            rsi5mSeries = emptyList(),
            rsi15mSeries = emptyList(),
            rsi30mSeries = emptyList(),
            rsi1hSeries = emptyList(),
        )
        val ceEval = bullishStock.evaluateCe(thresholds)
        assertTrue(ceEval.allPassed)
        assertEquals(5, ceEval.passedCount)
        assertFalse(bullishStock.evaluatePe(thresholds).allPassed)
        assertEquals("MCX 3700 CE", bullishStock.suggestedStrikeContract(OptionSide.CE))
    }
}
