package com.example

import com.example.domain.indicator.PivotAlertDetector
import com.example.domain.indicator.PositionalAnalyzer
import com.example.domain.indicator.PositionalStage
import com.example.domain.indicator.PositionSignalEvaluator
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.AlertCategory
import com.example.domain.model.AtmOptionBundle
import com.example.domain.model.CandleBar
import com.example.domain.model.DailyPositionSnapshot
import com.example.domain.model.FnoBuildupQuadrant
import com.example.domain.model.OptionSide
import com.example.domain.model.OptionStrikeQuote
import com.example.domain.model.PivotLevels
import com.example.domain.model.StockScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionalAnalyzerTest {

    @Test
    fun `early bullish long buildup with rising delivery and PP-to-R2 pivot ranks as EARLY_IGNITION with option target`() {
        val pivots = PivotLevels(
            prevHigh = 1020.0,
            prevLow = 980.0,
            prevClose = 1000.0,
            pp = 1000.0,
            r1 = 1020.0,
            r2 = 1040.0,
            r3 = 1080.0,
            s1 = 980.0,
            s2 = 960.0,
            s3 = 920.0,
        )
        val history = listOf(
            DailyPositionSnapshot("2026-11-16", 1000.0, 0.5, 2_000_000L, 10_000_000L, 100_000L, 1.0, 700_000L, 35.0),
            DailyPositionSnapshot("2026-11-15", 995.0, 0.2, 1_800_000L, 9_900_000L, 50_000L, 0.5, 576_000L, 32.0),
        )
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 2.6,
            liveOi = 11_200_000L,
            liveOiChange = 1_200_000L,
            liveOiChangePct = 12.0,
            livePcr = 1.24,
            liveDeliveryQty = 1_100_000L,
            liveDeliveryPct = 44.5,
            rawHistoryDesc = history,
            livePcrOiChangePct = 6.5,
        )

        val ce1020 = OptionStrikeQuote("EARLYBULL", OptionSide.CE, 1020.0, "27-Nov-2026", 28.0, 5.0, 21.0, 500_000L, 50_000L)
        val ce1040 = OptionStrikeQuote("EARLYBULL", OptionSide.CE, 1040.0, "27-Nov-2026", 18.0, 4.0, 28.0, 420_000L, 40_000L)
        val ce1000 = OptionStrikeQuote("EARLYBULL", OptionSide.CE, 1000.0, "27-Nov-2026", 40.0, 6.0, 17.6, 480_000L, 30_000L)

        val earlyBull = makeStock(
            symbol = "EARLYBULL",
            lastPrice = 1026.0, // Between R1 (1020) and R2 (1040) -> early zone
            changePercent = 2.6,
            rvol = 2.1,
            rsi1h = 63.0,
            rsi15m = 62.5,
            rsi5m = 66.0,
            pivots = pivots,
        ).copy(
            oiDelivery = summary,
            atmOptions = AtmOptionBundle(
                expiryDate = "27-Nov-2026",
                atmCe = ce1020,
                allStrikesCe = listOf(ce1000, ce1020, ce1040),
            ),
        )

        // Short covering trap: rising price (+2.8%) on falling OI (-250k)
        val shortCoverSummary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 2.8,
            liveOi = 8_000_000L,
            liveOiChange = -250_000L,
            liveOiChangePct = -3.0,
            livePcr = 0.90,
            liveDeliveryQty = 400_000L,
            liveDeliveryPct = 25.0,
            rawHistoryDesc = emptyList(),
        )
        val trapStock = makeStock(
            symbol = "SC_TRAP",
            lastPrice = 1025.0,
            changePercent = 2.8,
            rvol = 1.9,
            rsi1h = 61.0,
            rsi15m = 60.0,
            rsi5m = 68.0,
            pivots = pivots,
        ).copy(oiDelivery = shortCoverSummary)

        val candidates = PositionalAnalyzer.analyze(listOf(trapStock, earlyBull))
        assertEquals(2, candidates.size)

        val first = candidates.first()
        assertEquals("EARLYBULL", first.stock.symbol)
        assertEquals(OptionSide.CE, first.bias)
        assertEquals(PositionalStage.EARLY_IGNITION, first.stage)
        assertEquals("R1 → R2", first.pivotZoneLabel)
        assertEquals("Target R2", first.nextTargetLabel)
        assertEquals(1040.0, first.nextTargetSpotPrice ?: 0.0, 0.01)
        assertNotNull(first.nextTargetAtmOptionPrice)
        assertTrue(first.convictionScore >= 85)
        assertTrue(first.deliveryRising)

        val trap = candidates.last()
        assertEquals("SC_TRAP", trap.stock.symbol)
        assertEquals(FnoBuildupQuadrant.SHORT_COVERING, trap.stock.oiDelivery.buildupQuadrant)
        assertEquals(PositionalStage.EXTENDED_TRAIL, trap.stage)
        assertTrue(trap.warningNote?.contains("Short Covering", ignoreCase = true) == true)
    }

    @Test
    fun `bearish short buildup in PP-to-S2 zone flags PE EARLY_IGNITION and extended R3 flags TRAIL`() {
        val pivots = PivotLevels(
            prevHigh = 520.0,
            prevLow = 480.0,
            prevClose = 500.0,
            pp = 500.0,
            r1 = 520.0,
            r2 = 540.0,
            r3 = 580.0,
            s1 = 480.0,
            s2 = 460.0,
            s3 = 420.0,
        )
        val peSummary = PositionSignalEvaluator.buildSummary(
            priceChangePct = -2.4,
            liveOi = 9_500_000L,
            liveOiChange = 900_000L,
            liveOiChangePct = 10.5,
            livePcr = 0.64,
            liveDeliveryQty = 950_000L,
            liveDeliveryPct = 48.0,
            rawHistoryDesc = listOf(
                DailyPositionSnapshot("2026-11-16", 500.0, -0.5, 1_500_000L, 8_600_000L, 200_000L, 2.3, 525_000L, 35.0),
            ),
            livePcrOiChangePct = -5.0,
        )
        val peStock = makeStock(
            symbol = "BEARSWING",
            lastPrice = 474.0, // Between S1 (480) and S2 (460)
            changePercent = -2.4,
            rvol = 1.85,
            rsi1h = 37.0,
            rsi15m = 36.0,
            rsi5m = 33.0,
            pivots = pivots,
        ).copy(oiDelivery = peSummary)

        val extendedSummary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 6.2,
            liveOi = 7_000_000L,
            liveOiChange = 500_000L,
            liveOiChangePct = 7.7,
            livePcr = 1.30,
            liveDeliveryQty = 800_000L,
            liveDeliveryPct = 42.0,
            rawHistoryDesc = emptyList(),
        )
        val extendedCe = makeStock(
            symbol = "EXTENDED_R3",
            lastPrice = 590.0, // > R3 (580)
            changePercent = 6.2,
            rvol = 2.4,
            rsi1h = 74.0,
            rsi15m = 72.0,
            rsi5m = 78.0,
            pivots = pivots,
        ).copy(oiDelivery = extendedSummary)

        val results = PositionalAnalyzer.analyze(listOf(extendedCe, peStock))
        assertEquals(2, results.size)
        assertEquals("BEARSWING", results[0].stock.symbol)
        assertEquals(OptionSide.PE, results[0].bias)
        assertEquals(PositionalStage.EARLY_IGNITION, results[0].stage)
        assertEquals("Target S2", results[0].nextTargetLabel)

        assertEquals("EXTENDED_R3", results[1].stock.symbol)
        assertEquals(PositionalStage.EXTENDED_TRAIL, results[1].stage)
        assertTrue(results[1].warningNote?.contains("R3", ignoreCase = true) == true)
    }

    @Test
    fun `evaluates 1h 30m 15m 5m 1m RSI entry signal when above 65 for CE or below 35 for PE`() {
        val pivots = PivotLevels(520.0, 480.0, 500.0, 500.0, 520.0, 540.0, 580.0, 480.0, 460.0, 420.0)
        val ceReadyStock = makeStock(
            symbol = "CEREADY",
            lastPrice = 515.0,
            changePercent = 3.0,
            rvol = 2.0,
            rsi1h = 68.0,
            rsi15m = 67.0,
            rsi5m = 69.0,
            pivots = pivots,
        ).copy(rsi30m = 67.5, rsi1m = 71.0)

        val ceMatrix = PositionalAnalyzer.evaluatePositionalRsi(ceReadyStock, OptionSide.CE)
        assertEquals(5, ceMatrix.alignedCount)
        assertEquals(5, ceMatrix.totalCount)
        assertTrue(ceMatrix.isEntryReady)
        assertTrue(ceMatrix.entrySignalLabel.contains("TAKE CE POSITION"))

        val peReadyStock = makeStock(
            symbol = "PEREADY",
            lastPrice = 485.0,
            changePercent = -3.0,
            rvol = 2.0,
            rsi1h = 31.0,
            rsi15m = 32.5,
            rsi5m = 29.0,
            pivots = pivots,
        ).copy(rsi30m = 33.0, rsi1m = 28.0)

        val peMatrix = PositionalAnalyzer.evaluatePositionalRsi(peReadyStock, OptionSide.PE)
        assertEquals(5, peMatrix.alignedCount)
        assertEquals(5, peMatrix.totalCount)
        assertTrue(peMatrix.isEntryReady)
        assertTrue(peMatrix.entrySignalLabel.contains("TAKE PE POSITION"))

        // If 1m dips back inside 35..65, entry should NOT fire prematurely
        val notYetCe = PositionalAnalyzer.evaluatePositionalRsi(ceReadyStock.copy(rsi1m = 61.0), OptionSide.CE)
        assertEquals(4, notYetCe.alignedCount)
        assertEquals(false, notYetCe.isEntryReady)
    }

    @Test
    fun `detects fresh EMA 20 bullish and bearish crossovers and boosts early positional stage`() {
        val pivots = PivotLevels(520.0, 480.0, 500.0, 500.0, 520.0, 540.0, 580.0, 480.0, 460.0, 420.0)
        // 24 bars trading around 490..492 followed by a sharp break above EMA 20 to 506 on bar 25
        val baseTs = 1_700_000_000L
        val bullish15mBars = (0 until 24).map { idx ->
            CandleBar(baseTs + idx * 900L, 492.0, 494.0, 489.0, 491.0, 10_000L)
        } + listOf(
            CandleBar(baseTs + 24 * 900L, 491.0, 508.0, 490.0, 506.0, 45_000L),
        )

        val emaStatus = TechnicalIndicators.evaluateEmaCross(bullish15mBars, "15m", period = 20, lookbackBars = 2)
        assertNotNull(emaStatus)
        assertEquals(OptionSide.CE, emaStatus!!.freshCrossDirection)
        assertTrue(emaStatus.isAboveEma)
        assertEquals(0, emaStatus.crossBarsAgo)

        val ceCrossStock = makeStock(
            symbol = "EMABULL",
            lastPrice = 506.0, // in PP (500) -> R1 (520) early zone
            changePercent = 1.2,
            rvol = 1.3,
            rsi1h = 56.0,
            rsi15m = 62.0,
            rsi5m = 64.0,
            pivots = pivots,
        ).copy(candles15m = bullish15mBars)

        val scored = PositionalAnalyzer.scoreStock(ceCrossStock)
        assertNotNull(scored)
        assertTrue(scored!!.hasFreshEma20Cross)
        assertTrue("15m" in scored.ema20Summary.freshCrossLabels)
        assertEquals(PositionalStage.EARLY_IGNITION, scored.stage)
        assertTrue(scored.ema20Summary.signalLabel.contains("15m"))

        // Bearish breakdown below EMA 20
        val bearish15mBars = (0 until 24).map { idx ->
            CandleBar(baseTs + idx * 900L, 510.0, 512.0, 508.0, 509.0, 10_000L)
        } + listOf(
            CandleBar(baseTs + 24 * 900L, 509.0, 510.0, 492.0, 494.0, 48_000L),
        )
        val peStatus = TechnicalIndicators.evaluateEmaCross(bearish15mBars, "15m", period = 20, lookbackBars = 2)
        assertNotNull(peStatus)
        assertEquals(OptionSide.PE, peStatus!!.freshCrossDirection)
        assertEquals(false, peStatus.isAboveEma)
        assertTrue(peStatus.isBelowEma)

        // Sustained trend cleanly BELOW EMA 20 across 1h/30m/15m for PE (crossed >2 bars ago -> no fresh cross, but aligned)
        val sustainedBelowBars = (0 until 20).map { idx ->
            CandleBar(baseTs + idx * 900L, 515.0, 516.0, 512.0, 514.0, 10_000L)
        } + (20 until 26).map { idx ->
            CandleBar(baseTs + idx * 900L, 485.0, 486.0, 481.0, 482.0, 12_000L)
        }
        val peAlignedStock = makeStock(
            symbol = "PEBELOW",
            lastPrice = 482.0,
            changePercent = -2.2,
            rvol = 1.6,
            rsi1h = 39.0,
            rsi15m = 36.0,
            rsi5m = 33.0,
            pivots = pivots,
        ).copy(
            candles1h = sustainedBelowBars,
            candles30m = sustainedBelowBars,
            candles15m = sustainedBelowBars,
        )
        val peScored = PositionalAnalyzer.scoreStock(peAlignedStock)
        assertNotNull(peScored)
        assertEquals(OptionSide.PE, peScored!!.bias)
        assertEquals(false, peScored.hasFreshEma20Cross)
        assertTrue(peScored.isEma20Aligned)
        assertTrue(peScored.isEma20CrossOrAligned)
        assertTrue(peScored.ema20Summary.signalLabel.contains("BELOW EMA20"))
    }

    @Test
    fun `detects 2x 20-day volume SMA surge and emits unique breakout notifications`() {
        val pivots = PivotLevels(520.0, 480.0, 500.0, 500.0, 520.0, 540.0, 580.0, 480.0, 460.0, 420.0)
        // 20 prior daily sessions each with 1,000,000 volume; day 21 is today's bar (excluded from SMA baseline)
        val daySec = 86_400L
        val baseDayTs = 1_760_000_000L
        val dailyBars = (0 until 20).map { d ->
            CandleBar(baseDayTs + d * daySec, 500.0, 510.0, 495.0, 505.0, 1_000_000L)
        }
        val todayMs = (baseDayTs + 20 * daySec) * 1000L
        val surgeMetrics = TechnicalIndicators.calculateVolumeSma20Surge(
            dailyBars = dailyBars,
            currentSessionVolume = 2_250_000L, // 2.25x 20D SMA
            nowEpochMs = todayMs,
        )
        assertNotNull(surgeMetrics)
        assertEquals(1_000_000.0, surgeMetrics!!.avgVolume20d, 1.0)
        assertEquals(2.25, surgeMetrics.surgeRatio, 0.01)
        assertEquals(20, surgeMetrics.priorDaysUsed)
        assertTrue(surgeMetrics.isVolumeSurge2x)

        val prevSurgeSet = mutableSetOf<String>()
        val activeAlerts = mutableMapOf<String, com.example.domain.model.LiveScannerAlert>()

        val flatStock = makeStock("SURGER", 510.0, 1.8, 2.3, 62.0, 63.0, 64.0, pivots)
            .copy(
                sharesVolume = 1_500_000L,
                volumeSma20d = 1_000_000.0,
                volumeSurgeRatio = 1.5,
                isVolumeSurge2x = false,
            )

        // Baseline scan (below 2x) -> no notification yet
        val tick1 = PivotAlertDetector.evaluateVolumeSurgeBatch(
            stocks = listOf(flatStock),
            previousSurgeSymbols = prevSurgeSet,
            activeVolumeAlertsById = activeAlerts,
            hasBaseline = true,
            nowMs = todayMs,
        )
        assertEquals(0, tick1.newlyTriggeredVolumeAlerts.size)

        // Crosses >= 2.0x -> emits notification
        val surgingStock = flatStock.copy(
            sharesVolume = 2_250_000L,
            volumeSurgeRatio = 2.25,
            isVolumeSurge2x = true,
        )
        val tick2 = PivotAlertDetector.evaluateVolumeSurgeBatch(
            stocks = listOf(surgingStock),
            previousSurgeSymbols = prevSurgeSet,
            activeVolumeAlertsById = activeAlerts,
            hasBaseline = true,
            nowMs = todayMs + 10_000L,
        )
        assertEquals(1, tick2.newlyTriggeredVolumeAlerts.size)
        assertEquals(AlertCategory.VOLUME_SURGE, tick2.newlyTriggeredVolumeAlerts.first().category)
        assertTrue(tick2.newlyTriggeredVolumeAlerts.first().title.contains("2.3x") || tick2.newlyTriggeredVolumeAlerts.first().title.contains("2.2x"))

        // Subsequent tick while still >= 2x -> retains active feed alert, does not re-notify
        val tick3 = PivotAlertDetector.evaluateVolumeSurgeBatch(
            stocks = listOf(surgingStock),
            previousSurgeSymbols = prevSurgeSet,
            activeVolumeAlertsById = activeAlerts,
            hasBaseline = true,
            nowMs = todayMs + 20_000L,
        )
        assertEquals(1, tick3.activeVolumeAlerts.size)
        assertEquals(0, tick3.newlyTriggeredVolumeAlerts.size)

        // Positional scoring promotes early zone 2x surge + populates hold rule
        val posCandidate = PositionalAnalyzer.scoreStock(surgingStock)
        assertNotNull(posCandidate)
        assertTrue(posCandidate!!.hasVolumeSurge2x)
        assertEquals(PositionalStage.EARLY_IGNITION, posCandidate.stage)
        assertTrue(posCandidate.trendHoldRule.contains("EMA 20"))
    }

    @Test
    fun `detects positional volume greater than 2x combined with multi-TF RSI above 65 buzz without duplicate triggers`() {
        val pivots = PivotLevels(520.0, 480.0, 500.0, 500.0, 520.0, 540.0, 580.0, 480.0, 460.0, 420.0)
        // Vol 2.4x + 1h(67), 30m(68), 15m(66.5), 5m(69) > 65 -> qualifies for Positional Buzz
        val buzzStock = makeStock(
            symbol = "BUZZER",
            lastPrice = 514.0,
            changePercent = 2.8,
            rvol = 2.4,
            rsi1h = 67.0,
            rsi15m = 66.5,
            rsi5m = 69.0,
            pivots = pivots,
        ).copy(
            rsi30m = 68.0,
            rsi1m = 63.0,
            volumeSma20d = 1_000_000.0,
            volumeSurgeRatio = 2.4,
            isVolumeSurge2x = true,
        )

        // Vol 2.5x but RSI below 65 -> must NOT buzz
        val volOnlyStock = buzzStock.copy(
            symbol = "VOLONLY",
            rsi1h = 58.0,
            rsi30m = 59.0,
            rsi15m = 60.0,
            rsi5m = 61.0,
        )

        // RSI > 65 but Vol 1.6x (< 2x) -> must NOT buzz
        val rsiOnlyStock = buzzStock.copy(
            symbol = "RSIONLY",
            volumeSurgeRatio = 1.6,
            isVolumeSurge2x = false,
        )

        assertTrue(PositionalAnalyzer.evaluateVol2xRsi65(buzzStock).matched)
        assertEquals(false, PositionalAnalyzer.evaluateVol2xRsi65(volOnlyStock).matched)
        assertEquals(false, PositionalAnalyzer.evaluateVol2xRsi65(rsiOnlyStock).matched)

        val ranked = PositionalAnalyzer.analyze(listOf(volOnlyStock, rsiOnlyStock, buzzStock))
        assertEquals("BUZZER", ranked.first().stock.symbol)
        assertTrue(ranked.first().hasVol2xAndRsi65)
        assertEquals(4, ranked.first().rsiAbove65Count)

        val prevBuzzSet = mutableSetOf<String>()
        val activeBuzzMap = mutableMapOf<String, com.example.domain.model.LiveScannerAlert>()
        val tick1 = PivotAlertDetector.evaluatePositionalVolRsiBuzzBatch(
            stocks = listOf(volOnlyStock, rsiOnlyStock, buzzStock),
            previousBuzzSymbols = prevBuzzSet,
            activeBuzzAlertsById = activeBuzzMap,
            nowMs = 1_760_000_000_000L,
        )
        assertEquals(1, tick1.newlyTriggeredBuzzAlerts.size)
        assertEquals("BUZZER", tick1.newlyTriggeredBuzzAlerts.first().symbol)
        assertEquals(AlertCategory.POSITIONAL_VOL_RSI, tick1.newlyTriggeredBuzzAlerts.first().category)

        // Next 10s tick while still active: keeps active alert, does NOT re-buzz
        val tick2 = PivotAlertDetector.evaluatePositionalVolRsiBuzzBatch(
            stocks = listOf(volOnlyStock, rsiOnlyStock, buzzStock),
            previousBuzzSymbols = prevBuzzSet,
            activeBuzzAlertsById = activeBuzzMap,
            nowMs = 1_760_000_010_000L,
        )
        assertEquals(1, tick2.activeBuzzAlerts.size)
        assertEquals(0, tick2.newlyTriggeredBuzzAlerts.size)
    }

    private fun makeStock(
        symbol: String,
        lastPrice: Double,
        changePercent: Double,
        rvol: Double,
        rsi1h: Double,
        rsi15m: Double,
        rsi5m: Double,
        pivots: PivotLevels,
    ): StockScanResult {
        return StockScanResult(
            symbol = symbol,
            yahooTicker = "$symbol.NS",
            companyName = "$symbol Ltd",
            sector = "FnO",
            badgeColorHex = 0xFF2962FF,
            lastPrice = lastPrice,
            previousClose = pivots.prevClose,
            change = lastPrice - pivots.prevClose,
            changePercent = changePercent,
            sharesVolume = 2_500_000L,
            latest5mVolume = 85_000L,
            rvol = rvol,
            rsi1h = rsi1h,
            rsi30m = (rsi1h + rsi15m) / 2.0,
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
            pivotLevels = pivots,
        )
    }
}
