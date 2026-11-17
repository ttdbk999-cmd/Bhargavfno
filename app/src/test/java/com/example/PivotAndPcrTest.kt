package com.example

import com.example.domain.indicator.OptionPivotPricing
import com.example.domain.indicator.PivotAlertDetector
import com.example.domain.indicator.PositionSignalEvaluator
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.AlertCategory
import com.example.domain.model.AtmOptionBundle
import com.example.domain.model.BreakoutFilterMode
import com.example.domain.model.CandleBar
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.LiveScannerAlert
import com.example.domain.model.OptionSide
import com.example.domain.model.OptionStrikeQuote
import com.example.domain.model.PivotLevels
import com.example.domain.model.PivotWatchLevel
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.StockScanResult
import com.example.ui.scanner.ScannerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PivotAndPcrTest {

    private val istZone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `standard floor pivots match classic HLC formulas`() {
        // H=110, L=90, C=100 => PP=100, R1=110, S1=90, R2=120, S2=80, R3=130, S3=70
        val pivots = TechnicalIndicators.calculateStandardPivots(high = 110.0, low = 90.0, close = 100.0)
        assertNotNull(pivots)
        pivots!!
        assertEquals(100.0, pivots.pp, 0.001)
        assertEquals(110.0, pivots.r1, 0.001)
        assertEquals(120.0, pivots.r2, 0.001)
        assertEquals(130.0, pivots.r3, 0.001)
        assertEquals(90.0, pivots.s1, 0.001)
        assertEquals(80.0, pivots.s2, 0.001)
        assertEquals(70.0, pivots.s3, 0.001)
    }

    @Test
    fun `calculates standard pivots from prior trading day bars and flags R3 and S3 breakouts`() {
        val yest1 = LocalDateTime.of(2026, 11, 15, 9, 15).atZone(istZone).toEpochSecond()
        val yest2 = LocalDateTime.of(2026, 11, 15, 15, 25).atZone(istZone).toEpochSecond()
        val today1 = LocalDateTime.of(2026, 11, 16, 9, 15).atZone(istZone).toEpochSecond()

        val bars = listOf(
            CandleBar(yest1, 95.0, 110.0, 92.0, 102.0, 10_000),
            CandleBar(yest2, 102.0, 105.0, 90.0, 100.0, 10_000),
            CandleBar(today1, 128.0, 134.0, 127.0, 132.0, 50_000),
        )

        val pivots = TechnicalIndicators.calculateStandardPivotsFromBars(bars)
        assertNotNull(pivots)
        assertEquals(130.0, pivots!!.r3, 0.001)
        assertEquals(70.0, pivots.s3, 0.001)

        val r3Stock = makeStock(price = 132.6, pivots = pivots)
        assertTrue(r3Stock.isAboveR3)
        assertFalse(r3Stock.isBelowS3)
        assertEquals(2.0, r3Stock.percentAboveR3()!!, 0.001)
        assertNull(r3Stock.percentBelowS3())
        assertTrue(TechnicalIndicators.formatPivotZone(pivots, 132.6, OptionSide.CE).contains("Above R3"))

        val s3Stock = makeStock(price = 68.6, pivots = pivots)
        assertTrue(s3Stock.isBelowS3)
        assertFalse(s3Stock.isAboveR3)
        assertEquals(2.0, s3Stock.percentBelowS3()!!, 0.001)
        assertNull(s3Stock.percentAboveR3())
        assertTrue(TechnicalIndicators.formatPivotZone(pivots, 68.6, OptionSide.PE).contains("Below S3"))
    }

    @Test
    fun `dedicated ABOVE_R3 filter isolates only R3 breakouts and prioritizes fresh crosses`() {
        val pivots = TechnicalIndicators.calculateStandardPivots(high = 110.0, low = 90.0, close = 100.0)!!
        // r3 = 130.0
        val belowR3 = makeStock(symbol = "INFY", price = 124.0, pivots = pivots)
        val establishedR3 = makeStock(symbol = "POWERINDIA", price = 136.5, pivots = pivots) // +5% above R3
        val freshR3 = makeStock(symbol = "MCX", price = 131.3, pivots = pivots) // +1% above R3, newly crossed

        val state = ScannerUiState(
            allStocks = listOf(belowR3, establishedR3, freshR3),
            breakoutFilter = BreakoutFilterMode.ABOVE_R3,
            newlyCrossedR3Symbols = setOf("MCX"),
        )

        assertEquals(2, state.ceAboveR3Count)
        assertEquals(listOf("MCX", "POWERINDIA"), state.aboveR3Stocks.map { it.symbol })
        assertEquals(listOf("MCX", "POWERINDIA"), state.ceBoardStocks.map { it.symbol })
        assertFalse(state.ceBoardStocks.any { it.symbol == "INFY" })
    }

    @Test
    fun `R2_R3 and S2_S3 filters track all R1-R3 and S1-S3 counts and isolate priority levels`() {
        val pivots = TechnicalIndicators.calculateStandardPivots(high = 110.0, low = 90.0, close = 100.0)!!
        // PP=100, R1=110, R2=120, R3=130, S1=90, S2=80, S3=70
        val atR1 = makeStock(symbol = "R1ONLY", price = 112.0, pivots = pivots)
        val atR2 = makeStock(symbol = "R2STOCK", price = 122.0, pivots = pivots)
        val atR3 = makeStock(symbol = "R3STOCK", price = 133.0, pivots = pivots)
        val atS1 = makeStock(symbol = "S1ONLY", price = 88.0, pivots = pivots)
        val atS2 = makeStock(symbol = "S2STOCK", price = 78.0, pivots = pivots)
        val atS3 = makeStock(symbol = "S3STOCK", price = 67.0, pivots = pivots)

        val all = listOf(atR1, atR2, atR3, atS1, atS2, atS3)
        val r2r3State = ScannerUiState(
            allStocks = all,
            breakoutFilter = BreakoutFilterMode.R2_R3,
            newlyCrossedPriorityPivotSymbols = setOf("R2STOCK"),
        )

        assertEquals(3, r2r3State.ceAboveR1Count)
        assertEquals(2, r2r3State.ceAboveR2Count)
        assertEquals(1, r2r3State.ceAboveR3Count)
        assertEquals(3, r2r3State.peBelowS1Count)
        assertEquals(2, r2r3State.peBelowS2Count)
        assertEquals(1, r2r3State.peBelowS3Count)

        assertEquals(setOf("R2STOCK", "R3STOCK"), r2r3State.ceBoardStocks.map { it.symbol }.toSet())
        assertFalse(r2r3State.ceBoardStocks.any { it.symbol == "R1ONLY" })

        assertEquals(
            setOf("R2STOCK", "R3STOCK", "S2STOCK", "S3STOCK"),
            r2r3State.priorityPivotWatchStocks.map { it.symbol }.toSet()
        )

        val s2s3State = r2r3State.copy(breakoutFilter = BreakoutFilterMode.S2_S3)
        assertEquals(setOf("S2STOCK", "S3STOCK"), s2s3State.peBoardStocks.map { it.symbol }.toSet())
        assertFalse(s2s3State.peBoardStocks.any { it.symbol == "S1ONLY" })
    }

    @Test
    fun `PivotAlertDetector tracks R1-R3 and S1-S3 step-ups and marks R2, R3, S2, S3 as priority`() {
        val pivots = TechnicalIndicators.calculateStandardPivots(high = 110.0, low = 90.0, close = 100.0)!!
        val prevCeRank = mutableMapOf<String, Int>()
        val prevPeRank = mutableMapOf<String, Int>()
        val activeById = mutableMapOf<String, LiveScannerAlert>()

        // Baseline scan (hasBaseline = false): R1 (not priority) & S1 (not priority)
        val baseline = PivotAlertDetector.evaluateBatch(
            stocks = listOf(
                makeStock(symbol = "TCS", price = 112.0, pivots = pivots),
                makeStock(symbol = "WIPRO", price = 88.0, pivots = pivots),
            ),
            previousCeRankBySymbol = prevCeRank,
            previousPeRankBySymbol = prevPeRank,
            activeAlertsById = activeById,
            hasBaseline = false,
            nowMs = 1_700_000_000_000L,
        )
        assertTrue(baseline.newlyTriggeredAlerts.isEmpty())
        assertEquals(2, baseline.activePivotAlerts.size)
        assertTrue(baseline.activePivotAlerts.none { it.isPriorityWatch })

        // Step-up scan (hasBaseline = true): TCS R1->R2 (priority), WIPRO S1->S3 (priority), INFY 0->R1 (non-priority)
        val stepUp = PivotAlertDetector.evaluateBatch(
            stocks = listOf(
                makeStock(symbol = "TCS", price = 122.0, pivots = pivots),
                makeStock(symbol = "WIPRO", price = 68.0, pivots = pivots),
                makeStock(symbol = "INFY", price = 111.0, pivots = pivots),
            ),
            previousCeRankBySymbol = prevCeRank,
            previousPeRankBySymbol = prevPeRank,
            activeAlertsById = activeById,
            hasBaseline = true,
            nowMs = 1_700_000_010_000L,
        )

        assertEquals(3, stepUp.newlyTriggeredAlerts.size)
        assertEquals(setOf("TCS", "WIPRO"), stepUp.newlyCrossedPrioritySymbols)

        val tcsAlert = stepUp.newlyTriggeredAlerts.first { it.symbol == "TCS" }
        assertEquals(PivotWatchLevel.ABOVE_R2, tcsAlert.level)
        assertTrue(tcsAlert.isPriorityWatch)

        val wiproAlert = stepUp.newlyTriggeredAlerts.first { it.symbol == "WIPRO" }
        assertEquals(PivotWatchLevel.BELOW_S3, wiproAlert.level)
        assertTrue(wiproAlert.isPriorityWatch)

        val infyAlert = stepUp.newlyTriggeredAlerts.first { it.symbol == "INFY" }
        assertEquals(PivotWatchLevel.ABOVE_R1, infyAlert.level)
        assertFalse(infyAlert.isPriorityWatch)

        val rsiAlert = RsiCrossAlert(
            id = "MCX_M5_CE",
            symbol = "MCX",
            side = OptionSide.CE,
            timeframe = ChartTimeframe.M5,
            rsiValue = 73.4,
            crossedAtEpochMs = 1_700_000_005_000L,
            crossedAtTimeIst = "10:15:05 IST",
            lastPrice = 135.0,
            changePercent = 4.2,
            isActiveExtreme = true,
        )
        val unified = PivotAlertDetector.buildUnifiedAlerts(stepUp.activePivotAlerts, listOf(rsiAlert))
        assertTrue(unified.any { it.category == AlertCategory.PIVOT_BREAKOUT && it.symbol == "TCS" })
        assertTrue(unified.any { it.category == AlertCategory.RSI_CROSS && it.symbol == "MCX" })
    }

    @Test
    fun `position evaluator exposes PutCall PCR, CallPut ratio, and PCR OI change percent`() {
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 2.4,
            liveOi = 1_200_000L,
            liveOiChange = 120_000L,
            liveOiChangePct = 11.1,
            livePcr = 1.25,
            liveDeliveryQty = 450_000L,
            liveDeliveryPct = 52.0,
            rawHistoryDesc = emptyList(),
            liveCallToPutRatio = 0.80,
            livePcrOiChangePct = 12.5,
            livePutOiChange = 300_000L,
            liveCallOiChange = 100_000L,
            liveIntradayDeltaPcr = 3.0,
        )

        assertEquals(1.25, summary.pcr!!, 0.001)
        assertEquals(0.80, summary.callToPutRatio!!, 0.001)
        assertEquals(12.5, summary.pcrOiChangePct!!, 0.001)
        assertEquals(300_000L, summary.putOiChange)
        assertEquals(100_000L, summary.callOiChange)
        assertEquals(3.0, summary.intradayDeltaPcr!!, 0.001)
    }

    @Test
    fun `OptionPivotPricing computes R1-R3 CE and S1-S3 PE projections from live option chain`() {
        val ce149 = quote("PNB", OptionSide.CE, 149.0, 3.62)
        val ce150 = quote("PNB", OptionSide.CE, 150.0, 3.11)
        val ce151 = quote("PNB", OptionSide.CE, 151.0, 2.50)
        val ce152 = quote("PNB", OptionSide.CE, 152.0, 2.13)
        val ce153 = quote("PNB", OptionSide.CE, 153.0, 1.75)
        val ce155 = quote("PNB", OptionSide.CE, 155.0, 1.15)

        val pe147 = quote("PNB", OptionSide.PE, 147.0, 1.25)
        val pe149 = quote("PNB", OptionSide.PE, 149.0, 2.05)
        val pe150 = quote("PNB", OptionSide.PE, 150.0, 2.60)
        val pe151 = quote("PNB", OptionSide.PE, 151.0, 3.29)
        val pe152 = quote("PNB", OptionSide.PE, 152.0, 4.05)
        val pe153 = quote("PNB", OptionSide.PE, 153.0, 4.88)

        val pivots = PivotLevels(
            prevHigh = 148.0,
            prevLow = 144.0,
            prevClose = 146.0,
            pp = 146.0,
            r1 = 148.0,
            r2 = 151.80,
            r3 = 154.80,
            s1 = 148.80,
            s2 = 147.80,
            s3 = 146.80,
        )

        val stock = makeStock(symbol = "PNB", price = 150.80, pivots = pivots).copy(
            atmOptions = AtmOptionBundle(
                expiryDate = "23-Nov-2026",
                atmCe = ce151,
                atmPe = pe151,
                nearbyStrikesCe = listOf(ce149, ce150, ce151, ce152, ce153),
                nearbyStrikesPe = listOf(pe149, pe150, pe151, pe152, pe153),
                allStrikesCe = listOf(ce149, ce150, ce151, ce152, ce153, ce155),
                allStrikesPe = listOf(pe147, pe149, pe150, pe151, pe152, pe153),
            ),
        )

        val projections = OptionPivotPricing.buildPivotProjections(stock, OptionSide.CE)
        assertEquals(listOf("R3", "R2", "R1", "PP", "S1", "S2", "S3"), projections.map { it.levelLabel })

        val r2 = projections.first { it.levelLabel == "R2" }
        val r3 = projections.first { it.levelLabel == "R3" }
        val s1 = projections.first { it.levelLabel == "S1" }
        val s2 = projections.first { it.levelLabel == "S2" }
        val s3 = projections.first { it.levelLabel == "S3" }

        // Spot 150.80 -> R2 151.80 (+1.00): 151 CE assumes 150 CE moneyness = ₹3.11
        assertEquals(3.11, r2.projectedAtmOptionPrice!!, 0.01)
        // Nearest strike to R3 154.80 uses full chain (155 CE), not limited to 5-strike window
        assertEquals(155.0, r3.nearestStrikeQuote!!.strikePrice, 0.001)
        assertTrue(r3.projectedAtmOptionPrice!! > r2.projectedAtmOptionPrice!!)

        // Spot 150.80 -> S1 148.80 (-2.00): 151 PE assumes 153 PE moneyness = ₹4.88
        assertEquals(4.88, s1.projectedAtmOptionPrice!!, 0.01)
        // Deep drop to S2 / S3 continues increasing PE premium monotonically
        assertTrue(s2.projectedAtmOptionPrice!! > s1.projectedAtmOptionPrice!!)
        assertTrue(s3.projectedAtmOptionPrice!! > s2.projectedAtmOptionPrice!!)
        // Nearest strike to S3 146.80 uses full PE chain (147 PE)
        assertEquals(147.0, s3.nearestStrikeQuote!!.strikePrice, 0.001)

        // When NSE option chain has not synced yet, projected prices return null cleanly (no fake numbers)
        val unsynced = makeStock(symbol = "OIL", price = 437.75, pivots = pivots)
        val unsyncedProjections = OptionPivotPricing.buildPivotProjections(unsynced, OptionSide.PE)
        assertTrue(unsyncedProjections.all { it.projectedAtmOptionPrice == null })
    }

    private fun quote(symbol: String, side: OptionSide, strike: Double, ltp: Double) = OptionStrikeQuote(
        symbol = symbol,
        side = side,
        strikePrice = strike,
        expiryDate = "23-Nov-2026",
        runningPrice = ltp,
        change = 0.0,
        changePercent = 0.0,
        openInterest = 100_000L,
        oiChange = 10_000L,
    )

    private fun makeStock(
        symbol: String = "POWERINDIA",
        price: Double,
        pivots: PivotLevels,
    ) = StockScanResult(
        symbol = symbol,
        yahooTicker = "$symbol.NS",
        companyName = "Hitachi Energy",
        sector = "Industrials",
        badgeColorHex = 0xFF26A69A,
        lastPrice = price,
        previousClose = 100.0,
        change = price - 100.0,
        changePercent = ((price - 100.0) / 100.0) * 100.0,
        sharesVolume = 250_000L,
        latest5mVolume = 20_000L,
        rvol = 2.5,
        rsi1h = 68.0,
        rsi30m = 71.0,
        rsi15m = 72.0,
        rsi5m = 75.0,
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
