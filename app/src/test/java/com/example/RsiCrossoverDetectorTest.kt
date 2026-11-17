package com.example

import com.example.domain.indicator.RsiCrossoverDetector
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.OptionSide
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.StockScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class RsiCrossoverDetectorTest {

    private fun makeStock(
        symbol: String,
        rsi1m: List<Double?> = listOf(55.0, 56.0),
        rsi5m: List<Double?> = listOf(55.0, 58.0),
        rsi15m: List<Double?> = listOf(55.0, 56.0),
        rsi30m: List<Double?> = listOf(55.0, 56.0),
        rsi1h: List<Double?> = listOf(55.0, 56.0),
    ): StockScanResult = StockScanResult(
        symbol = symbol,
        yahooTicker = "$symbol.NS",
        companyName = symbol,
        sector = "F&O",
        badgeColorHex = 0xFF1E88E5,
        lastPrice = 1000.0,
        previousClose = 980.0,
        change = 20.0,
        changePercent = 2.04,
        sharesVolume = 120_000L,
        latest5mVolume = 12_000L,
        rvol = 2.1,
        rsi1h = rsi1h.lastOrNull(),
        rsi30m = rsi30m.lastOrNull(),
        rsi15m = rsi15m.lastOrNull(),
        rsi5m = rsi5m.lastOrNull(),
        candles5m = emptyList(),
        candles15m = emptyList(),
        candles30m = emptyList(),
        candles1h = emptyList(),
        rsi5mSeries = rsi5m,
        rsi15mSeries = rsi15m,
        rsi30mSeries = rsi30m,
        rsi1hSeries = rsi1h,
        rsi1m = rsi1m.lastOrNull(),
        candles1m = emptyList(),
        rsi1mSeries = rsi1m,
    )

    @Test
    fun `detects 30m RSI crossing above 70 as CE high priority with timestamp`() {
        val prevMap = ConcurrentHashMap<String, Double>()
        val alertsMap = ConcurrentHashMap<String, RsiCrossAlert>()

        // Initial scan at 68.5
        val scan1 = makeStock("POWERINDIA", rsi30m = listOf(66.0, 68.5))
        val out1 = RsiCrossoverDetector.evaluateBatch(listOf(scan1), prevMap, alertsMap, nowMs = 1_700_000_000_000L)
        assertFalse(out1.enrichedStocks.first().isHighPriority(OptionSide.CE))

        // Next 10s tick crosses above 70 -> 71.2
        val scan2 = makeStock("POWERINDIA", rsi30m = listOf(68.5, 71.2))
        val out2 = RsiCrossoverDetector.evaluateBatch(listOf(scan2), prevMap, alertsMap, nowMs = 1_700_000_010_000L)
        val resultStock = out2.enrichedStocks.first()
        assertTrue(resultStock.isHighPriority(OptionSide.CE))
        assertEquals(1, out2.watchAlertsFeed.size)
        val alert = out2.watchAlertsFeed.first()
        assertEquals(OptionSide.CE, alert.side)
        assertEquals(ChartTimeframe.M30, alert.timeframe)
        assertEquals(71.2, alert.rsiValue, 0.01)
        assertTrue(alert.crossedAtTimeIst.isNotBlank())
    }

    @Test
    fun `detects 15m RSI crossing below 30 as PE high priority`() {
        val prevMap = ConcurrentHashMap<String, Double>()
        val alertsMap = ConcurrentHashMap<String, RsiCrossAlert>()

        // Initial bar transition directly 31.2 -> 28.4
        val stock = makeStock("NMDC", rsi15m = listOf(31.2, 28.4))
        val out = RsiCrossoverDetector.evaluateBatch(listOf(stock), prevMap, alertsMap, nowMs = 1_700_000_000_000L)
        val resultStock = out.enrichedStocks.first()
        assertTrue(resultStock.isHighPriority(OptionSide.PE))
        assertFalse(resultStock.isHighPriority(OptionSide.CE))
        assertEquals(ChartTimeframe.M15, out.watchAlertsFeed.first().timeframe)
        assertEquals(OptionSide.PE, out.watchAlertsFeed.first().side)
        assertEquals(1, out.newlyTriggeredAlerts.size)

        // Staying below 30 on the next 10s scan should keep PE high priority, without re-triggering sound alert
        val stockStillLow = makeStock("NMDC", rsi15m = listOf(28.4, 27.9))
        val outNext = RsiCrossoverDetector.evaluateBatch(listOf(stockStillLow), prevMap, alertsMap, nowMs = 1_700_000_010_000L)
        assertTrue(outNext.enrichedStocks.first().isHighPriority(OptionSide.PE))
        assertTrue(outNext.newlyTriggeredAlerts.isEmpty())
    }

    @Test
    fun `detects 1m RSI crossing above 70 CE and below 30 PE as M1 high priority`() {
        val prevMap = ConcurrentHashMap<String, Double>()
        val alertsMap = ConcurrentHashMap<String, RsiCrossAlert>()

        val ce1m = makeStock("HAL", rsi1m = listOf(67.4, 71.8), rsi5m = listOf(66.0, 68.0))
        val pe1m = makeStock("OIL", rsi1m = listOf(31.5, 28.2), rsi5m = listOf(32.0, 29.0))

        val out = RsiCrossoverDetector.evaluateBatch(listOf(ce1m, pe1m), prevMap, alertsMap, nowMs = 1_700_000_000_000L)

        val halResult = out.enrichedStocks.first { it.symbol == "HAL" }
        assertTrue(halResult.isHighPriority(OptionSide.CE))
        assertTrue(halResult.activeCrossesFor(OptionSide.CE).any { it.timeframe == ChartTimeframe.M1 })

        val oilResult = out.enrichedStocks.first { it.symbol == "OIL" }
        assertTrue(oilResult.isHighPriority(OptionSide.PE))
        assertTrue(oilResult.activeCrossesFor(OptionSide.PE).any { it.timeframe == ChartTimeframe.M1 })
        assertEquals(2, out.newlyTriggeredAlerts.count { it.timeframe == ChartTimeframe.M1 })
    }

    @Test
    fun `require 1m alignment gates 5m signals until 1m RSI crosses 70 or 30`() {
        val thresholds = ScannerThresholds(
            minRvol = 1.5,
            minShares = 50_000L,
            minRsi1h = 60.0,
            minRsi15m = 60.0,
            minRsi5m = 65.0,
            minRsi1m = 70.0,
            require1mAlignment = true,
            mirrorPeRsi = true,
        )

        // 5m, 15m, 1h, RVOL, Shares all pass CE — but 1m RSI is only 58.0 (< 70)
        val unalignedCe = makeStock(
            symbol = "BEL",
            rsi1m = listOf(56.0, 58.0),
            rsi5m = listOf(68.0, 72.0),
            rsi15m = listOf(63.0, 65.0),
            rsi1h = listOf(62.0, 64.0),
        )
        val unalignedEval = unalignedCe.evaluateCe(thresholds)
        assertEquals(5, unalignedEval.passedCount)
        assertFalse(unalignedEval.rsi1mPass)
        assertFalse(unalignedEval.allPassed)
        assertFalse(unalignedCe.is1mAnd5mAligned(OptionSide.CE, thresholds))

        // Now 1m crosses > 70 -> 71.5
        val alignedCe = unalignedCe.copy(rsi1m = 71.5, rsi1mSeries = listOf(69.0, 71.5))
        val alignedEval = alignedCe.evaluateCe(thresholds)
        assertTrue(alignedEval.rsi1mPass)
        assertTrue(alignedEval.allPassed)
        assertTrue(alignedCe.is1mAnd5mAligned(OptionSide.CE, thresholds))

        // PE case: 5m, 15m, 1h < 35/40 — 1m RSI is 42.0 (unaligned), then drops to 27.5 (< 30 aligned)
        val unalignedPe = makeStock(
            symbol = "OIL",
            rsi1m = listOf(44.0, 42.0),
            rsi5m = listOf(31.0, 28.0),
            rsi15m = listOf(36.0, 34.0),
            rsi1h = listOf(38.0, 36.0),
        )
        assertFalse(unalignedPe.evaluatePe(thresholds).allPassed)
        assertFalse(unalignedPe.is1mAnd5mAligned(OptionSide.PE, thresholds))

        val alignedPe = unalignedPe.copy(rsi1m = 27.5, rsi1mSeries = listOf(30.5, 27.5))
        assertTrue(alignedPe.evaluatePe(thresholds).allPassed)
        assertTrue(alignedPe.is1mAnd5mAligned(OptionSide.PE, thresholds))
    }

    @Test
    fun `high priced stocks pass liquidity rule via turnover without waiting 20 mins for 50k raw shares`() {
        val thresholds = ScannerThresholds(minShares = 50_000L, require1mAlignment = true)
        // DIXON at ₹13,145 with 15,000 shares = ₹19.7 Cr turnover (> ₹2.5 Cr equivalent)
        val dixonEarly = makeStock(
            symbol = "DIXON",
            rsi1m = listOf(69.0, 72.5),
            rsi5m = listOf(68.0, 73.1),
            rsi15m = listOf(61.0, 63.0),
            rsi1h = listOf(60.5, 62.0),
        ).copy(lastPrice = 13_145.0, sharesVolume = 15_000L)

        assertTrue(dixonEarly.passesLiquidityRule(thresholds))
        assertTrue(dixonEarly.evaluateCe(thresholds).allPassed)

        // Low-priced ₹100 stock with 15,000 shares = ₹15L (< ₹2.5 Cr and < 50k shares) still fails
        val pennyLowVol = dixonEarly.copy(lastPrice = 100.0, sharesVolume = 15_000L)
        assertFalse(pennyLowVol.passesLiquidityRule(thresholds))
        assertFalse(pennyLowVol.evaluateCe(thresholds).allPassed)
    }

    @Test
    fun `initial scan catches recent 3-bar 70 crossover even if 1 extra bar elapsed`() {
        val prevMap = ConcurrentHashMap<String, Double>()
        val alertsMap = ConcurrentHashMap<String, RsiCrossAlert>()

        // Crossed > 70 one bar ago (68.0 -> 71.2 -> 73.0) before initial poll
        val dixon = makeStock("DIXON", rsi5m = listOf(68.0, 71.2, 73.0))
        val out = RsiCrossoverDetector.evaluateBatch(listOf(dixon), prevMap, alertsMap, nowMs = 1_700_000_000_000L)
        assertTrue(out.enrichedStocks.first().isHighPriority(OptionSide.CE))
        assertTrue(out.watchAlertsFeed.any { it.symbol == "DIXON" && it.timeframe == ChartTimeframe.M5 })
    }
}
