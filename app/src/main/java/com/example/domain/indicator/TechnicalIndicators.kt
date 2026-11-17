package com.example.domain.indicator

import com.example.domain.model.CandleBar
import com.example.domain.model.OptionSide
import com.example.domain.model.PivotLevels
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

object TechnicalIndicators {

    private val istZone: ZoneId = ZoneId.of("Asia/Kolkata")
    private const val NSE_OPEN_MINUTE_OF_DAY = 9 * 60 + 15 // 09:15 IST

    /**
     * Exact Wilder's Smoothing RSI(14) matching TradingView's ta.rsi(close, 14).
     * Returns a list aligned 1:1 with [closes], with null entries until enough warmup bars exist.
     */
    fun calculateRsiSeries(closes: List<Double>, period: Int = 14): List<Double?> {
        if (closes.isEmpty()) return emptyList()
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size <= period) return result

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0.0) gainSum += diff else lossSum += abs(diff)
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        result[period] = rsiFromAverages(avgGain, avgLoss)

        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0.0) diff else 0.0
            val loss = if (diff < 0.0) -diff else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            result[i] = rsiFromAverages(avgGain, avgLoss)
        }
        return result
    }

    fun latestRsi(bars: List<CandleBar>, period: Int = 14): Double? {
        if (bars.size <= period) return null
        return calculateRsiSeries(bars.map { it.close }, period).lastOrNull { it != null }
    }

    private fun rsiFromAverages(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0 && avgGain == 0.0) return 50.0
        if (avgLoss == 0.0) return 100.0
        if (avgGain == 0.0) return 0.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    data class EmaCrossStatus(
        val timeframeLabel: String,
        val emaValue: Double,
        val lastClose: Double,
        val distancePct: Double,
        val isAboveEma: Boolean,
        val freshCrossDirection: OptionSide?,
        val crossBarsAgo: Int?,
    ) {
        val isBelowEma: Boolean
            get() = lastClose < emaValue
    }

    /**
     * Exact TradingView Exponential Moving Average ta.ema(close, period),
     * seeded with the SMA of the first [period] closes.
     */
    fun calculateEmaSeries(closes: List<Double>, period: Int = 20): List<Double?> {
        if (closes.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size < period) return result

        var sum = 0.0
        for (i in 0 until period) {
            sum += closes[i]
        }
        var ema = sum / period
        result[period - 1] = ema

        val multiplier = 2.0 / (period + 1.0)
        for (i in period until closes.size) {
            ema = (closes[i] - ema) * multiplier + ema
            result[i] = ema
        }
        return result
    }

    /**
     * Evaluates price position relative to EMA(period) and detects fresh upward (CE)
     * or downward (PE) crossovers within the last [lookbackBars] bars.
     */
    fun evaluateEmaCross(
        bars: List<CandleBar>,
        timeframeLabel: String,
        period: Int = 20,
        lookbackBars: Int = 2,
    ): EmaCrossStatus? {
        if (bars.size < period + 1) return null
        val closes = bars.map { it.close }
        val emaSeries = calculateEmaSeries(closes, period)
        val latestIdx = closes.lastIndex
        val latestEma = emaSeries[latestIdx] ?: return null
        if (latestEma <= 0.0) return null
        val latestClose = closes[latestIdx]
        val isAbove = latestClose > latestEma
        val distPct = ((latestClose - latestEma) / latestEma) * 100.0

        var crossDir: OptionSide? = null
        var barsAgo: Int? = null
        val maxOffset = lookbackBars.coerceAtLeast(1)
        for (offset in 1..maxOffset) {
            val prevIdx = latestIdx - offset
            if (prevIdx < 0) break
            val prevEma = emaSeries[prevIdx] ?: continue
            val prevClose = closes[prevIdx]
            if (isAbove && prevClose <= prevEma) {
                crossDir = OptionSide.CE
                barsAgo = offset - 1
                break
            } else if (!isAbove && latestClose < latestEma && prevClose >= prevEma) {
                crossDir = OptionSide.PE
                barsAgo = offset - 1
                break
            }
        }

        return EmaCrossStatus(
            timeframeLabel = timeframeLabel,
            emaValue = latestEma,
            lastClose = latestClose,
            distancePct = distPct,
            isAboveEma = isAbove,
            freshCrossDirection = crossDir,
            crossBarsAgo = barsAgo,
        )
    }

    /**
     * Resamples 5m NSE bars into 15m or 60m bars aligned to NSE 09:15 IST session open.
     */
    fun resampleNseCandles(bars5m: List<CandleBar>, bucketMinutes: Int): List<CandleBar> {
        if (bars5m.isEmpty()) return emptyList()
        if (bucketMinutes <= 5) return bars5m

        val grouped = LinkedHashMap<Long, MutableList<CandleBar>>()
        for (bar in bars5m) {
            val zoned = Instant.ofEpochSecond(bar.timestampSec).atZone(istZone)
            val date = zoned.toLocalDate()
            val minuteOfDay = zoned.hour * 60 + zoned.minute
            val minutesFromOpen = (minuteOfDay - NSE_OPEN_MINUTE_OF_DAY).coerceAtLeast(0)
            val bucketIndex = minutesFromOpen / bucketMinutes
            val bucketStartMinute = NSE_OPEN_MINUTE_OF_DAY + bucketIndex * bucketMinutes
            val bucketHour = (bucketStartMinute / 60).coerceIn(0, 23)
            val bucketMin = bucketStartMinute % 60
            val bucketEpoch = date.atTime(bucketHour, bucketMin).atZone(istZone).toEpochSecond()
            grouped.getOrPut(bucketEpoch) { mutableListOf() }.add(bar)
        }

        return grouped.entries.map { (bucketTs, list) ->
            CandleBar(
                timestampSec = bucketTs,
                open = list.first().open,
                high = list.maxOf { it.high },
                low = list.minOf { it.low },
                close = list.last().close,
                volume = list.sumOf { it.volume },
            )
        }
    }

    /**
     * Merges cached 1-month 60m historical candles with live resampled 60m candles from the 5m feed
     * so 1h RSI(14) has deep Wilder warmup AND updates live every 10 seconds.
     */
    fun mergeHourlyCandles(
        historical1h: List<CandleBar>,
        recent1hFrom5m: List<CandleBar>,
    ): List<CandleBar> {
        if (historical1h.isEmpty()) return recent1hFrom5m
        if (recent1hFrom5m.isEmpty()) return historical1h

        val map = LinkedHashMap<Long, CandleBar>(historical1h.size + recent1hFrom5m.size)
        for (bar in historical1h) {
            map[bar.timestampSec] = bar
        }
        for (bar in recent1hFrom5m) {
            map[bar.timestampSec] = bar
        }
        return map.values.sortedBy { it.timestampSec }
    }

    /**
     * Computes Relative Volume (RVOL) by comparing today's cumulative intraday volume up to the
     * latest bar's IST time-of-day against the average cumulative volume at the same time-of-day
     * across prior trading sessions in the 5-day 5m window.
     */
    fun calculateSessionRvol(bars5m: List<CandleBar>, reportedSessionVolume: Long): Double {
        if (bars5m.isEmpty()) return 0.0

        val byDate = LinkedHashMap<LocalDate, MutableList<Pair<Int, Long>>>()
        for (bar in bars5m) {
            val zoned = Instant.ofEpochSecond(bar.timestampSec).atZone(istZone)
            val date = zoned.toLocalDate()
            val minuteOfDay = zoned.hour * 60 + zoned.minute
            byDate.getOrPut(date) { mutableListOf() }.add(minuteOfDay to bar.volume)
        }

        val dates = byDate.keys.sorted()
        if (dates.size >= 2) {
            val latestDate = dates.last()
            val latestDayBars = byDate[latestDate].orEmpty()
            val cutoffMinute = latestDayBars.maxOfOrNull { it.first } ?: (15 * 60 + 30)
            val todayBarSum = latestDayBars.sumOf { it.second }
            val currentCumVol = maxOf(todayBarSum, reportedSessionVolume).toDouble()

            val priorSameTimeTotals = dates.dropLast(1).mapNotNull { d ->
                val dayBars = byDate[d].orEmpty()
                val sameTimeSum = dayBars.filter { it.first <= cutoffMinute }.sumOf { it.second }
                if (sameTimeSum > 0L) sameTimeSum.toDouble() else null
            }

            if (priorSameTimeTotals.isNotEmpty()) {
                val avgPrior = priorSameTimeTotals.average()
                if (avgPrior > 0.0) {
                    return (currentCumVol / avgPrior).coerceIn(0.0, 25.0)
                }
            }
        }

        // Fallback: latest non-zero 5m volume vs 20-bar average
        val nonZeroVolumes = bars5m.map { it.volume }.filter { it > 0L }
        if (nonZeroVolumes.size >= 2) {
            val latestVol = nonZeroVolumes.last().toDouble()
            val baseline = nonZeroVolumes.dropLast(1).takeLast(20).average()
            if (baseline > 0.0) return (latestVol / baseline).coerceIn(0.0, 25.0)
        }
        return 1.0
    }

    data class VolumeSurgeMetrics(
        val avgVolume20d: Double,
        val currentVolume: Long,
        val surgeRatio: Double,
        val paceSurgeRatio: Double?,
        val priorDaysUsed: Int,
        val isVolumeSurge2x: Boolean,
        val isEarlyPaceSurge2x: Boolean,
    )

    /**
     * Computes 20-day Simple Moving Average of completed daily trading volumes (excluding today's
     * still-forming session bar in IST) and detects when today's cumulative volume reaches >= 2.0x
     * the 20-day average. Also computes an intraday pace-adjusted ratio for early detection.
     */
    fun calculateVolumeSma20Surge(
        dailyBars: List<CandleBar>,
        currentSessionVolume: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
        bars5m: List<CandleBar> = emptyList(),
        lookbackDays: Int = 20,
        surgeMultiplier: Double = 2.0,
    ): VolumeSurgeMetrics? {
        if (dailyBars.isEmpty() || currentSessionVolume <= 0L) return null
        val todayIst = Instant.ofEpochMilli(nowEpochMs).atZone(istZone).toLocalDate()
        val latestIntradayDate = bars5m.lastOrNull()?.let {
            Instant.ofEpochSecond(it.timestampSec).atZone(istZone).toLocalDate()
        }
        val activeDate = if (latestIntradayDate != null && !latestIntradayDate.isBefore(todayIst)) {
            latestIntradayDate
        } else {
            todayIst
        }

        val completedPriorDaily = dailyBars
            .filter { bar ->
                bar.volume > 0L &&
                    Instant.ofEpochSecond(bar.timestampSec).atZone(istZone).toLocalDate().isBefore(activeDate)
            }
            .sortedBy { it.timestampSec }
            .takeLast(lookbackDays)

        val effectivePrior = if (completedPriorDaily.isNotEmpty()) {
            completedPriorDaily
        } else {
            dailyBars.filter { it.volume > 0L }.dropLast(1).sortedBy { it.timestampSec }.takeLast(lookbackDays)
        }
        if (effectivePrior.size < 5) return null

        val avg20d = effectivePrior.map { it.volume.toDouble() }.average()
        if (avg20d <= 0.0) return null

        val ratio = currentSessionVolume.toDouble() / avg20d

        // Compute projected full-session ratio when intraday market hours (09:15-15:30 IST = 375m) are active
        val latestBarZoned = bars5m.lastOrNull()?.let {
            Instant.ofEpochSecond(it.timestampSec).atZone(istZone)
        } ?: Instant.ofEpochMilli(nowEpochMs).atZone(istZone)
        val minuteOfDay = latestBarZoned.hour * 60 + latestBarZoned.minute
        val nseCloseMinute = 15 * 60 + 30
        val elapsedMinutes = (minuteOfDay - NSE_OPEN_MINUTE_OF_DAY).coerceIn(15, nseCloseMinute - NSE_OPEN_MINUTE_OF_DAY)
        val totalSessionMinutes = (nseCloseMinute - NSE_OPEN_MINUTE_OF_DAY).toDouble()
        val paceRatio = if (minuteOfDay in NSE_OPEN_MINUTE_OF_DAY until nseCloseMinute) {
            (ratio / (elapsedMinutes.toDouble() / totalSessionMinutes)).coerceIn(0.0, 50.0)
        } else {
            ratio
        }

        return VolumeSurgeMetrics(
            avgVolume20d = avg20d,
            currentVolume = currentSessionVolume,
            surgeRatio = ratio,
            paceSurgeRatio = paceRatio,
            priorDaysUsed = effectivePrior.size,
            isVolumeSurge2x = ratio >= surgeMultiplier,
            isEarlyPaceSurge2x = paceRatio >= surgeMultiplier,
        )
    }

    fun formatPrice(price: Double): String {
        return when {
            price >= 10000.0 -> String.format(Locale.US, "%,.0f", price)
            price >= 1000.0 -> String.format(Locale.US, "%,.1f", price)
            else -> String.format(Locale.US, "%,.2f", price)
        }
    }

    fun formatChange(change: Double): String {
        val sign = if (change > 0.0) "+" else ""
        return when {
            abs(change) >= 1000.0 -> sign + String.format(Locale.US, "%,.0f", change)
            abs(change) >= 100.0 -> sign + String.format(Locale.US, "%.1f", change)
            else -> sign + String.format(Locale.US, "%.2f", change)
        }
    }

    fun formatPercent(pct: Double): String {
        val sign = if (pct > 0.0) "+" else ""
        return sign + String.format(Locale.US, "%.2f%%", pct)
    }

    /**
     * Computes Standard Floor Pivots (S1, S2, S3, PP, R1, R2, R3) from the most recent
     * completed prior trading session's High, Low, and Close in [bars5m].
     */
    fun calculateStandardPivotsFromBars(bars5m: List<CandleBar>): PivotLevels? {
        if (bars5m.isEmpty()) return null
        val byDate = LinkedHashMap<LocalDate, MutableList<CandleBar>>()
        for (bar in bars5m) {
            val date = Instant.ofEpochSecond(bar.timestampSec).atZone(istZone).toLocalDate()
            byDate.getOrPut(date) { mutableListOf() }.add(bar)
        }
        val dates = byDate.keys.sorted()
        if (dates.size < 2) return null
        val prevDayBars = byDate[dates[dates.size - 2]].orEmpty()
        if (prevDayBars.isEmpty()) return null

        val high = prevDayBars.maxOf { it.high }
        val low = prevDayBars.minOf { it.low }
        val close = prevDayBars.last().close
        return calculateStandardPivots(high = high, low = low, close = close)
    }

    fun calculateStandardPivots(high: Double, low: Double, close: Double): PivotLevels? {
        if (high <= 0.0 || low <= 0.0 || close <= 0.0 || high < low) return null
        val pp = (high + low + close) / 3.0
        val range = high - low
        val r1 = (2.0 * pp) - low
        val s1 = (2.0 * pp) - high
        val r2 = pp + range
        val s2 = pp - range
        val r3 = high + 2.0 * (pp - low)
        val s3 = low - 2.0 * (high - pp)
        return PivotLevels(
            prevHigh = high,
            prevLow = low,
            prevClose = close,
            pp = pp,
            r1 = r1,
            r2 = r2,
            r3 = r3,
            s1 = s1,
            s2 = s2,
            s3 = s3,
        )
    }

    fun formatPivotZone(pivots: PivotLevels, spot: Double, side: OptionSide): String {
        return if (side == OptionSide.CE) {
            when {
                spot >= pivots.r3 -> "Above R3 ₹${formatPrice(pivots.r3)}"
                spot >= pivots.r2 -> "Broke R2 → R3 ₹${formatPrice(pivots.r3)}"
                spot >= pivots.r1 -> "Broke R1 → R2 ₹${formatPrice(pivots.r2)}"
                spot >= pivots.pp -> "Above PP → R1 ₹${formatPrice(pivots.r1)}"
                else -> "Below PP → R1 ₹${formatPrice(pivots.r1)}"
            }
        } else {
            when {
                spot <= pivots.s3 -> "Below S3 ₹${formatPrice(pivots.s3)}"
                spot <= pivots.s2 -> "Broke S2 → S3 ₹${formatPrice(pivots.s3)}"
                spot <= pivots.s1 -> "Broke S1 → S2 ₹${formatPrice(pivots.s2)}"
                spot <= pivots.pp -> "Below PP → S1 ₹${formatPrice(pivots.s1)}"
                else -> "Above PP → S1 ₹${formatPrice(pivots.s1)}"
            }
        }
    }

    fun formatVolumeCompact(volume: Long): String {
        return when {
            volume >= 1_000_000L -> String.format(Locale.US, "%.2f M", volume / 1_000_000.0)
            volume >= 1_000L -> String.format(Locale.US, "%.2f K", volume / 1_000.0)
            else -> volume.toString()
        }
    }
}
