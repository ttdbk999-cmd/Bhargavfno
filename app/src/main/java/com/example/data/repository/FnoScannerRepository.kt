package com.example.data.repository

import com.example.data.remote.ChartResult
import com.example.data.remote.YahooFinanceApi
import com.example.data.universe.FnoUniverse
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.CandleBar
import com.example.domain.model.FnoTickerMeta
import com.example.domain.model.StockScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed class ScanBatchOutcome {
    data class Success(
        val results: List<StockScanResult>,
        val failedSymbolsCount: Int,
        val fetchedAtEpochMs: Long,
    ) : ScanBatchOutcome()

    data class Failure(val message: String) : ScanBatchOutcome()
}

class FnoScannerRepository(
    private val api: YahooFinanceApi,
) {
    private val istZone = ZoneId.of("Asia/Kolkata")

    // Cache 1-month 60m historical candles for Wilder 1h RSI warmup (refreshed every ~15s)
    private val hourlyCache = ConcurrentHashMap<String, CachedHourly>()
    // Cache 3-month 1d historical candles for 20-day Volume SMA calculation (refreshed every ~5m)
    private val dailyCache = ConcurrentHashMap<String, CachedDaily>()
    private val lastPrices = ConcurrentHashMap<String, Double>()

    private data class CachedHourly(
        val bars: List<CandleBar>,
        val fetchedAtMs: Long,
    )

    private data class CachedDaily(
        val bars: List<CandleBar>,
        val fetchedAtMs: Long,
    )

    suspend fun scanUniverse(
        symbols: List<String>,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): ScanBatchOutcome = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) {
            return@withContext ScanBatchOutcome.Success(emptyList(), 0, System.currentTimeMillis())
        }

        val total = symbols.size
        val completedCounter = AtomicInteger(0)
        val failedCounter = AtomicInteger(0)
        val semaphore = Semaphore(8)
        val nowMs = System.currentTimeMillis()

        val scannedList = coroutineScope {
            symbols.map { rawSym ->
                async {
                    semaphore.withPermit {
                        val meta = FnoUniverse.resolveMeta(rawSym)
                        val result = runCatching { fetchAndComputeStock(meta, nowMs) }
                            .getOrElse {
                                failedCounter.incrementAndGet()
                                null
                            }
                        val done = completedCounter.incrementAndGet()
                        onProgress(done, total)
                        result
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (scannedList.isEmpty() && failedCounter.get() == total) {
            return@withContext ScanBatchOutcome.Failure(
                "Unable to reach live NSE market feed. Check internet connectivity and retry."
            )
        }

        ScanBatchOutcome.Success(
            results = scannedList,
            failedSymbolsCount = failedCounter.get(),
            fetchedAtEpochMs = System.currentTimeMillis(),
        )
    }

    suspend fun validateAndFetchSymbol(rawSymbol: String): Result<StockScanResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val meta = FnoUniverse.resolveMeta(rawSymbol)
                fetchAndComputeStock(meta, System.currentTimeMillis())
                    ?: error("No intraday NSE chart data found for ${meta.symbol}")
            }
        }

    private suspend fun fetchAndComputeStock(
        meta: FnoTickerMeta,
        nowMs: Long,
    ): StockScanResult? {
        val ticker = meta.yahooTicker

        // Fetch 1h (1mo), 1d (3mo cached 5m), 5m (5d), and 1m (5d) bars concurrently when cache misses occur
        val cached1h = hourlyCache[ticker]
        val cached1d = dailyCache[ticker]
        val (historical1h, historical1d, resp5m, resp1m) = coroutineScope {
            val d1h = async {
                if (cached1h == null || (nowMs - cached1h.fetchedAtMs) > 15_000L) {
                    runCatching {
                        val resp1h = api.getChart(symbol = ticker, interval = "60m", range = "1mo")
                        val bars1h = parseCandles(resp1h.chart?.result?.firstOrNull())
                        if (bars1h.isNotEmpty()) {
                            hourlyCache[ticker] = CachedHourly(bars1h, nowMs)
                        }
                        bars1h
                    }.getOrElse { cached1h?.bars.orEmpty() }
                } else {
                    cached1h.bars
                }
            }
            val d1d = async {
                if (cached1d == null || (nowMs - cached1d.fetchedAtMs) > 300_000L) {
                    runCatching {
                        val resp1d = api.getChart(symbol = ticker, interval = "1d", range = "3mo")
                        val bars1d = parseCandles(resp1d.chart?.result?.firstOrNull())
                        if (bars1d.isNotEmpty()) {
                            dailyCache[ticker] = CachedDaily(bars1d, nowMs)
                        }
                        bars1d
                    }.getOrElse { cached1d?.bars.orEmpty() }
                } else {
                    cached1d.bars
                }
            }
            val d5m = async { api.getChart(symbol = ticker, interval = "5m", range = "5d") }
            val d1m = async {
                runCatching { api.getChart(symbol = ticker, interval = "1m", range = "5d") }.getOrNull()
            }
            Quadruple(d1h.await(), d1d.await(), d5m.await(), d1m.await())
        }
        val chartResult = resp5m.chart?.result?.firstOrNull() ?: return null
        val bars5m = parseCandles(chartResult)
        if (bars5m.isEmpty()) return null

        val chartResult1m = resp1m?.chart?.result?.firstOrNull()
        val bars1m = parseCandles(chartResult1m)

        val chartMeta = chartResult.meta
        val lastPrice = chartResult1m?.meta?.regularMarketPrice?.takeIf { it > 0.0 }
            ?: chartMeta?.regularMarketPrice?.takeIf { it > 0.0 }
            ?: bars1m.lastOrNull()?.close
            ?: bars5m.last().close

        // Determine accurate previous trading session close
        val previousClose = resolvePreviousSessionClose(bars5m, chartMeta?.previousClose, chartMeta?.chartPreviousClose, lastPrice)
        val change = lastPrice - previousClose
        val changePct = if (previousClose > 0.0) (change / previousClose) * 100.0 else 0.0

        // Session volume (Shares) - take max of reported volume, 5m sum, and 1m sum to avoid opening bucket lag
        val latestSessionBarsSum5m = latestSessionVolumeSum(bars5m)
        val latestSessionBarsSum1m = latestSessionVolumeSum(bars1m)
        val reportedSessionVol = chartMeta?.regularMarketVolume ?: 0L
        val sessionShares = maxOf(reportedSessionVol, latestSessionBarsSum5m, latestSessionBarsSum1m)
        val latest5mVol = bars5m.lastOrNull { it.volume > 0L }?.volume ?: 0L

        // Resample 15m, 30m & 1h NSE candles aligned to 09:15 IST
        val bars15m = TechnicalIndicators.resampleNseCandles(bars5m, bucketMinutes = 15)
        val bars30m = TechnicalIndicators.resampleNseCandles(bars5m, bucketMinutes = 30)
        val recent1hFrom5m = TechnicalIndicators.resampleNseCandles(bars5m, bucketMinutes = 60)
        val bars1h = TechnicalIndicators.mergeHourlyCandles(historical1h, recent1hFrom5m)

        // Compute Wilder RSI(14) on 1m, 5m, 15m, 30m, and 1h
        val rsi1mSeries = TechnicalIndicators.calculateRsiSeries(bars1m.map { it.close }, period = 14)
        val rsi5mSeries = TechnicalIndicators.calculateRsiSeries(bars5m.map { it.close }, period = 14)
        val rsi15mSeries = TechnicalIndicators.calculateRsiSeries(bars15m.map { it.close }, period = 14)
        val rsi30mSeries = TechnicalIndicators.calculateRsiSeries(bars30m.map { it.close }, period = 14)
        val rsi1hSeries = TechnicalIndicators.calculateRsiSeries(bars1h.map { it.close }, period = 14)

        val rsi1m = rsi1mSeries.lastOrNull { it != null }
        val rsi5m = rsi5mSeries.lastOrNull { it != null }
        val rsi15m = rsi15mSeries.lastOrNull { it != null }
        val rsi30m = rsi30mSeries.lastOrNull { it != null }
        val rsi1h = rsi1hSeries.lastOrNull { it != null }

        // Compute time-matched session RVOL, 20-day Volume SMA surge (>= 2x), & Standard Floor Pivots
        val rvol = TechnicalIndicators.calculateSessionRvol(bars5m, sessionShares)
        val volumeSurge = TechnicalIndicators.calculateVolumeSma20Surge(
            dailyBars = historical1d,
            currentSessionVolume = sessionShares,
            nowEpochMs = nowMs,
            bars5m = bars5m,
        )
        val pivotLevels = TechnicalIndicators.calculateStandardPivotsFromBars(bars5m)

        val prevScanPrice = lastPrices.put(meta.symbol, lastPrice)
        val flashDir = when {
            prevScanPrice == null -> 0
            lastPrice > prevScanPrice -> 1
            lastPrice < prevScanPrice -> -1
            else -> 0
        }

        val resolvedCompany = chartMeta?.shortName?.takeIf { it.isNotBlank() } ?: meta.companyName

        return StockScanResult(
            symbol = meta.symbol,
            yahooTicker = ticker,
            companyName = resolvedCompany,
            sector = meta.sector,
            badgeColorHex = meta.badgeColorHex,
            lastPrice = lastPrice,
            previousClose = previousClose,
            change = change,
            changePercent = changePct,
            sharesVolume = sessionShares,
            latest5mVolume = latest5mVol,
            rvol = rvol,
            rsi1h = rsi1h,
            rsi30m = rsi30m,
            rsi15m = rsi15m,
            rsi5m = rsi5m,
            candles5m = bars5m,
            candles15m = bars15m,
            candles30m = bars30m,
            candles1h = bars1h,
            rsi5mSeries = rsi5mSeries,
            rsi15mSeries = rsi15mSeries,
            rsi30mSeries = rsi30mSeries,
            rsi1hSeries = rsi1hSeries,
            pivotLevels = pivotLevels,
            priceFlashDirection = flashDir,
            lastUpdatedEpochMs = nowMs,
            rsi1m = rsi1m,
            candles1m = bars1m,
            rsi1mSeries = rsi1mSeries,
            volumeSma20d = volumeSurge?.avgVolume20d,
            volumeSurgeRatio = volumeSurge?.surgeRatio,
            volumePaceRatio = volumeSurge?.paceSurgeRatio,
            isVolumeSurge2x = volumeSurge?.isVolumeSurge2x == true,
            isEarlyVolumePace2x = volumeSurge?.isEarlyPaceSurge2x == true,
            volumeSmaDaysUsed = volumeSurge?.priorDaysUsed ?: 0,
        )
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private fun parseCandles(result: ChartResult?): List<CandleBar> {
        if (result == null) return emptyList()
        val timestamps = result.timestamp ?: return emptyList()
        val quote = result.indicators?.quote?.firstOrNull() ?: return emptyList()
        val opens = quote.open.orEmpty()
        val highs = quote.high.orEmpty()
        val lows = quote.low.orEmpty()
        val closes = quote.close.orEmpty()
        val volumes = quote.volume.orEmpty()

        val bars = ArrayList<CandleBar>(timestamps.size)
        for (i in timestamps.indices) {
            val close = closes.getOrNull(i) ?: continue
            val open = opens.getOrNull(i) ?: close
            val high = highs.getOrNull(i) ?: maxOf(open, close)
            val low = lows.getOrNull(i) ?: minOf(open, close)
            val vol = volumes.getOrNull(i) ?: 0L
            if (close > 0.0) {
                bars.add(
                    CandleBar(
                        timestampSec = timestamps[i],
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = vol.coerceAtLeast(0L),
                    )
                )
            }
        }
        return bars
    }

    private fun resolvePreviousSessionClose(
        bars5m: List<CandleBar>,
        metaPrevClose: Double?,
        metaChartPrevClose: Double?,
        fallbackLast: Double,
    ): Double {
        if (metaPrevClose != null && metaPrevClose > 0.0) return metaPrevClose

        // Compute previous trading date's final close directly from the 5m bars
        val latestDate = Instant.ofEpochSecond(bars5m.last().timestampSec).atZone(istZone).toLocalDate()
        val priorBar = bars5m.lastOrNull { bar ->
            Instant.ofEpochSecond(bar.timestampSec).atZone(istZone).toLocalDate().isBefore(latestDate)
        }
        if (priorBar != null && priorBar.close > 0.0) return priorBar.close

        if (metaChartPrevClose != null && metaChartPrevClose > 0.0) return metaChartPrevClose
        return fallbackLast
    }

    private fun latestSessionVolumeSum(bars5m: List<CandleBar>): Long {
        if (bars5m.isEmpty()) return 0L
        val latestDate = Instant.ofEpochSecond(bars5m.last().timestampSec).atZone(istZone).toLocalDate()
        return bars5m.asSequence()
            .filter { Instant.ofEpochSecond(it.timestampSec).atZone(istZone).toLocalDate() == latestDate }
            .sumOf { it.volume }
    }
}
