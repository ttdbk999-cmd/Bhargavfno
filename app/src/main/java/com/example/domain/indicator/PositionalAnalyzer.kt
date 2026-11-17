package com.example.domain.indicator

import com.example.domain.model.FnoBuildupQuadrant
import com.example.domain.model.OptionSide
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.StockScanResult
import java.util.Locale
import kotlin.math.abs

enum class AppDestination(val label: String) {
    GAPBOARD("GapBoard"),
    POSITIONAL("Positional"),
}

enum class PositionalStage(val badgeLabel: String) {
    EARLY_IGNITION("EARLY IGNITION"),
    CONFIRMED_HOLD("CONFIRMED HOLD"),
    EXTENDED_TRAIL("EXTENDED / TRAIL"),
}

enum class PositionalFilter(val label: String) {
    ALL("All Setups"),
    VOL2X_RSI65("Vol>2x + RSI>65 🔊"),
    VOLUME_SURGE("Vol Surge ≥2x"),
    EMA20_CROSS("EMA20 Cross ⚡"),
    EMA20_CROSS_OR_ALIGNED("EMA20 Cross + Trend"),
    EMA20_CE_ABOVE("CE ↑ Cross/Above"),
    EMA20_PE_BELOW("PE ↓ Cross/Below"),
    RSI_ENTRY("Entry >65/<35"),
    EARLY_IGNITION("Early Ignition"),
    CONFIRMED_HOLD("Confirmed Hold"),
    CE_LONGS("CE Longs ↑"),
    PE_SHORTS("PE Shorts ↓"),
    WATCHLIST("Watchlist ★"),
}

data class PositionalVolRsiSignal(
    val matched: Boolean,
    val volumeRatio: Double?,
    val rsiAbove65Count: Int,
    val totalRsiCount: Int,
    val matchedTfLabels: List<String>,
)

data class PositionalRsiSlot(
    val label: String,
    val value: Double?,
    val passed: Boolean,
)

data class PositionalRsiSummary(
    val slots: List<PositionalRsiSlot>,
    val alignedCount: Int,
    val totalCount: Int,
    val isEntryReady: Boolean,
    val entrySignalLabel: String,
)

data class PositionalEma20Slot(
    val label: String,
    val emaValue: Double?,
    val distancePct: Double?,
    val alignedWithBias: Boolean,
    val freshCrossForBias: Boolean,
)

data class PositionalEma20Summary(
    val slots: List<PositionalEma20Slot>,
    val alignedCount: Int,
    val totalCount: Int,
    val freshCrossLabels: List<String>,
    val hasFreshCross: Boolean,
    val signalLabel: String,
) {
    val isAllAligned: Boolean
        get() = totalCount >= 3 && alignedCount == totalCount
}

data class PositionalCandidate(
    val stock: StockScanResult,
    val bias: OptionSide,
    val stage: PositionalStage,
    val convictionScore: Int,
    val pivotZoneLabel: String,
    val nextTargetLabel: String,
    val nextTargetSpotPrice: Double?,
    val nextTargetAtmOptionPrice: Double?,
    val deliveryAvg5dPct: Double?,
    val deliveryRising: Boolean,
    val hasOiData: Boolean,
    val backedUpDaysCount: Int,
    val hasFiveDayBackup: Boolean,
    val rsiEntrySummary: PositionalRsiSummary,
    val ema20Summary: PositionalEma20Summary,
    val reasons: List<String>,
    val warningNote: String?,
    val trendHoldRule: String = "",
    val rsiAbove65Count: Int = 0,
    val hasVol2xAndRsi65: Boolean = false,
) {
    val isRsiEntryReady: Boolean
        get() = rsiEntrySummary.isEntryReady

    val hasFreshEma20Cross: Boolean
        get() = ema20Summary.hasFreshCross

    val isEma20Aligned: Boolean
        get() = ema20Summary.isAllAligned

    val isEma20CrossOrAligned: Boolean
        get() = ema20Summary.hasFreshCross || ema20Summary.isAllAligned

    val hasVolumeSurge2x: Boolean
        get() = stock.isVolumeSurge2x
}

/**
 * Evaluates scanned F&O stocks specifically for early multi-day positional & institutional
 * swing entries (separate from fast 1m/5m intraday GapBoard breakouts).
 *
 * Prioritizes fresh futures OI buildup (Long Buildup / Short Buildup), rising 5-day cash delivery %,
 * 2x 20-day Volume SMA surges, early PP->R2 / PP->S2 pivot windows, higher-timeframe (1h/15m) RSI strength,
 * and PCR confirmation, while flagging Short Covering / Long Unwinding / >R3 / <S3 extensions as trail-only.
 */
object PositionalAnalyzer {

    fun analyze(
        stocks: List<StockScanResult>,
        thresholds: ScannerThresholds = ScannerThresholds(),
    ): List<PositionalCandidate> {
        return stocks.mapNotNull { stock ->
            scoreStock(stock, thresholds)
        }.sortedWith(
            compareByDescending<PositionalCandidate> { it.hasVol2xAndRsi65 }
                .thenBy { it.stage.ordinal }
                .thenByDescending { it.hasFreshEma20Cross }
                .thenByDescending { it.hasVolumeSurge2x }
                .thenByDescending { it.isEma20Aligned }
                .thenByDescending { it.hasOiData }
                .thenByDescending { it.convictionScore }
                .thenByDescending { abs(it.stock.changePercent) }
        )
    }

    fun scoreStock(
        stock: StockScanResult,
        thresholds: ScannerThresholds = ScannerThresholds(),
    ): PositionalCandidate? {
        val oi = stock.oiDelivery
        val hasOiData = (oi.openInterest ?: 0L) > 0L

        val bias = when (oi.buildupQuadrant) {
            FnoBuildupQuadrant.LONG_BUILDUP,
            FnoBuildupQuadrant.SHORT_COVERING -> OptionSide.CE
            FnoBuildupQuadrant.SHORT_BUILDUP,
            FnoBuildupQuadrant.LONG_UNWINDING -> OptionSide.PE
            FnoBuildupQuadrant.NEUTRAL -> if (stock.changePercent >= 0.0) OptionSide.CE else OptionSide.PE
        }

        val avg5dDeliv = oi.history5Days
            .mapNotNull { it.deliveryPct.takeIf { pct -> pct > 0.0 } }
            .take(5)
            .takeIf { it.isNotEmpty() }
            ?.average()

        val deliveryRising = (oi.deliveryPctChangePp != null && oi.deliveryPctChangePp > 0.0) ||
            (oi.deliveryQtyChangePct != null && oi.deliveryQtyChangePct > 10.0) ||
            (oi.deliveryPct != null && avg5dDeliv != null && oi.deliveryPct > avg5dDeliv)

        val pivots = stock.pivotLevels
        val (pivotZoneLabel, nextTargetKey, isEarlyZone, isExtendedZone) = resolvePivotZone(
            spot = stock.lastPrice,
            pivots = pivots,
            bias = bias,
        )

        val projections = OptionPivotPricing.buildPivotProjections(stock, bias)
        val targetProjection = nextTargetKey?.let { key ->
            projections.firstOrNull { it.levelLabel == key }
        }
        val nextTargetSpotPrice = targetProjection?.spotLevelPrice ?: when (nextTargetKey) {
            "R1" -> pivots?.r1
            "R2" -> pivots?.r2
            "R3" -> pivots?.r3
            "S1" -> pivots?.s1
            "S2" -> pivots?.s2
            "S3" -> pivots?.s3
            "PP" -> pivots?.pp
            else -> null
        }

        val reasons = mutableListOf<String>()
        var warningNote: String? = null
        var score = 0

        // 1. Institutional OI Quadrant (max 30 pts)
        when (oi.buildupQuadrant) {
            FnoBuildupQuadrant.LONG_BUILDUP -> {
                score += 30
                val oiPct = oi.oiChangePct?.let { String.format(Locale.US, " (%+.1f%%)", it) }.orEmpty()
                reasons.add("Long Buildup$oiPct")
            }
            FnoBuildupQuadrant.SHORT_BUILDUP -> {
                score += 30
                val oiPct = oi.oiChangePct?.let { String.format(Locale.US, " (%+.1f%%)", it) }.orEmpty()
                reasons.add("Short Buildup$oiPct")
            }
            FnoBuildupQuadrant.SHORT_COVERING -> {
                score += 8
                warningNote = "Short Covering — price up on falling OI; avoid chasing fresh positional size"
            }
            FnoBuildupQuadrant.LONG_UNWINDING -> {
                score += 10
                warningNote = "Long Unwinding — bulls exiting; confirm fresh short OI before PE hold"
            }
            FnoBuildupQuadrant.NEUTRAL -> {
                if (stock.rvol >= 1.2 || abs(stock.changePercent) >= 1.0) {
                    score += 8
                }
            }
        }

        // 2. Delivery % Accumulation (max 25 pts)
        if (deliveryRising) {
            val strongDeliv = (oi.deliveryPctChangePp ?: 0.0) >= 3.0 &&
                oi.deliveryPct != null && avg5dDeliv != null && oi.deliveryPct > avg5dDeliv
            score += if (strongDeliv) 25 else 20
            val ppTag = oi.deliveryPctChangePp?.let { String.format(Locale.US, " %+.1fpp", it) }.orEmpty()
            reasons.add("Rising Delivery$ppTag")
        } else if ((oi.deliveryPct ?: 0.0) >= 35.0) {
            score += 10
            reasons.add("Deliv ${String.format(Locale.US, "%.0f%%", oi.deliveryPct)}")
        }

        // 3. Pivot Entry Window (max 20 pts)
        when {
            isEarlyZone -> {
                score += 20
                reasons.add("Early zone $pivotZoneLabel")
            }
            isExtendedZone -> {
                score += 4
                if (warningNote == null) {
                    warningNote = if (bias == OptionSide.CE) {
                        "Extended past R3 — wait for pullback or trail existing CE"
                    } else {
                        "Extended past S3 — wait for bounce or trail existing PE"
                    }
                }
            }
            pivotZoneLabel.startsWith("R2") || pivotZoneLabel.startsWith("S2") -> {
                score += 13
                reasons.add("Mid breakout $pivotZoneLabel")
            }
            else -> score += 4
        }

        // 4. Higher-Timeframe RSI + RVOL Momentum (max 15 pts)
        if (stock.rvol >= thresholds.minRvol) {
            score += 6
            reasons.add("RVOL ${String.format(Locale.US, "%.1fx", stock.rvol)}")
        } else if (stock.rvol >= 1.2) {
            score += 3
        }

        val htfAligned = if (bias == OptionSide.CE) {
            (stock.rsi1h ?: 0.0) >= 55.0 && (stock.rsi15m ?: 0.0) >= 55.0
        } else {
            (stock.rsi1h ?: 100.0) <= 45.0 && (stock.rsi15m ?: 100.0) <= 45.0
        }
        val singleHtfAligned = if (bias == OptionSide.CE) {
            (stock.rsi1h ?: 0.0) >= 55.0 || (stock.rsi15m ?: 0.0) >= 55.0
        } else {
            (stock.rsi1h ?: 100.0) <= 45.0 || (stock.rsi15m ?: 100.0) <= 45.0
        }
        if (htfAligned) {
            score += 6
            reasons.add("1h & 15m RSI trend aligned")
        } else if (singleHtfAligned) {
            score += 3
        }

        val earlyTriggerRsi = if (bias == OptionSide.CE) {
            (stock.rsi5m ?: 0.0) >= 60.0 || (stock.rsi1m ?: 0.0) >= 65.0
        } else {
            (stock.rsi5m ?: 100.0) <= 40.0 || (stock.rsi1m ?: 100.0) <= 35.0
        }
        if (earlyTriggerRsi) {
            score += 3
        }

        // 5. F&O PCR Option Support (max 10 pts)
        val pcr = oi.pcr
        val pcrChg = oi.pcrOiChangePct ?: 0.0
        val pcrAligned = if (bias == OptionSide.CE) {
            (pcr != null && pcr >= 1.0) || pcrChg >= 2.0
        } else {
            (pcr != null && pcr in 0.01..0.78) || pcrChg <= -2.0
        }
        if (pcrAligned) {
            score += 10
            pcr?.let { reasons.add("PCR ${String.format(Locale.US, "%.2f", it)}") }
        } else if (pcr != null && ((bias == OptionSide.CE && pcr >= 0.85) || (bias == OptionSide.PE && pcr <= 0.92))) {
            score += 5
        }

        val ema20Summary = evaluatePositionalEma20(stock, bias)
        when {
            ema20Summary.hasFreshCross && ema20Summary.isAllAligned -> {
                score = (score + 12).coerceAtMost(100)
                reasons.add(0, ema20Summary.signalLabel)
            }
            ema20Summary.hasFreshCross -> {
                score = (score + 10).coerceAtMost(100)
                reasons.add(0, ema20Summary.signalLabel)
            }
            ema20Summary.isAllAligned -> {
                score = (score + 8).coerceAtMost(100)
                reasons.add(0, ema20Summary.signalLabel)
            }
        }

        // 6. 20-Day Volume SMA Surge (>= 2.0x) or Early Intraday Pace (>= 2.0x)
        if (stock.isVolumeSurge2x && stock.volumeSurgeRatio != null) {
            score = (score + 10).coerceAtMost(100)
            reasons.add(0, "Vol ${String.format(Locale.US, "%.1fx", stock.volumeSurgeRatio)} 20D SMA")
        } else if (stock.isEarlyVolumePace2x && stock.volumePaceRatio != null && stock.rvol >= 1.5) {
            score = (score + 5).coerceAtMost(100)
            reasons.add("Early Pace ${String.format(Locale.US, "%.1fx", stock.volumePaceRatio)} 20D")
        }

        val volRsiSignal = evaluateVol2xRsi65(stock)
        if (volRsiSignal.matched && volRsiSignal.volumeRatio != null) {
            score = (score + 8).coerceAtMost(100)
            reasons.add(
                0,
                "🔊 BUZZ Vol ${String.format(Locale.US, "%.1fx", volRsiSignal.volumeRatio)} + ${volRsiSignal.rsiAbove65Count}/${volRsiSignal.totalRsiCount} RSI >65",
            )
        }

        val finalScore = score.coerceIn(0, 100)

        // Filter out completely inactive flat symbols when NSE data is absent
        val hasActivity = finalScore >= 24 ||
            volRsiSignal.matched ||
            stock.isVolumeSurge2x ||
            ema20Summary.hasFreshCross ||
            ema20Summary.isAllAligned ||
            oi.buildupQuadrant != FnoBuildupQuadrant.NEUTRAL ||
            stock.rvol >= 1.1 ||
            abs(stock.changePercent) >= 0.75
        if (!hasActivity) return null

        val hasHtfEmaCross = ema20Summary.freshCrossLabels.any { it in setOf("1h", "30m", "15m") }
        val stage = when {
            isExtendedZone -> PositionalStage.EXTENDED_TRAIL
            oi.buildupQuadrant == FnoBuildupQuadrant.SHORT_COVERING -> PositionalStage.EXTENDED_TRAIL
            oi.buildupQuadrant == FnoBuildupQuadrant.LONG_UNWINDING -> PositionalStage.EXTENDED_TRAIL
            isEarlyZone && hasOiData &&
                ((bias == OptionSide.CE && oi.buildupQuadrant == FnoBuildupQuadrant.LONG_BUILDUP) ||
                    (bias == OptionSide.PE && oi.buildupQuadrant == FnoBuildupQuadrant.SHORT_BUILDUP)) ->
                PositionalStage.EARLY_IGNITION
            isEarlyZone && (hasHtfEmaCross || stock.isVolumeSurge2x || volRsiSignal.matched) && stock.rvol >= 1.1 ->
                PositionalStage.EARLY_IGNITION
            isEarlyZone && !hasOiData && stock.rvol >= 1.25 && abs(stock.changePercent) >= 0.8 && htfAligned ->
                PositionalStage.EARLY_IGNITION
            finalScore >= 52 -> PositionalStage.CONFIRMED_HOLD
            else -> PositionalStage.EXTENDED_TRAIL
        }

        if (!hasOiData && warningNote == null) {
            warningNote = "Sync NSE OI & Delivery to confirm institutional buildup"
        }

        val nextTargetLabel = when {
            isExtendedZone && bias == OptionSide.CE -> "Trail > R3"
            isExtendedZone && bias == OptionSide.PE -> "Trail < S3"
            nextTargetKey != null -> "Target $nextTargetKey"
            else -> "Awaiting Pivots"
        }

        val rsiEntrySummary = evaluatePositionalRsi(stock, bias)
        if (rsiEntrySummary.isEntryReady) {
            reasons.add(rsiEntrySummary.entrySignalLabel)
        }

        val validBackedUpDays = oi.history5Days.count { it.openInterest > 0L || it.deliveryPct > 0.0 }
        val trendHoldRule = buildTrendHoldGuidance(bias, stage, pivots)

        return PositionalCandidate(
            stock = stock,
            bias = bias,
            stage = stage,
            convictionScore = finalScore,
            pivotZoneLabel = pivotZoneLabel,
            nextTargetLabel = nextTargetLabel,
            nextTargetSpotPrice = nextTargetSpotPrice,
            nextTargetAtmOptionPrice = targetProjection?.projectedAtmOptionPrice,
            deliveryAvg5dPct = avg5dDeliv,
            deliveryRising = deliveryRising,
            hasOiData = hasOiData,
            backedUpDaysCount = validBackedUpDays,
            hasFiveDayBackup = validBackedUpDays >= 5,
            rsiEntrySummary = rsiEntrySummary,
            ema20Summary = ema20Summary,
            reasons = reasons.ifEmpty {
                listOf(
                    "Chg ${TechnicalIndicators.formatPercent(stock.changePercent)}",
                    "RVOL ${String.format(Locale.US, "%.1fx", stock.rvol)}",
                )
            },
            warningNote = warningNote,
            trendHoldRule = trendHoldRule,
            rsiAbove65Count = volRsiSignal.rsiAbove65Count,
            hasVol2xAndRsi65 = volRsiSignal.matched,
        )
    }

    private fun buildTrendHoldGuidance(
        bias: OptionSide,
        stage: PositionalStage,
        pivots: com.example.domain.model.PivotLevels?,
    ): String {
        return if (bias == OptionSide.CE) {
            val pivotStop = pivots?.let { " or Pivot PP ₹${TechnicalIndicators.formatPrice(it.pp)}" }.orEmpty()
            when (stage) {
                PositionalStage.EARLY_IGNITION, PositionalStage.CONFIRMED_HOLD ->
                    "Hold CE while 15m/1h closes stay ABOVE EMA 20$pivotStop; exit if OI flips to Long Unwinding"
                PositionalStage.EXTENDED_TRAIL ->
                    "Trail partial CE below 5m/15m EMA 20; avoid fresh size while extended/Short Covering"
            }
        } else {
            val pivotStop = pivots?.let { " or Pivot PP ₹${TechnicalIndicators.formatPrice(it.pp)}" }.orEmpty()
            when (stage) {
                PositionalStage.EARLY_IGNITION, PositionalStage.CONFIRMED_HOLD ->
                    "Hold PE while 15m/1h closes stay BELOW EMA 20$pivotStop; exit if OI flips to Short Covering"
                PositionalStage.EXTENDED_TRAIL ->
                    "Trail partial PE above 5m/15m EMA 20; avoid fresh size while extended/Long Unwinding"
            }
        }
    }

    fun evaluatePositionalEma20(
        stock: StockScanResult,
        bias: OptionSide,
    ): PositionalEma20Summary {
        val tfBars = listOf(
            "1h" to stock.candles1h,
            "30m" to stock.candles30m,
            "15m" to stock.candles15m,
            "5m" to stock.candles5m,
            "1m" to stock.candles1m,
        )
        val slots = tfBars.map { (tf, bars) ->
            val status = TechnicalIndicators.evaluateEmaCross(
                bars = bars,
                timeframeLabel = tf,
                period = 20,
                lookbackBars = 2,
            )
            if (status == null) {
                PositionalEma20Slot(
                    label = tf,
                    emaValue = null,
                    distancePct = null,
                    alignedWithBias = false,
                    freshCrossForBias = false,
                )
            } else {
                val aligned = if (bias == OptionSide.CE) status.isAboveEma else status.isBelowEma
                val freshCross = status.freshCrossDirection == bias
                PositionalEma20Slot(
                    label = tf,
                    emaValue = status.emaValue,
                    distancePct = status.distancePct,
                    alignedWithBias = aligned,
                    freshCrossForBias = freshCross,
                )
            }
        }

        val available = slots.count { it.emaValue != null }
        val aligned = slots.count { it.alignedWithBias }
        val freshTfs = slots.filter { it.freshCrossForBias }.map { it.label }
        val hasFresh = freshTfs.isNotEmpty()
        val dirArrow = if (bias == OptionSide.CE) "↑" else "↓"
        val dirWord = if (bias == OptionSide.CE) "ABOVE" else "BELOW"
        val signal = when {
            hasFresh && available > 0 && aligned == available ->
                "⚡ ${freshTfs.joinToString("/")} CROSS $dirArrow • $aligned/$available $dirWord"
            hasFresh && available > 0 ->
                "⚡ ${freshTfs.joinToString("/")} CROSS $dirArrow ($aligned/$available $dirWord)"
            hasFresh ->
                "⚡ ${freshTfs.joinToString("/")} EMA20 CROSS $dirArrow"
            available > 0 && aligned == available -> "ALL $aligned/$available $dirWord EMA20"
            available > 0 -> "$aligned/$available $dirWord EMA20"
            else -> "Awaiting 20 bars"
        }

        return PositionalEma20Summary(
            slots = slots,
            alignedCount = aligned,
            totalCount = available,
            freshCrossLabels = freshTfs,
            hasFreshCross = hasFresh,
            signalLabel = signal,
        )
    }

    fun evaluatePositionalRsi(
        stock: StockScanResult,
        bias: OptionSide,
    ): PositionalRsiSummary {
        val raw = listOf(
            "1h" to stock.rsi1h,
            "30m" to stock.rsi30m,
            "15m" to stock.rsi15m,
            "5m" to stock.rsi5m,
            "1m" to stock.rsi1m,
        )
        val slots = raw.map { (tf, valOpt) ->
            val passed = valOpt != null && !valOpt.isNaN() && if (bias == OptionSide.CE) {
                valOpt > POSITIONAL_CE_RSI_ENTRY
            } else {
                valOpt < POSITIONAL_PE_RSI_ENTRY
            }
            PositionalRsiSlot(label = tf, value = valOpt, passed = passed)
        }
        val available = slots.count { it.value != null && !it.value.isNaN() }
        val aligned = slots.count { it.passed }
        val ready = available >= 4 && aligned == available
        val threshLabel = if (bias == OptionSide.CE) ">65" else "<35"
        val signal = when {
            ready -> "TAKE ${bias.name} POSITION ($aligned/$available $threshLabel)"
            aligned >= 3 -> "PREPARE ENTRY ($aligned/$available $threshLabel)"
            else -> "WAIT RSI ($aligned/$available $threshLabel)"
        }
        return PositionalRsiSummary(
            slots = slots,
            alignedCount = aligned,
            totalCount = available,
            isEntryReady = ready,
            entrySignalLabel = signal,
        )
    }

    fun evaluateVol2xRsi65(stock: StockScanResult): PositionalVolRsiSignal {
        val ratio = stock.volumeSurgeRatio
        val hasVolGt2x = (ratio != null && ratio > 2.0) || stock.isVolumeSurge2x
        val tfValues = listOf(
            "1h" to stock.rsi1h,
            "30m" to stock.rsi30m,
            "15m" to stock.rsi15m,
            "5m" to stock.rsi5m,
            "1m" to stock.rsi1m,
        )
        val availableCount = tfValues.count { (_, v) -> v != null && !v.isNaN() }
        val matchedTfs = tfValues.filter { (_, v) -> v != null && !v.isNaN() && v > POSITIONAL_CE_RSI_ENTRY }
            .map { it.first }
        val hasHtfAnchor = matchedTfs.any { it == "1h" || it == "30m" || it == "15m" }
        val requiredCount = if (availableCount >= 3) 3 else availableCount.coerceAtLeast(2)
        val matched = hasVolGt2x && availableCount >= 2 && matchedTfs.size >= requiredCount && hasHtfAnchor
        return PositionalVolRsiSignal(
            matched = matched,
            volumeRatio = ratio,
            rsiAbove65Count = matchedTfs.size,
            totalRsiCount = availableCount,
            matchedTfLabels = matchedTfs,
        )
    }

    private const val POSITIONAL_CE_RSI_ENTRY = 65.0
    private const val POSITIONAL_PE_RSI_ENTRY = 35.0

    private data class PivotZoneResolution(
        val zoneLabel: String,
        val nextTargetKey: String?,
        val isEarlyZone: Boolean,
        val isExtendedZone: Boolean,
    )

    private fun resolvePivotZone(
        spot: Double,
        pivots: com.example.domain.model.PivotLevels?,
        bias: OptionSide,
    ): PivotZoneResolution {
        if (pivots == null || spot <= 0.0) {
            return PivotZoneResolution("PP Watch", null, isEarlyZone = false, isExtendedZone = false)
        }
        return if (bias == OptionSide.CE) {
            when {
                spot >= pivots.r3 -> PivotZoneResolution("> R3 Extended", "R3", isEarlyZone = false, isExtendedZone = true)
                spot >= pivots.r2 -> PivotZoneResolution("R2 → R3", "R3", isEarlyZone = false, isExtendedZone = false)
                spot >= pivots.r1 -> PivotZoneResolution("R1 → R2", "R2", isEarlyZone = true, isExtendedZone = false)
                spot >= pivots.pp -> PivotZoneResolution("PP → R1", "R1", isEarlyZone = true, isExtendedZone = false)
                else -> PivotZoneResolution("< PP (Base)", "PP", isEarlyZone = false, isExtendedZone = false)
            }
        } else {
            when {
                spot <= pivots.s3 -> PivotZoneResolution("< S3 Extended", "S3", isEarlyZone = false, isExtendedZone = true)
                spot <= pivots.s2 -> PivotZoneResolution("S2 → S3", "S3", isEarlyZone = false, isExtendedZone = false)
                spot <= pivots.s1 -> PivotZoneResolution("S1 → S2", "S2", isEarlyZone = true, isExtendedZone = false)
                spot <= pivots.pp -> PivotZoneResolution("PP → S1", "S1", isEarlyZone = true, isExtendedZone = false)
                else -> PivotZoneResolution("> PP (Base)", "PP", isEarlyZone = false, isExtendedZone = false)
            }
        }
    }
}
