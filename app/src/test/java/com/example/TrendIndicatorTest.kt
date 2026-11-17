package com.example

import com.example.domain.model.ChartTimeframe
import com.example.domain.model.OptionSide
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.StockScanResult
import com.example.domain.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendIndicatorTest {

    private val defaultThresholds = ScannerThresholds()

    @Test
    fun `classifies 5of5 CE and RSI above 70 as STRONG_BULLISH`() {
        val strongCe = makeStock(
            changePct = 1.85,
            rvol = 2.2,
            shares = 250_000L,
            rsi1h = 64.0,
            rsi15m = 63.0,
            rsi5m = 68.0,
        )
        assertEquals(TrendDirection.STRONG_BULLISH, strongCe.overallTrendDirection(defaultThresholds))

        val crossCe = makeStock(
            changePct = 0.05,
            rvol = 1.1,
            shares = 30_000L,
            rsi1h = 52.0,
            rsi15m = 54.0,
            rsi5m = 71.2,
            crosses = listOf(
                RsiCrossAlert(
                    id = "T_CE_5m",
                    symbol = "MCX",
                    side = OptionSide.CE,
                    timeframe = ChartTimeframe.M5,
                    rsiValue = 71.2,
                    crossedAtEpochMs = 1_700_000_000_000L,
                    crossedAtTimeIst = "10:15:00",
                    lastPrice = 1000.0,
                    changePercent = 0.05,
                    isActiveExtreme = true,
                )
            ),
        )
        assertEquals(TrendDirection.STRONG_BULLISH, crossCe.overallTrendDirection(defaultThresholds))
    }

    @Test
    fun `classifies 5of5 PE and negative moves as bearish directions`() {
        val strongPe = makeStock(
            changePct = -2.4,
            rvol = 2.0,
            shares = 400_000L,
            rsi1h = 34.0,
            rsi15m = 33.0,
            rsi5m = 28.5,
        )
        assertEquals(TrendDirection.STRONG_BEARISH, strongPe.overallTrendDirection(defaultThresholds))

        val mildBear = makeStock(
            changePct = -0.65,
            rvol = 1.0,
            shares = 40_000L,
            rsi1h = 46.0,
            rsi15m = 45.0,
            rsi5m = 44.0,
        )
        assertEquals(TrendDirection.BEARISH, mildBear.overallTrendDirection(defaultThresholds))

        val mildBull = makeStock(
            changePct = 0.45,
            rvol = 1.0,
            shares = 40_000L,
            rsi1h = 54.0,
            rsi15m = 56.0,
            rsi5m = 57.0,
        )
        assertEquals(TrendDirection.BULLISH, mildBull.overallTrendDirection(defaultThresholds))

        val flat = makeStock(
            changePct = 0.0,
            rvol = 1.0,
            shares = 40_000L,
            rsi1h = 50.0,
            rsi15m = 50.0,
            rsi5m = 50.0,
        )
        assertEquals(TrendDirection.NEUTRAL, flat.overallTrendDirection(defaultThresholds))
    }

    private fun makeStock(
        changePct: Double,
        rvol: Double,
        shares: Long,
        rsi1h: Double,
        rsi15m: Double,
        rsi5m: Double,
        crosses: List<RsiCrossAlert> = emptyList(),
    ) = StockScanResult(
        symbol = "MCX",
        yahooTicker = "MCX.NS",
        companyName = "MCX",
        sector = "Exchanges",
        badgeColorHex = 0xFF546E7A,
        lastPrice = 1000.0,
        previousClose = 1000.0 / (1.0 + changePct / 100.0),
        change = 1000.0 - (1000.0 / (1.0 + changePct / 100.0)),
        changePercent = changePct,
        sharesVolume = shares,
        latest5mVolume = 10_000L,
        rvol = rvol,
        rsi1h = rsi1h,
        rsi30m = 50.0,
        rsi15m = rsi15m,
        rsi5m = rsi5m,
        candles5m = emptyList(),
        candles15m = emptyList(),
        candles30m = emptyList(),
        candles1h = emptyList(),
        rsi5mSeries = emptyList(),
        rsi15mSeries = emptyList(),
        rsi30mSeries = emptyList(),
        rsi1hSeries = emptyList(),
        highPriorityCrosses = crosses,
    )
}
