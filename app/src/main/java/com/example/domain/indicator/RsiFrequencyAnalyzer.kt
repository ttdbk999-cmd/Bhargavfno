package com.example.domain.indicator

import com.example.domain.model.CandleBar
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.OptionSide
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.StockScanResult
import kotlin.math.abs

enum class TrendPersistence(val label: String, val rank: Int) {
    SUSTAINED("SUSTAINED", 3),
    BUILDING("BUILDING", 2),
    FLEETING("FLEETING", 1),
}

data class RsiTriggerEvent(
    val symbol: String,
    val side: OptionSide,
    val timeframe: ChartTimeframe,
    val timestampEpochMs: Long,
    val rsiValue: Double,
)

data class Rsi60mBucket(
    val index: Int,
    val triggerCount: Int,
    val hasExtremeDwell: Boolean,
    val dominantSide: OptionSide?,
)

data class RsiFrequencyStat(
    val symbol: String,
    val companyName: String,
    val lastPrice: Double,
    val changePercent: Double,
    val dominantSide: OptionSide,
    val ceTriggers60m: Int,
    val peTriggers60m: Int,
    val totalTriggers60m: Int,
    val extremeDwellMinutes1m: Int,
    val latestRsi1m: Double?,
    val latestRsi5m: Double?,
    val timeframeBreakdown: Map<ChartTimeframe, Int>,
    val persistence: TrendPersistence,
    val buckets12: List<Rsi60mBucket>,
    val lastTriggerEpochMs: Long?,
) {
    val activeBucketCount: Int
        get() = buckets12.count { it.triggerCount > 0 || it.hasExtremeDwell }
}

/**
 * Measures the rolling 60-minute frequency of RSI 70/30 threshold triggers across 1m, 5m, and 15m
 * bars (plus any live scan-loop triggers) to distinguish sustained momentum from fleeting spikes.
 */
object RsiFrequencyAnalyzer {

    const val WINDOW_MS = 60L * 60L * 1000L
    const val BUCKET_COUNT = 12 // 12 x 5-minute buckets = 60 minutes
    const val BUCKET_MS = WINDOW_MS / BUCKET_COUNT

    fun analyze(
        stocks: List<StockScanResult>,
        liveEvents: List<RsiTriggerEvent> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis(),
        maxResults: Int = 10,
    ): List<RsiFrequencyStat> {
        if (stocks.isEmpty()) return emptyList()

        val liveBySymbol = liveEvents
            .filter { (nowEpochMs - it.timestampEpochMs) in 0L..WINDOW_MS }
            .groupBy { it.symbol }

        return stocks.mapNotNull { stock ->
            analyzeStock(
                stock = stock,
                liveSymbolEvents = liveBySymbol[stock.symbol].orEmpty(),
                fallbackNowMs = nowEpochMs,
            )
        }.sortedWith(
            compareByDescending<RsiFrequencyStat> { it.totalTriggers60m }
                .thenByDescending { it.persistence.rank }
                .thenByDescending { it.extremeDwellMinutes1m }
                .thenByDescending { abs(it.changePercent) }
        ).take(maxResults)
    }

    fun analyzeStock(
        stock: StockScanResult,
        liveSymbolEvents: List<RsiTriggerEvent> = emptyList(),
        fallbackNowMs: Long = System.currentTimeMillis(),
    ): RsiFrequencyStat? {
        val anchorMs = listOfNotNull(
            stock.candles1m.lastOrNull()?.timestampSec?.times(1000L),
            stock.candles5m.lastOrNull()?.timestampSec?.times(1000L),
            stock.candles15m.lastOrNull()?.timestampSec?.times(1000L),
            liveSymbolEvents.maxOfOrNull { it.timestampEpochMs },
        ).maxOrNull() ?: fallbackNowMs

        val windowStartMs = anchorMs - WINDOW_MS

        val events = mutableListOf<RsiTriggerEvent>()
        events += extractCrossesFromBars(stock.symbol, ChartTimeframe.M1, stock.candles1m, stock.rsi1mSeries, windowStartMs)
        events += extractCrossesFromBars(stock.symbol, ChartTimeframe.M5, stock.candles5m, stock.rsi5mSeries, windowStartMs)
        events += extractCrossesFromBars(stock.symbol, ChartTimeframe.M15, stock.candles15m, stock.rsi15mSeries, windowStartMs)

        // Merge live Intra-scan events avoiding duplicate minute+timeframe+side records
        val seenKeys = events.mapTo(mutableSetOf()) {
            Triple(it.timeframe, it.side, it.timestampEpochMs / 60_000L)
        }
        liveSymbolEvents.forEach { ev ->
            if (ev.timestampEpochMs in windowStartMs..anchorMs) {
                val key = Triple(ev.timeframe, ev.side, ev.timestampEpochMs / 60_000L)
                if (seenKeys.add(key)) {
                    events += ev
                }
            }
        }

        val ceCount = events.count { it.side == OptionSide.CE }
        val peCount = events.count { it.side == OptionSide.PE }
        val totalTriggers = events.size

        // Measure 1m dwell inside >70 (CE) or <30 (PE) over the last 60m
        val recent1mPairs = pairCandlesWithRsi(stock.candles1m, stock.rsi1mSeries, windowStartMs)
        val ceDwell1m = recent1mPairs.count { (_, rsi) -> rsi > RsiCrossoverDetector.CE_RSI_CROSS_LEVEL }
        val peDwell1m = recent1mPairs.count { (_, rsi) -> rsi < RsiCrossoverDetector.PE_RSI_CROSS_LEVEL }

        val dominantSide = when {
            ceCount > peCount -> OptionSide.CE
            peCount > ceCount -> OptionSide.PE
            ceDwell1m > peDwell1m -> OptionSide.CE
            peDwell1m > ceDwell1m -> OptionSide.PE
            stock.changePercent >= 0.0 -> OptionSide.CE
            else -> OptionSide.PE
        }

        val dominantDwell1m = if (dominantSide == OptionSide.CE) ceDwell1m else peDwell1m

        if (totalTriggers == 0 && dominantDwell1m < 3) {
            return null
        }

        val timeframeBreakdown = ChartTimeframe.entries
            .associateWith { tf -> events.count { it.timeframe == tf } }
            .filterValues { it > 0 }

        val buckets = buildBuckets(
            windowStartMs = windowStartMs,
            events = events,
            recent1mPairs = recent1mPairs,
        )

        val activeBuckets = buckets.count { it.triggerCount > 0 || it.hasExtremeDwell }
        val distinctTfs = timeframeBreakdown.size

        val persistence = when {
            (totalTriggers >= 3 && (dominantDwell1m >= 10 || activeBuckets >= 3 || distinctTfs >= 2)) ||
                (dominantDwell1m >= 20 && totalTriggers >= 1) -> TrendPersistence.SUSTAINED
            totalTriggers >= 2 || (totalTriggers >= 1 && (dominantDwell1m >= 5 || activeBuckets >= 2)) -> TrendPersistence.BUILDING
            else -> TrendPersistence.FLEETING
        }

        return RsiFrequencyStat(
            symbol = stock.symbol,
            companyName = stock.companyName,
            lastPrice = stock.lastPrice,
            changePercent = stock.changePercent,
            dominantSide = dominantSide,
            ceTriggers60m = ceCount,
            peTriggers60m = peCount,
            totalTriggers60m = totalTriggers,
            extremeDwellMinutes1m = dominantDwell1m,
            latestRsi1m = stock.rsi1m,
            latestRsi5m = stock.rsi5m,
            timeframeBreakdown = timeframeBreakdown,
            persistence = persistence,
            buckets12 = buckets,
            lastTriggerEpochMs = events.maxOfOrNull { it.timestampEpochMs },
        )
    }

    fun fromAlert(alert: RsiCrossAlert): RsiTriggerEvent = RsiTriggerEvent(
        symbol = alert.symbol,
        side = alert.side,
        timeframe = alert.timeframe,
        timestampEpochMs = alert.crossedAtEpochMs,
        rsiValue = alert.rsiValue,
    )

    private fun extractCrossesFromBars(
        symbol: String,
        timeframe: ChartTimeframe,
        candles: List<CandleBar>,
        rsiSeries: List<Double?>,
        windowStartMs: Long,
    ): List<RsiTriggerEvent> {
        val count = minOf(candles.size, rsiSeries.size)
        if (count < 2) return emptyList()

        val result = mutableListOf<RsiTriggerEvent>()
        for (i in 1 until count) {
            val prevRsi = rsiSeries[i - 1] ?: continue
            val currRsi = rsiSeries[i] ?: continue
            val tsMs = candles[i].timestampSec * 1000L
            if (tsMs < windowStartMs) continue

            if (prevRsi <= RsiCrossoverDetector.CE_RSI_CROSS_LEVEL && currRsi > RsiCrossoverDetector.CE_RSI_CROSS_LEVEL) {
                result += RsiTriggerEvent(
                    symbol = symbol,
                    side = OptionSide.CE,
                    timeframe = timeframe,
                    timestampEpochMs = tsMs,
                    rsiValue = currRsi,
                )
            }
            if (prevRsi >= RsiCrossoverDetector.PE_RSI_CROSS_LEVEL && currRsi < RsiCrossoverDetector.PE_RSI_CROSS_LEVEL) {
                result += RsiTriggerEvent(
                    symbol = symbol,
                    side = OptionSide.PE,
                    timeframe = timeframe,
                    timestampEpochMs = tsMs,
                    rsiValue = currRsi,
                )
            }
        }
        return result
    }

    private fun pairCandlesWithRsi(
        candles: List<CandleBar>,
        rsiSeries: List<Double?>,
        windowStartMs: Long,
    ): List<Pair<Long, Double>> {
        val count = minOf(candles.size, rsiSeries.size)
        if (count == 0) return emptyList()
        val pairs = ArrayList<Pair<Long, Double>>(64)
        for (i in 0 until count) {
            val rsi = rsiSeries[i] ?: continue
            val tsMs = candles[i].timestampSec * 1000L
            if (tsMs >= windowStartMs) {
                pairs.add(tsMs to rsi)
            }
        }
        return pairs
    }

    private fun buildBuckets(
        windowStartMs: Long,
        events: List<RsiTriggerEvent>,
        recent1mPairs: List<Pair<Long, Double>>,
    ): List<Rsi60mBucket> {
        return (0 until BUCKET_COUNT).map { idx ->
            val start = windowStartMs + idx * BUCKET_MS
            val end = if (idx == BUCKET_COUNT - 1) Long.MAX_VALUE else start + BUCKET_MS
            val bucketEvents = events.filter { it.timestampEpochMs in start until end }
            val bucketDwells = recent1mPairs.filter { (ts, _) -> ts in start until end }
            val ceDwell = bucketDwells.any { (_, rsi) -> rsi > RsiCrossoverDetector.CE_RSI_CROSS_LEVEL }
            val peDwell = bucketDwells.any { (_, rsi) -> rsi < RsiCrossoverDetector.PE_RSI_CROSS_LEVEL }
            val ceHits = bucketEvents.count { it.side == OptionSide.CE }
            val peHits = bucketEvents.count { it.side == OptionSide.PE }
            val side = when {
                ceHits > peHits -> OptionSide.CE
                peHits > ceHits -> OptionSide.PE
                ceDwell && !peDwell -> OptionSide.CE
                peDwell && !ceDwell -> OptionSide.PE
                else -> null
            }
            Rsi60mBucket(
                index = idx,
                triggerCount = bucketEvents.size,
                hasExtremeDwell = ceDwell || peDwell,
                dominantSide = side,
            )
        }
    }
}
