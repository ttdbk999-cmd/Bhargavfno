package com.example.domain.model

import kotlin.math.roundToLong

enum class OptionSide(val label: String) {
    CE("CE Calls ↑"),
    PE("PE Puts ↓"),
}

enum class TrendDirection(val shortLabel: String) {
    STRONG_BULLISH("BULL ++"),
    BULLISH("BULL"),
    NEUTRAL("FLAT"),
    BEARISH("BEAR"),
    STRONG_BEARISH("BEAR --"),
}

enum class BoardViewMode(val label: String) {
    DUAL_SPLIT("Gapboard CE | PE"),
    CE_ONLY("CE Calls ↑"),
    PE_ONLY("PE Puts ↓"),
}

enum class BreakoutFilterMode(val label: String) {
    ALL("All Pivots"),
    R2_R3("R2 & R3"),
    ABOVE_R3("> R3 Only"),
    S2_S3("S2 & S3"),
    BELOW_S3("< S3 Only"),
}

enum class PivotWatchLevel(val shortLabel: String, val side: OptionSide, val rank: Int) {
    ABOVE_R3("R3", OptionSide.CE, 3),
    ABOVE_R2("R2", OptionSide.CE, 2),
    ABOVE_R1("R1", OptionSide.CE, 1),
    BELOW_S3("S3", OptionSide.PE, 3),
    BELOW_S2("S2", OptionSide.PE, 2),
    BELOW_S1("S1", OptionSide.PE, 1),
}

enum class AlertCategory(val label: String) {
    PIVOT_BREAKOUT("PIVOT"),
    RSI_CROSS("RSI"),
    VOLUME_SURGE("VOL 2X"),
    POSITIONAL_VOL_RSI("POS VOL>2+RSI>65"),
}

data class LiveScannerAlert(
    val id: String,
    val symbol: String,
    val side: OptionSide,
    val category: AlertCategory,
    val title: String,
    val detailText: String,
    val level: PivotWatchLevel? = null,
    val timeframe: ChartTimeframe? = null,
    val triggeredAtEpochMs: Long,
    val triggeredAtTimeIst: String,
    val isPriorityWatch: Boolean = true,
    val isNew: Boolean = false,
)

enum class SortColumn(val label: String) {
    SYMBOL("Symbol"),
    LAST("Last"),
    CHG("Chg"),
    CHG_PCT("Chg%"),
    VOL("Vol"),
    RVOL("RVOL"),
    RSI_5M("5m RSI"),
}

enum class ChartTimeframe(val label: String, val minutes: Int) {
    M1("1m", 1),
    M5("5m", 5),
    M15("15m", 15),
    M30("30m", 30),
    H1("1h", 60),
}

data class RsiCrossAlert(
    val id: String,
    val symbol: String,
    val side: OptionSide,
    val timeframe: ChartTimeframe,
    val rsiValue: Double,
    val crossedAtEpochMs: Long,
    val crossedAtTimeIst: String,
    val lastPrice: Double,
    val changePercent: Double,
    val isActiveExtreme: Boolean = true,
)

enum class FnoBuildupQuadrant(val label: String) {
    LONG_BUILDUP("Long Buildup"),
    SHORT_COVERING("Short Covering"),
    SHORT_BUILDUP("Short Buildup"),
    LONG_UNWINDING("Long Unwinding"),
    NEUTRAL("Neutral"),
}

enum class PositionAction(val badgeLabel: String) {
    STRONG_HOLD("STRONG HOLD"),
    HOLD("HOLD"),
    TRAIL_PARTIAL("TRAIL / BOOK"),
    SELL_EXIT("SELL / EXIT"),
    WATCH("WATCH"),
}

data class DailyPositionSnapshot(
    val tradeDate: String,
    val closePrice: Double,
    val priceChangePct: Double,
    val tradedQty: Long,
    val openInterest: Long,
    val oiChange: Long,
    val oiChangePct: Double,
    val deliveryQty: Long,
    val deliveryPct: Double,
    val deliveryQtyChangePctVsPrev: Double? = null,
    val deliveryPctChangeVsPrev: Double? = null,
)

data class OiDeliverySummary(
    val openInterest: Long? = null,
    val oiChange: Long? = null,
    val oiChangePct: Double? = null,
    val pcr: Double? = null,
    val callToPutRatio: Double? = null,
    val pcrOiChangePct: Double? = null,
    val putOiChange: Long? = null,
    val callOiChange: Long? = null,
    val intradayDeltaPcr: Double? = null,
    val deliveryQty: Long? = null,
    val deliveryPct: Double? = null,
    val deliveryQtyChangePct: Double? = null,
    val deliveryPctChangePp: Double? = null,
    val buildupQuadrant: FnoBuildupQuadrant = FnoBuildupQuadrant.NEUTRAL,
    val positionAction: PositionAction = PositionAction.WATCH,
    val positionHeadline: String = "Awaiting NSE OI/Delivery sync",
    val positionRationale: String = "Fetching 5-day OI and delivery data from NSE India",
    val history5Days: List<DailyPositionSnapshot> = emptyList(),
)

data class PivotLevels(
    val prevHigh: Double,
    val prevLow: Double,
    val prevClose: Double,
    val pp: Double,
    val r1: Double,
    val r2: Double,
    val r3: Double,
    val s1: Double,
    val s2: Double,
    val s3: Double,
)

data class OptionStrikeQuote(
    val symbol: String,
    val side: OptionSide,
    val strikePrice: Double,
    val expiryDate: String,
    val runningPrice: Double,
    val change: Double,
    val changePercent: Double,
    val openInterest: Long,
    val oiChange: Long,
    val tradedContracts: Long = 0L,
) {
    val formattedStrike: String
        get() = if (strikePrice % 1.0 == 0.0) {
            strikePrice.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", strikePrice)
        }

    val contractLabel: String
        get() = "$formattedStrike ${side.name}"
}

data class AtmOptionBundle(
    val expiryDate: String = "",
    val atmCe: OptionStrikeQuote? = null,
    val atmPe: OptionStrikeQuote? = null,
    val nearbyStrikesCe: List<OptionStrikeQuote> = emptyList(),
    val nearbyStrikesPe: List<OptionStrikeQuote> = emptyList(),
    val maxCallOiStrike: Double? = null,
    val maxCallOi: Long = 0L,
    val maxPutOiStrike: Double? = null,
    val maxPutOi: Long = 0L,
    val allStrikesCe: List<OptionStrikeQuote> = emptyList(),
    val allStrikesPe: List<OptionStrikeQuote> = emptyList(),
)

data class CandleBar(
    val timestampSec: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

data class FnoTickerMeta(
    val symbol: String,
    val companyName: String,
    val sector: String,
    val badgeColorHex: Long,
    val yahooOverride: String? = null,
) {
    val yahooTicker: String
        get() = yahooOverride ?: "$symbol.NS"
}

data class ScannerThresholds(
    val minRvol: Double = 1.5,
    val minShares: Long = 50_000L,
    val minRsi1h: Double = 60.0,
    val minRsi15m: Double = 60.0,
    val minRsi5m: Double = 65.0,
    val minRsi1m: Double = 70.0,
    val require1mAlignment: Boolean = true,
    val mirrorPeRsi: Boolean = true,
    val maxPeRsi1h: Double = 40.0,
    val maxPeRsi15m: Double = 40.0,
    val maxPeRsi5m: Double = 35.0,
    val maxPeRsi1m: Double = 30.0,
    val refreshIntervalSec: Int = 10,
    val autoRefreshEnabled: Boolean = true,
    val soundAlertsEnabled: Boolean = true,
    val soundOn5of5Match: Boolean = true,
) {
    val effectivePeRsi1h: Double
        get() = if (mirrorPeRsi) (100.0 - minRsi1h).coerceIn(5.0, 95.0) else maxPeRsi1h

    val effectivePeRsi15m: Double
        get() = if (mirrorPeRsi) (100.0 - minRsi15m).coerceIn(5.0, 95.0) else maxPeRsi15m

    val effectivePeRsi5m: Double
        get() = if (mirrorPeRsi) (100.0 - minRsi5m).coerceIn(5.0, 95.0) else maxPeRsi5m

    val effectivePeRsi1m: Double
        get() = if (mirrorPeRsi) (100.0 - minRsi1m).coerceIn(5.0, 95.0) else maxPeRsi1m
}

data class CriteriaEvaluation(
    val rvolPass: Boolean,
    val sharesPass: Boolean,
    val rsi1hPass: Boolean,
    val rsi15mPass: Boolean,
    val rsi5mPass: Boolean,
    val rsi1mPass: Boolean = true,
) {
    val passedCount: Int
        get() = listOf(rvolPass, sharesPass, rsi1hPass, rsi15mPass, rsi5mPass).count { it }

    val allPassed: Boolean
        get() = passedCount == 5 && rsi1mPass
}

data class StockScanResult(
    val symbol: String,
    val yahooTicker: String,
    val companyName: String,
    val sector: String,
    val badgeColorHex: Long,
    val lastPrice: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val sharesVolume: Long,
    val latest5mVolume: Long,
    val rvol: Double,
    val rsi1h: Double?,
    val rsi30m: Double?,
    val rsi15m: Double?,
    val rsi5m: Double?,
    val candles5m: List<CandleBar>,
    val candles15m: List<CandleBar>,
    val candles30m: List<CandleBar>,
    val candles1h: List<CandleBar>,
    val rsi5mSeries: List<Double?>,
    val rsi15mSeries: List<Double?>,
    val rsi30mSeries: List<Double?>,
    val rsi1hSeries: List<Double?>,
    val highPriorityCrosses: List<RsiCrossAlert> = emptyList(),
    val oiDelivery: OiDeliverySummary = OiDeliverySummary(),
    val atmOptions: AtmOptionBundle = AtmOptionBundle(),
    val pivotLevels: PivotLevels? = null,
    val priceFlashDirection: Int = 0, // +1 up, -1 down, 0 unchanged
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
    val rsi1m: Double? = null,
    val candles1m: List<CandleBar> = emptyList(),
    val rsi1mSeries: List<Double?> = emptyList(),
    val volumeSma20d: Double? = null,
    val volumeSurgeRatio: Double? = null,
    val volumePaceRatio: Double? = null,
    val isVolumeSurge2x: Boolean = false,
    val isEarlyVolumePace2x: Boolean = false,
    val volumeSmaDaysUsed: Int = 0,
) {
    val isGapUp: Boolean get() = changePercent >= 0.0
    val isGapDown: Boolean get() = changePercent < 0.0
    val isAboveR1: Boolean get() = pivotLevels != null && lastPrice >= pivotLevels.r1
    val isAboveR2: Boolean get() = pivotLevels != null && lastPrice >= pivotLevels.r2
    val isAboveR3: Boolean get() = pivotLevels != null && lastPrice >= pivotLevels.r3
    val isBelowS1: Boolean get() = pivotLevels != null && lastPrice <= pivotLevels.s1
    val isBelowS2: Boolean get() = pivotLevels != null && lastPrice <= pivotLevels.s2
    val isBelowS3: Boolean get() = pivotLevels != null && lastPrice <= pivotLevels.s3
    val isR2OrR3Watch: Boolean get() = isAboveR2
    val isS2OrS3Watch: Boolean get() = isBelowS2

    fun currentCePivotLevel(): PivotWatchLevel? = when {
        isAboveR3 -> PivotWatchLevel.ABOVE_R3
        isAboveR2 -> PivotWatchLevel.ABOVE_R2
        isAboveR1 -> PivotWatchLevel.ABOVE_R1
        else -> null
    }

    fun currentPePivotLevel(): PivotWatchLevel? = when {
        isBelowS3 -> PivotWatchLevel.BELOW_S3
        isBelowS2 -> PivotWatchLevel.BELOW_S2
        isBelowS1 -> PivotWatchLevel.BELOW_S1
        else -> null
    }

    fun percentAboveR2(): Double? {
        val r2 = pivotLevels?.r2 ?: return null
        if (r2 <= 0.0 || lastPrice < r2) return null
        return ((lastPrice - r2) / r2) * 100.0
    }

    fun percentBelowS2(): Double? {
        val s2 = pivotLevels?.s2 ?: return null
        if (s2 <= 0.0 || lastPrice > s2) return null
        return ((s2 - lastPrice) / s2) * 100.0
    }

    fun percentAboveR3(): Double? {
        val r3 = pivotLevels?.r3 ?: return null
        if (r3 <= 0.0 || lastPrice < r3) return null
        return ((lastPrice - r3) / r3) * 100.0
    }

    fun percentBelowS3(): Double? {
        val s3 = pivotLevels?.s3 ?: return null
        if (s3 <= 0.0 || lastPrice > s3) return null
        return ((s3 - lastPrice) / s3) * 100.0
    }

    fun atmOptionFor(side: OptionSide): OptionStrikeQuote? {
        return if (side == OptionSide.CE) atmOptions.atmCe else atmOptions.atmPe
    }

    fun nearbyOptionsFor(side: OptionSide): List<OptionStrikeQuote> {
        return if (side == OptionSide.CE) atmOptions.nearbyStrikesCe else atmOptions.nearbyStrikesPe
    }

    fun computedAtmStrike(): Double {
        val step = when {
            lastPrice < 100.0 -> 2.5
            lastPrice < 300.0 -> 5.0
            lastPrice < 750.0 -> 10.0
            lastPrice < 1500.0 -> 20.0
            lastPrice < 3500.0 -> 50.0
            lastPrice < 7500.0 -> 100.0
            else -> 200.0
        }
        return ((lastPrice / step).roundToLong() * step)
    }

    fun formattedComputedAtmStrike(side: OptionSide): String {
        val strike = atmOptionFor(side)?.strikePrice ?: computedAtmStrike()
        val strikeLabel = if (strike % 1.0 == 0.0) {
            strike.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", strike)
        }
        return "$strikeLabel ${side.name}"
    }

    fun activeCrossesFor(side: OptionSide): List<RsiCrossAlert> {
        return highPriorityCrosses.filter { it.side == side && it.isActiveExtreme }
    }

    fun isHighPriority(side: OptionSide): Boolean {
        return activeCrossesFor(side).isNotEmpty()
    }

    fun overallTrendDirection(t: ScannerThresholds): TrendDirection {
        val ceStrong = isHighPriority(OptionSide.CE) || evaluateCe(t).allPassed
        val peStrong = isHighPriority(OptionSide.PE) || evaluatePe(t).allPassed
        return when {
            ceStrong && !peStrong -> TrendDirection.STRONG_BULLISH
            peStrong && !ceStrong -> TrendDirection.STRONG_BEARISH
            changePercent > 0.10 || (changePercent >= 0.0 && (rsi5m ?: 50.0) >= 55.0) -> TrendDirection.BULLISH
            changePercent < -0.10 || (changePercent < 0.0 && (rsi5m ?: 50.0) <= 45.0) -> TrendDirection.BEARISH
            changePercent > 0.0 -> TrendDirection.BULLISH
            changePercent < 0.0 -> TrendDirection.BEARISH
            else -> TrendDirection.NEUTRAL
        }
    }

    fun is1mAligned(side: OptionSide, t: ScannerThresholds): Boolean {
        val v1m = rsi1m ?: return false
        return if (side == OptionSide.CE) {
            v1m > t.minRsi1m
        } else {
            v1m < t.effectivePeRsi1m
        }
    }

    fun is1mAnd5mAligned(side: OptionSide, t: ScannerThresholds): Boolean {
        if (!is1mAligned(side, t)) return false
        val v5m = rsi5m ?: return false
        return if (side == OptionSide.CE) {
            v5m > t.minRsi5m
        } else {
            v5m < t.effectivePeRsi5m
        }
    }

    fun passesLiquidityRule(t: ScannerThresholds): Boolean {
        if (sharesVolume > t.minShares) return true
        if (lastPrice >= 500.0 && t.minShares > 0L) {
            val turnoverInr = sharesVolume.toDouble() * lastPrice
            val minEquivalentTurnover = t.minShares.toDouble() * 500.0
            if (turnoverInr >= minEquivalentTurnover) return true
        }
        return false
    }

    fun evaluateCe(t: ScannerThresholds): CriteriaEvaluation = CriteriaEvaluation(
        rvolPass = rvol > t.minRvol,
        sharesPass = passesLiquidityRule(t),
        rsi1hPass = (rsi1h ?: Double.NaN) > t.minRsi1h,
        rsi15mPass = (rsi15m ?: Double.NaN) > t.minRsi15m,
        rsi5mPass = (rsi5m ?: Double.NaN) > t.minRsi5m,
        rsi1mPass = !t.require1mAlignment || rsi1m == null || rsi1m > t.minRsi1m,
    )

    fun evaluatePe(t: ScannerThresholds): CriteriaEvaluation = CriteriaEvaluation(
        rvolPass = rvol > t.minRvol,
        sharesPass = passesLiquidityRule(t),
        rsi1hPass = rsi1h != null && rsi1h < t.effectivePeRsi1h,
        rsi15mPass = rsi15m != null && rsi15m < t.effectivePeRsi15m,
        rsi5mPass = rsi5m != null && rsi5m < t.effectivePeRsi5m,
        rsi1mPass = !t.require1mAlignment || rsi1m == null || rsi1m < t.effectivePeRsi1m,
    )

    fun suggestedStrikeContract(side: OptionSide): String {
        val step = when {
            lastPrice < 100.0 -> 2.5
            lastPrice < 300.0 -> 5.0
            lastPrice < 750.0 -> 10.0
            lastPrice < 1500.0 -> 20.0
            lastPrice < 3500.0 -> 50.0
            lastPrice < 7500.0 -> 100.0
            else -> 200.0
        }
        val atmStrike = ((lastPrice / step).roundToLong() * step)
        val strikeLabel = if (atmStrike % 1.0 == 0.0) {
            atmStrike.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", atmStrike)
        }
        val suffix = if (side == OptionSide.CE) "CE" else "PE"
        return "$symbol $strikeLabel $suffix"
    }
}

data class ScanProgress(
    val completed: Int = 0,
    val total: Int = 0,
    val isScanning: Boolean = false,
)
