package com.example.data.repository

import com.example.data.local.DailyFnoMetricDao
import com.example.data.local.DailyFnoMetricEntity
import com.example.data.remote.LiveDerivativeParsed
import com.example.data.remote.NseBrowserClient
import com.example.data.remote.NseHistoricalEquityResponse
import com.example.data.remote.NseHistoricalEquityRow
import com.example.data.remote.NseHistoricalFoResponse
import com.example.data.remote.NseHistoricalFoRow
import com.example.data.remote.NseIndiaApi
import com.example.data.remote.NseNetworkModule
import com.example.data.remote.NseOptionChainParser
import com.example.domain.model.AtmOptionBundle
import com.example.domain.model.DailyPositionSnapshot
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class LiveOiDeliverySnapshot(
    val symbol: String,
    val openInterest: Long?,
    val oiChange: Long?,
    val oiChangePct: Double?,
    val pcr: Double?,
    val callToPutRatio: Double? = null,
    val pcrOiChangePct: Double? = null,
    val putOiChange: Long? = null,
    val callOiChange: Long? = null,
    val intradayDeltaPcr: Double? = null,
    val deliveryQty: Long?,
    val deliveryPct: Double?,
    val atmOptions: AtmOptionBundle = AtmOptionBundle(),
    val fetchedAtMs: Long,
)

class NseOiDeliveryRepository(
    private val nseApi: NseIndiaApi,
    private val dao: DailyFnoMetricDao,
    private val browserClient: NseBrowserClient? = null,
) {
    private val istZone = ZoneId.of("Asia/Kolkata")
    private val nseQueryDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    private val nseAltDateFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.US)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val cookieMutex = Mutex()
    private var lastCookieWarmupMs = 0L

    private val moshi = Moshi.Builder().build()
    private val genericMapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
    private val historicalEquityAdapter = moshi.adapter(NseHistoricalEquityResponse::class.java)
    private val historicalFoAdapter = moshi.adapter(NseHistoricalFoResponse::class.java)

    private val liveSnapshotCache = ConcurrentHashMap<String, LiveOiDeliverySnapshot>()
    private val historyFetchedAtMs = ConcurrentHashMap<String, Long>()
    private val syncStatusBySymbol = ConcurrentHashMap<String, String>()

    fun observeAllHistoryBySymbol(): Flow<Map<String, List<DailyPositionSnapshot>>> {
        return dao.observeAllHistory().map { entities ->
            entities
                .groupBy { it.symbol }
                .mapValues { (_, list) ->
                    list.sortedByDescending { it.tradeDate }.take(15).map { it.toDomain() }
                }
        }
    }

    fun getCachedLiveSnapshot(symbol: String): LiveOiDeliverySnapshot? = liveSnapshotCache[symbol]

    fun getSyncStatusMessage(symbol: String): String? = syncStatusBySymbol[symbol]

    /**
     * Fetches live OI + OI Change + Delivery Qty/Pct AND ensures at least 5 trading days
     * of historical OI & Delivery are cached in Room for [symbol].
     */
    suspend fun syncSymbol(
        symbol: String,
        lastPrice: Double,
        priceChangePct: Double,
        tradedQty: Long,
        forceHistory: Boolean = false,
        includeHistory: Boolean = true,
    ): LiveOiDeliverySnapshot? = withContext(Dispatchers.IO) {
        ensureNseSessionCookie(forceRefresh = forceHistory)

        // 1. Fetch live derivative OI + ATM Options & equity delivery concurrently
        val (liveDeriv, liveDelivPair) = kotlinx.coroutines.withTimeoutOrNull(if (includeHistory) 18_000L else 8_500L) {
            coroutineScope {
                val derivDeferred = async {
                    runCatching { fetchLiveDerivativeMetrics(symbol, lastPrice) }.getOrNull()
                }
                val delivDeferred = async {
                    runCatching { fetchLiveDeliveryMetrics(symbol) }.getOrNull()
                }
                derivDeferred.await() to delivDeferred.await()
            }
        } ?: (null to null)

        // 2. Fetch 5+ day historical OI & Delivery archives when requested and missing (< 5 backed-up days) or stale (> 15 min)
        if (includeHistory) {
            val existingHistory = dao.getSymbolHistoryOnce(symbol, limit = 15)
            val completeHistoryDays = existingHistory.count { it.openInterest > 0L || it.deliveryPct > 0.0 }
            val lastHistSync = historyFetchedAtMs[symbol] ?: 0L
            val shouldFetchHistory = forceHistory ||
                completeHistoryDays < 5 ||
                (System.currentTimeMillis() - lastHistSync) > 15 * 60_000L

            if (shouldFetchHistory) {
                runCatching {
                    fetchAndStoreHistorical5PlusDays(symbol, existingHistory)
                    historyFetchedAtMs[symbol] = System.currentTimeMillis()
                }
            }
        }

        val todayIso = LocalDate.now(istZone).format(isoDateFormatter)
        val existingSnap = liveSnapshotCache[symbol]
        val oi = liveDeriv?.futOi?.takeIf { it > 0L } ?: existingSnap?.openInterest
        val oiChg = liveDeriv?.futOiChange?.takeIf { liveDeriv.futOi > 0L } ?: existingSnap?.oiChange
        val oiChgPct = if ((liveDeriv?.futOi ?: 0L) > 0L) liveDeriv?.futOiChangePct else existingSnap?.oiChangePct
        val pcr = liveDeriv?.pcr ?: existingSnap?.pcr
        val callToPut = liveDeriv?.callToPutRatio ?: existingSnap?.callToPutRatio
        val pcrOiChgPct = liveDeriv?.pcrOiChangePct ?: existingSnap?.pcrOiChangePct
        val putOiChg = liveDeriv?.putOiChange ?: existingSnap?.putOiChange
        val callOiChg = liveDeriv?.callOiChange ?: existingSnap?.callOiChange
        val deltaPcr = liveDeriv?.intradayDeltaPcr ?: existingSnap?.intradayDeltaPcr
        val atmBundle = liveDeriv?.atmOptions?.takeIf {
            it.atmCe != null || it.atmPe != null || it.nearbyStrikesCe.isNotEmpty() || it.nearbyStrikesPe.isNotEmpty()
        } ?: existingSnap?.atmOptions ?: AtmOptionBundle()
        val delivQty = liveDelivPair?.first ?: existingSnap?.deliveryQty
        val delivPct = liveDelivPair?.second ?: existingSnap?.deliveryPct

        if (atmBundle.atmCe != null || atmBundle.atmPe != null) {
            syncStatusBySymbol[symbol] = if (atmBundle.expiryDate.isNotBlank()) {
                "Nearest Expiry: ${atmBundle.expiryDate} • Refreshes every 10s"
            } else {
                "Live NSE Option Chain synced • Refreshes every 10s"
            }
        } else if (liveDeriv == null) {
            syncStatusBySymbol[symbol] = "NSE option chain blocked or timed out — tap ↻ to retry"
        }

        val snapshot = LiveOiDeliverySnapshot(
            symbol = symbol,
            openInterest = oi,
            oiChange = oiChg,
            oiChangePct = oiChgPct,
            pcr = pcr,
            callToPutRatio = callToPut,
            pcrOiChangePct = pcrOiChgPct,
            putOiChange = putOiChg,
            callOiChange = callOiChg,
            intradayDeltaPcr = deltaPcr,
            deliveryQty = delivQty,
            deliveryPct = delivPct,
            atmOptions = atmBundle,
            fetchedAtMs = System.currentTimeMillis(),
        )

        if (oi != null || delivQty != null || pcr != null || atmBundle.atmCe != null || atmBundle.atmPe != null) {
            liveSnapshotCache[symbol] = snapshot
            val existingToday = dao.getSymbolHistoryOnce(symbol, limit = 15)
                .firstOrNull { it.tradeDate == todayIso }
            val mergedOi = oi ?: existingToday?.openInterest ?: 0L
            val mergedOiChg = if (oi != null) (oiChg ?: existingToday?.oiChange ?: 0L) else (existingToday?.oiChange ?: 0L)
            val mergedOiChgPct = if (oi != null) (oiChgPct ?: existingToday?.oiChangePct ?: 0.0) else (existingToday?.oiChangePct ?: 0.0)
            val mergedDelivQty = delivQty ?: existingToday?.deliveryQty ?: 0L
            val mergedDelivPct = delivPct ?: existingToday?.deliveryPct ?: 0.0

            val mergedToday = DailyFnoMetricEntity(
                symbol = symbol,
                tradeDate = todayIso,
                closePrice = if (lastPrice > 0.0) lastPrice else (existingToday?.closePrice ?: 0.0),
                priceChangePct = priceChangePct,
                tradedQty = if (tradedQty > 0L) tradedQty else (existingToday?.tradedQty ?: 0L),
                openInterest = mergedOi,
                oiChange = mergedOiChg,
                oiChangePct = mergedOiChgPct,
                deliveryQty = mergedDelivQty,
                deliveryPct = mergedDelivPct,
                updatedAtMs = System.currentTimeMillis(),
            )
            dao.upsertAll(listOf(mergedToday))
            if (includeHistory) {
                dao.pruneSymbolOlderThan(symbol, keepDays = 15)
            }
            return@withContext snapshot
        }

        null
    }

    /**
     * Fast 10-second live refresh for ATM Option running price & OI of a single focused stock.
     */
    suspend fun refreshLiveDerivativeOnly(
        symbol: String,
        spotPrice: Double,
    ): LiveOiDeliverySnapshot? = withContext(Dispatchers.IO) {
        ensureNseSessionCookie()
        val deriv = runCatching { fetchLiveDerivativeMetrics(symbol, spotPrice) }.getOrNull()
        if (deriv == null) {
            if (liveSnapshotCache[symbol]?.atmOptions?.atmCe == null) {
                syncStatusBySymbol[symbol] = "NSE option chain blocked or timed out — tap ↻ to retry"
            }
            return@withContext null
        }

        val prev = liveSnapshotCache[symbol]
        val futOi = deriv.futOi.takeIf { it > 0L } ?: prev?.openInterest
        val futOiChg = if (deriv.futOi > 0L) deriv.futOiChange else prev?.oiChange
        val futOiChgPct = if (deriv.futOi > 0L) deriv.futOiChangePct else prev?.oiChangePct

        if (deriv.atmOptions.atmCe != null || deriv.atmOptions.atmPe != null) {
            syncStatusBySymbol[symbol] = if (deriv.atmOptions.expiryDate.isNotBlank()) {
                "Nearest Expiry: ${deriv.atmOptions.expiryDate} • Refreshes every 10s"
            } else {
                "Live NSE Option Chain synced • Refreshes every 10s"
            }
        }

        val updated = LiveOiDeliverySnapshot(
            symbol = symbol,
            openInterest = futOi,
            oiChange = futOiChg,
            oiChangePct = futOiChgPct,
            pcr = deriv.pcr ?: prev?.pcr,
            callToPutRatio = deriv.callToPutRatio ?: prev?.callToPutRatio,
            pcrOiChangePct = deriv.pcrOiChangePct ?: prev?.pcrOiChangePct,
            putOiChange = deriv.putOiChange ?: prev?.putOiChange,
            callOiChange = deriv.callOiChange ?: prev?.callOiChange,
            intradayDeltaPcr = deriv.intradayDeltaPcr ?: prev?.intradayDeltaPcr,
            deliveryQty = prev?.deliveryQty,
            deliveryPct = prev?.deliveryPct,
            atmOptions = deriv.atmOptions,
            fetchedAtMs = System.currentTimeMillis(),
        )
        liveSnapshotCache[symbol] = updated
        updated
    }

    /**
     * Politely refreshes NSE live OI + Delivery + 5-day history across the watchlist
     * with a bounded semaphore so NSE session limits are respected.
     */
    suspend fun syncBatch(
        items: List<Triple<String, Double, Pair<Double, Long>>>,
        includeHistory: Boolean = true,
    ): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        ensureNseSessionCookie()
        val semaphore = Semaphore(if (includeHistory) 3 else 4)
        val successes = java.util.concurrent.atomic.AtomicInteger(0)
        coroutineScope {
            items.map { (symbol, lastPrice, chgAndVol) ->
                async {
                    semaphore.withPermit {
                        val snap = syncSymbol(
                            symbol = symbol,
                            lastPrice = lastPrice,
                            priceChangePct = chgAndVol.first,
                            tradedQty = chgAndVol.second,
                            forceHistory = false,
                            includeHistory = includeHistory,
                        )
                        if (snap != null) successes.incrementAndGet()
                    }
                }
            }.awaitAll()
        }
        successes.get()
    }

    /**
     * Lightweight 10-second live refresh for Positional setups (Futures OI, Delivery %, PCR & ATM Options)
     * without re-pulling multi-day historical archives on every tick.
     */
    suspend fun syncLiveOnlyBatch(
        items: List<Triple<String, Double, Pair<Double, Long>>>,
    ): Int = syncBatch(items = items, includeHistory = false)

    private suspend fun ensureNseSessionCookie(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && NseNetworkModule.cookieJar.hasCookies() && (now - lastCookieWarmupMs) < 4 * 60_000L) {
            return
        }
        cookieMutex.withLock {
            val recheck = System.currentTimeMillis()
            if (forceRefresh || !NseNetworkModule.cookieJar.hasCookies() || (recheck - lastCookieWarmupMs) >= 4 * 60_000L) {
                if (forceRefresh) {
                    NseNetworkModule.cookieJar.clear()
                }
                val homeOk = runCatching { nseApi.warmUpSession().isSuccessful }.getOrDefault(false)
                val chainOk = runCatching { nseApi.warmUpOptionChain().isSuccessful }.getOrDefault(false)
                if ((!homeOk && !chainOk) || !NseNetworkModule.cookieJar.hasCookies() || forceRefresh) {
                    browserClient?.warmUpAndSyncCookies(forceReload = forceRefresh)
                }
                lastCookieWarmupMs = System.currentTimeMillis()
            }
        }
    }

    private suspend fun executeNseJsonCall(
        webViewUrl: String,
        okHttpCall: suspend () -> Response<ResponseBody>,
    ): String? {
        var blockedByWaf = false
        val firstAttempt = runCatching { okHttpCall() }.getOrNull()
        if (firstAttempt != null) {
            val code = firstAttempt.code()
            val bodyStr = runCatching { firstAttempt.body()?.string() }.getOrNull()
            if (firstAttempt.isSuccessful && NseOptionChainParser.isLikelyJsonObject(bodyStr)) {
                return bodyStr
            }
            if (code == 401 || code == 403 || !NseOptionChainParser.isLikelyJsonObject(bodyStr)) {
                blockedByWaf = true
            }
        } else {
            blockedByWaf = true
        }

        // Fallback: Use Chromium WebView same-origin fetch() which carries genuine Chrome TLS + Akamai cookies
        if (blockedByWaf && browserClient != null) {
            val wvBody = browserClient.fetchJson(webViewUrl)
            if (NseOptionChainParser.isLikelyJsonObject(wvBody)) {
                return wvBody
            }
        }

        return null
    }

    private suspend fun fetchLiveDerivativeMetrics(
        symbol: String,
        fallbackSpotPrice: Double,
    ): LiveDerivativeParsed? = coroutineScope {
        val encodedSymbol = urlEncode(symbol)
        val isIndex = symbol.uppercase(Locale.US) in INDEX_SYMBOLS
        val ocType = if (isIndex) "Indices" else "Equity"

        // Fetch Futures OI (getSymbolDerivativesData / quote-derivative) concurrently with Option Chain,
        // because option-chain-v3 only contains Option strikes (futOi = 0L).
        val futDeferred = async {
            fetchFuturesDerivativeFallback(symbol, encodedSymbol, fallbackSpotPrice)
        }

        // Strategy 1: Modern NSE Option Chain v3 (contract-info -> nearest expiry -> option-chain-v3)
        val contractInfoJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/option-chain-contract-info?symbol=$encodedSymbol",
            okHttpCall = { nseApi.getOptionChainContractInfo(symbol) },
        )
        val nearestExpiry = contractInfoJson?.let { NseOptionChainParser.parseContractInfoNearestExpiry(it) }

        val v3Url = if (!nearestExpiry.isNullOrBlank()) {
            "https://www.nseindia.com/api/option-chain-v3?type=$ocType&symbol=$encodedSymbol&expiry=${urlEncode(nearestExpiry)}"
        } else {
            "https://www.nseindia.com/api/option-chain-v3?type=$ocType&symbol=$encodedSymbol"
        }
        val v3Json = executeNseJsonCall(
            webViewUrl = v3Url,
            okHttpCall = { nseApi.getOptionChainV3(type = ocType, symbol = symbol, expiry = nearestExpiry) },
        )
        val parsedV3 = v3Json?.let { NseOptionChainParser.parse(it, symbol, fallbackSpotPrice) }
        if (parsedV3 != null && (parsedV3.atmOptions.atmCe != null || parsedV3.atmOptions.atmPe != null)) {
            val futParsed = futDeferred.await()
            return@coroutineScope mergeFuturesIntoDerivative(parsedV3, futParsed)
        }

        // Strategy 2: Classic NSE Option Chain endpoint (option-chain-equities / option-chain-indices)
        val legacyOcUrl = if (isIndex) {
            "https://www.nseindia.com/api/option-chain-indices?symbol=$encodedSymbol"
        } else {
            "https://www.nseindia.com/api/option-chain-equities?symbol=$encodedSymbol"
        }
        val legacyOcJson = executeNseJsonCall(
            webViewUrl = legacyOcUrl,
            okHttpCall = {
                if (isIndex) nseApi.getOptionChainIndices(symbol) else nseApi.getOptionChainEquities(symbol)
            },
        )
        val parsedLegacyOc = legacyOcJson?.let { NseOptionChainParser.parse(it, symbol, fallbackSpotPrice) }
        if (parsedLegacyOc != null && (parsedLegacyOc.atmOptions.atmCe != null || parsedLegacyOc.atmOptions.atmPe != null)) {
            val futParsed = futDeferred.await()
            return@coroutineScope mergeFuturesIntoDerivative(parsedLegacyOc, futParsed)
        }

        // Strategy 3: NSE NextApi GetQuoteApi (getOptionChainData)
        val nextOcUrl = if (!nearestExpiry.isNullOrBlank()) {
            "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?functionName=getOptionChainData&symbol=$encodedSymbol&params=${urlEncode("expiryDate=$nearestExpiry")}"
        } else {
            "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?functionName=getOptionChainData&symbol=$encodedSymbol"
        }
        val nextOcJson = executeNseJsonCall(
            webViewUrl = nextOcUrl,
            okHttpCall = {
                nseApi.getNextApiQuote(
                    functionName = "getOptionChainData",
                    symbol = symbol,
                    params = nearestExpiry?.let { "expiryDate=$it" },
                )
            },
        )
        val parsedNextOc = nextOcJson?.let { NseOptionChainParser.parse(it, symbol, fallbackSpotPrice) }
        val futParsed = futDeferred.await()
        if (parsedNextOc != null && (parsedNextOc.atmOptions.atmCe != null || parsedNextOc.atmOptions.atmPe != null)) {
            return@coroutineScope mergeFuturesIntoDerivative(parsedNextOc, futParsed)
        }

        futParsed ?: parsedV3 ?: parsedLegacyOc ?: parsedNextOc
    }

    private suspend fun fetchFuturesDerivativeFallback(
        symbol: String,
        encodedSymbol: String,
        fallbackSpotPrice: Double,
    ): LiveDerivativeParsed? {
        val nextDerivJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?functionName=getSymbolDerivativesData&symbol=$encodedSymbol",
            okHttpCall = {
                nseApi.getNextApiQuote(functionName = "getSymbolDerivativesData", symbol = symbol)
            },
        )
        val parsedNextDeriv = nextDerivJson?.let { NseOptionChainParser.parse(it, symbol, fallbackSpotPrice) }
        if (parsedNextDeriv != null && parsedNextDeriv.futOi > 0L) {
            return parsedNextDeriv
        }

        val quoteDerivJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/quote-derivative?symbol=$encodedSymbol",
            okHttpCall = { nseApi.getQuoteDerivativeRaw(symbol) },
        )
        val parsedQuoteDeriv = quoteDerivJson?.let { NseOptionChainParser.parse(it, symbol, fallbackSpotPrice) }
        return when {
            parsedQuoteDeriv != null && parsedQuoteDeriv.futOi > 0L -> parsedQuoteDeriv
            else -> parsedNextDeriv ?: parsedQuoteDeriv
        }
    }

    private fun mergeFuturesIntoDerivative(
        optionSide: LiveDerivativeParsed,
        futuresSide: LiveDerivativeParsed?,
    ): LiveDerivativeParsed {
        if (futuresSide == null) return optionSide
        return optionSide.copy(
            futOi = if (optionSide.futOi > 0L) optionSide.futOi else futuresSide.futOi,
            futOiChange = if (optionSide.futOi > 0L) optionSide.futOiChange else futuresSide.futOiChange,
            futOiChangePct = if (optionSide.futOi > 0L) optionSide.futOiChangePct else futuresSide.futOiChangePct,
            pcr = optionSide.pcr ?: futuresSide.pcr,
            callToPutRatio = optionSide.callToPutRatio ?: futuresSide.callToPutRatio,
            pcrOiChangePct = optionSide.pcrOiChangePct ?: futuresSide.pcrOiChangePct,
            putOiChange = optionSide.putOiChange ?: futuresSide.putOiChange,
            callOiChange = optionSide.callOiChange ?: futuresSide.callOiChange,
            intradayDeltaPcr = optionSide.intradayDeltaPcr ?: futuresSide.intradayDeltaPcr,
        )
    }

    private suspend fun fetchLiveDeliveryMetrics(symbol: String): Pair<Long, Double>? {
        if (symbol.uppercase(Locale.US) in INDEX_SYMBOLS) return null
        val encodedSymbol = urlEncode(symbol)

        // 1. Modern NSE NextApi GetQuoteApi (getSymbolData -> contains securityWiseDP)
        val nextSymbolJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?functionName=getSymbolData&marketType=N&series=EQ&symbol=$encodedSymbol",
            okHttpCall = {
                nseApi.getNextApiQuote(
                    functionName = "getSymbolData",
                    symbol = symbol,
                    marketType = "N",
                    series = "EQ",
                )
            },
        )
        parseDeliveryPairFromJson(nextSymbolJson)?.let { return it }

        // 2. Modern NSE NextApi getTradeInfo
        val nextTradeInfoJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?functionName=getTradeInfo&marketType=N&series=EQ&symbol=$encodedSymbol",
            okHttpCall = {
                nseApi.getNextApiQuote(
                    functionName = "getTradeInfo",
                    symbol = symbol,
                    marketType = "N",
                    series = "EQ",
                )
            },
        )
        parseDeliveryPairFromJson(nextTradeInfoJson)?.let { return it }

        // 3. Legacy quote-equity trade_info fallback
        val legacyTradeInfoJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/quote-equity?symbol=$encodedSymbol&section=trade_info",
            okHttpCall = { nseApi.getEquityTradeInfoRaw(symbol) },
        )
        parseDeliveryPairFromJson(legacyTradeInfoJson)?.let { return it }

        // 4. Legacy full quote-equity fallback
        val legacyFullJson = executeNseJsonCall(
            webViewUrl = "https://www.nseindia.com/api/quote-equity?symbol=$encodedSymbol",
            okHttpCall = { nseApi.getEquityQuoteRaw(symbol) },
        )
        return parseDeliveryPairFromJson(legacyFullJson)
    }

    internal fun parseDeliveryPairFromJson(json: String?): Pair<Long, Double>? {
        if (json.isNullOrBlank() || !NseOptionChainParser.isLikelyJsonObject(json)) return null
        val root = runCatching { genericMapAdapter.fromJson(json) }.getOrNull() ?: return null
        val dp = findNestedMapByKey(root, "securityWiseDP") ?: root
        val qty = parseFlexibleLong(dp["deliveryQuantity"])
            ?: parseFlexibleLong(dp["deliveryQty"])
            ?: parseFlexibleLong(dp["COP_DELIV_QTY"])
        val pct = parseFlexibleDouble(dp["deliveryToTradedQuantity"])
            ?: parseFlexibleDouble(dp["deliveryPercentage"])
            ?: parseFlexibleDouble(dp["deliveryPerc"])
            ?: parseFlexibleDouble(dp["COP_DELIV_PERC"])
        return if (qty != null && pct != null && (qty > 0L || pct > 0.0)) {
            qty to pct
        } else {
            null
        }
    }

    private fun findNestedMapByKey(node: Any?, targetKey: String, depth: Int = 0): Map<String, Any?>? {
        if (depth > 4 || node == null) return null
        when (node) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val typed = node as Map<String, Any?>
                val direct = typed[targetKey] as? Map<String, Any?>
                if (direct != null) return direct
                for (value in typed.values) {
                    findNestedMapByKey(value, targetKey, depth + 1)?.let { return it }
                }
            }
            is List<*> -> {
                for (item in node) {
                    findNestedMapByKey(item, targetKey, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun urlEncode(raw: String): String =
        URLEncoder.encode(raw, StandardCharsets.UTF_8.name())

    private companion object {
        val INDEX_SYMBOLS = setOf("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "NIFTYNXT50")
    }

    private suspend fun fetchAndStoreHistorical5PlusDays(
        symbol: String,
        existingHistory: List<DailyFnoMetricEntity>,
    ) {
        val toDate = LocalDate.now(istZone)
        val fromDate = toDate.minusDays(25) // ~16 trading sessions to guarantee >= 5-10 valid days across holidays
        val fromStr = fromDate.format(nseQueryDateFormatter)
        val toStr = toDate.format(nseQueryDateFormatter)
        val encodedSymbol = urlEncode(symbol)
        val encodedSeries = urlEncode("[\"EQ\"]")
        val isIndex = symbol.uppercase(Locale.US) in INDEX_SYMBOLS
        val instrumentType = if (isIndex) "FUTIDX" else "FUTSTK"
        val currentYear = toDate.year

        val (equityRows, foRows) = coroutineScope {
            val eqDef = async {
                if (isIndex) {
                    emptyList()
                } else {
                    // 1. Modern NSE historicalOR/cm/equity endpoint
                    val orEqUrl = "https://www.nseindia.com/api/historicalOR/cm/equity?from=$fromStr&to=$toStr&symbol=$encodedSymbol&series=$encodedSeries"
                    val orJson = executeNseJsonCall(
                        webViewUrl = orEqUrl,
                        okHttpCall = { nseApi.getHistoricalOrEquityRaw(fromStr, toStr, symbol) },
                    )
                    val parsedOr = parseHistoricalEquityRows(orJson)
                    if (parsedOr.isNotEmpty()) {
                        parsedOr
                    } else {
                        // 2. Legacy securityArchives fallback
                        val legacyEqUrl = "https://www.nseindia.com/api/historical/securityArchives?from=$fromStr&to=$toStr&symbol=$encodedSymbol&dataType=priceVolumeDeliverable&series=EQ"
                        val legacyJson = executeNseJsonCall(
                            webViewUrl = legacyEqUrl,
                            okHttpCall = { nseApi.getHistoricalDeliveryRaw(fromStr, toStr, symbol) },
                        )
                        parseHistoricalEquityRows(legacyJson)
                    }
                }
            }
            val foDef = async {
                // 1. Modern NSE historicalOR/foCPV (first without year, then with year if needed)
                val orFoUrl = "https://www.nseindia.com/api/historicalOR/foCPV?from=$fromStr&to=$toStr&symbol=$encodedSymbol&instrumentType=$instrumentType"
                val orJson = executeNseJsonCall(
                    webViewUrl = orFoUrl,
                    okHttpCall = { nseApi.getHistoricalOrFoRaw(fromStr, toStr, symbol, instrumentType = instrumentType, year = null) },
                )
                val parsedOr = parseHistoricalFoRows(orJson)
                if (parsedOr.isNotEmpty()) {
                    parsedOr
                } else {
                    val orYearUrl = "$orFoUrl&year=$currentYear"
                    val orYearJson = executeNseJsonCall(
                        webViewUrl = orYearUrl,
                        okHttpCall = { nseApi.getHistoricalOrFoRaw(fromStr, toStr, symbol, instrumentType = instrumentType, year = currentYear) },
                    )
                    val parsedOrYear = parseHistoricalFoRows(orYearJson)
                    if (parsedOrYear.isNotEmpty()) {
                        parsedOrYear
                    } else {
                        // 2. Legacy historical/foCPV fallback
                        val legacyFoUrl = "https://www.nseindia.com/api/historical/foCPV?from=$fromStr&to=$toStr&symbol=$encodedSymbol&instrumentType=$instrumentType"
                        val legacyJson = executeNseJsonCall(
                            webViewUrl = legacyFoUrl,
                            okHttpCall = { nseApi.getHistoricalFuturesOiRaw(fromStr, toStr, symbol, instrumentType = instrumentType) },
                        )
                        parseHistoricalFoRows(legacyJson)
                    }
                }
            }
            eqDef.await() to foDef.await()
        }

        if (equityRows.isEmpty() && foRows.isEmpty()) return

        val mergedEntities = mergeHistoricalRows(
            symbol = symbol,
            equityRows = equityRows,
            foRows = foRows,
            existingEntities = existingHistory,
            nowMs = System.currentTimeMillis(),
            maxDays = 15,
        )

        if (mergedEntities.isNotEmpty()) {
            dao.upsertAll(mergedEntities)
            dao.pruneSymbolOlderThan(symbol, keepDays = 15)
        }
    }

    internal fun parseHistoricalEquityRows(json: String?): List<NseHistoricalEquityRow> {
        if (json.isNullOrBlank() || !NseOptionChainParser.isLikelyJsonObject(json)) return emptyList()
        runCatching { historicalEquityAdapter.fromJson(json)?.data?.takeIf { it.isNotEmpty() } }
            .getOrNull()
            ?.let { return it }

        val root = runCatching { genericMapAdapter.fromJson(json) }.getOrNull() ?: return emptyList()
        val dataList = root["data"] as? List<*> ?: return emptyList()
        return dataList.mapNotNull { entry ->
            val row = entry as? Map<*, *> ?: return@mapNotNull null
            val ts = (row["CH_TIMESTAMP"] ?: row["mTIMESTAMP"] ?: row["TIMESTAMP"])?.toString()
            if (ts.isNullOrBlank()) return@mapNotNull null
            NseHistoricalEquityRow(
                timestamp = ts,
                closingPrice = parseFlexibleDouble(row["CH_CLOSING_PRICE"] ?: row["CH_LAST_TRADED_PRICE"] ?: row["CLOSE"]),
                previousClosePrice = parseFlexibleDouble(row["CH_PREVIOUS_CLS_PRICE"] ?: row["PREV_CLOSE"]),
                totalTradedQty = parseFlexibleLong(row["CH_TOT_TRADED_QTY"] ?: row["TOTTRDQTY"]),
                deliveryQty = parseFlexibleLong(row["COP_DELIV_QTY"] ?: row["DELIV_QTY"]),
                deliveryPerc = parseFlexibleDouble(row["COP_DELIV_PERC"] ?: row["DELIV_PERC"]),
            )
        }
    }

    internal fun parseHistoricalFoRows(json: String?): List<NseHistoricalFoRow> {
        if (json.isNullOrBlank() || !NseOptionChainParser.isLikelyJsonObject(json)) return emptyList()
        runCatching { historicalFoAdapter.fromJson(json)?.data?.takeIf { it.isNotEmpty() } }
            .getOrNull()
            ?.let { return it }

        val root = runCatching { genericMapAdapter.fromJson(json) }.getOrNull() ?: return emptyList()
        val dataList = root["data"] as? List<*> ?: return emptyList()
        return dataList.mapNotNull { entry ->
            val row = entry as? Map<*, *> ?: return@mapNotNull null
            val ts = (row["FH_TIMESTAMP"] ?: row["mTIMESTAMP"] ?: row["TIMESTAMP"])?.toString()
            if (ts.isNullOrBlank()) return@mapNotNull null
            NseHistoricalFoRow(
                timestamp = ts,
                openInterest = parseFlexibleLong(row["FH_OPEN_INT"] ?: row["OPEN_INT"]),
                changeInOi = parseFlexibleLong(row["FH_CHANGE_IN_OI"] ?: row["CHG_IN_OI"] ?: row["CHANGE_IN_OI"]),
                closingPrice = parseFlexibleDouble(row["FH_CLOSING_PRICE"] ?: row["FH_LAST_TRADED_PRICE"] ?: row["CLOSE"]),
                totalTradedQty = parseFlexibleLong(row["FH_TOT_TRADED_QTY"] ?: row["FH_TOT_TRADED_VAL"]),
            )
        }
    }

    private fun parseFlexibleDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }
        is String -> {
            val clean = value.replace(",", "").trim()
            if (clean.isEmpty() || clean == "-") null else clean.toDoubleOrNull()
        }
        else -> null
    }

    private fun parseFlexibleLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> {
            val clean = value.replace(",", "").trim()
            if (clean.isEmpty() || clean == "-") null else clean.toDoubleOrNull()?.toLong()
        }
        else -> null
    }

    /**
     * Merges fetched NSE equity delivery and futures OI historical rows with any existing local Room
     * backup entries non-destructively (never overwriting non-zero backed-up fields with zero)
     * and derives sequential day-over-day OI Change % whenever NSE omits FH_CHANGE_IN_OI.
     */
    internal fun mergeHistoricalRows(
        symbol: String,
        equityRows: List<NseHistoricalEquityRow>,
        foRows: List<NseHistoricalFoRow>,
        existingEntities: List<DailyFnoMetricEntity>,
        nowMs: Long = System.currentTimeMillis(),
        maxDays: Int = 15,
    ): List<DailyFnoMetricEntity> {
        val existingByDate = existingEntities.associateBy { it.tradeDate }

        // Group FUTSTK / FUTIDX historical records by ISO date (summing OI across active contracts per date)
        val foByDate = HashMap<String, Pair<Long, Long>>() // date -> (totalOI, totalOiChg)
        val foCloseByDate = HashMap<String, Double>()
        val foTradedQtyByDate = HashMap<String, Long>()
        for (row in foRows) {
            val iso = parseNseDateToIso(row.timestamp) ?: continue
            val prev = foByDate[iso] ?: (0L to 0L)
            foByDate[iso] = (prev.first + (row.openInterest ?: 0L)) to (prev.second + (row.changeInOi ?: 0L))
            if ((row.closingPrice ?: 0.0) > 0.0 && !foCloseByDate.containsKey(iso)) {
                foCloseByDate[iso] = row.closingPrice!!
            }
            if ((row.totalTradedQty ?: 0L) > 0L) {
                foTradedQtyByDate[iso] = (foTradedQtyByDate[iso] ?: 0L) + row.totalTradedQty!!
            }
        }

        val eqByDate = HashMap<String, Triple<Double, Double, Pair<Long, Long>>>() // close, chg%, (traded, deliv)
        val delivPctByDate = HashMap<String, Double>()
        for (row in equityRows) {
            val iso = parseNseDateToIso(row.timestamp) ?: continue
            val close = row.closingPrice ?: 0.0
            val prevCls = row.previousClosePrice ?: 0.0
            val chgPct = if (prevCls > 0.0) ((close - prevCls) / prevCls) * 100.0 else 0.0
            val traded = row.totalTradedQty ?: 0L
            val deliv = row.deliveryQty ?: 0L
            eqByDate[iso] = Triple(close, chgPct, traded to deliv)
            delivPctByDate[iso] = row.deliveryPerc ?: 0.0
        }

        val allDates = (foByDate.keys + eqByDate.keys + existingByDate.keys)
            .distinct()
            .sorted() // chronological oldest -> newest for sequential fallback calculation
        if (allDates.isEmpty()) return emptyList()

        val builtAsc = ArrayList<DailyFnoMetricEntity>(allDates.size)
        for (idx in allDates.indices) {
            val iso = allDates[idx]
            val prevBuilt = builtAsc.lastOrNull()
            val prevBackup = existingByDate[iso]
            val eq = eqByDate[iso]
            val fo = foByDate[iso]

            val fetchedClose = eq?.first?.takeIf { it > 0.0 } ?: foCloseByDate[iso]?.takeIf { it > 0.0 }
            val closePrice = fetchedClose ?: prevBackup?.closePrice ?: 0.0
            val priceChgPct = when {
                eq != null && eq.second != 0.0 -> eq.second
                prevBackup != null && prevBackup.priceChangePct != 0.0 -> prevBackup.priceChangePct
                closePrice > 0.0 && prevBuilt != null && prevBuilt.closePrice > 0.0 ->
                    ((closePrice - prevBuilt.closePrice) / prevBuilt.closePrice) * 100.0
                else -> 0.0
            }

            val tradedQty = eq?.third?.first?.takeIf { it > 0L }
                ?: prevBackup?.tradedQty?.takeIf { it > 0L }
                ?: foTradedQtyByDate[iso]
                ?: 0L

            val delivQty = eq?.third?.second?.takeIf { it > 0L }
                ?: prevBackup?.deliveryQty
                ?: 0L
            val delivPct = delivPctByDate[iso]?.takeIf { it > 0.0 }
                ?: prevBackup?.deliveryPct
                ?: 0.0

            val fetchedOi = fo?.first?.takeIf { it > 0L }
            val oi = fetchedOi ?: prevBackup?.openInterest ?: 0L
            val rawFetchedOiChg = fo?.second
            val oiChg = when {
                fetchedOi != null && rawFetchedOiChg != null && rawFetchedOiChg != 0L -> rawFetchedOiChg
                prevBackup != null && prevBackup.openInterest == oi && prevBackup.oiChange != 0L -> prevBackup.oiChange
                oi > 0L && prevBuilt != null && prevBuilt.openInterest > 0L -> oi - prevBuilt.openInterest
                else -> rawFetchedOiChg ?: prevBackup?.oiChange ?: 0L
            }
            val oiChgPct = when {
                oi > 0L && oiChg != 0L -> {
                    val prevOi = (oi - oiChg).coerceAtLeast(1L)
                    (oiChg.toDouble() / prevOi.toDouble()) * 100.0
                }
                prevBackup != null && prevBackup.openInterest == oi -> prevBackup.oiChangePct
                else -> 0.0
            }

            builtAsc.add(
                DailyFnoMetricEntity(
                    symbol = symbol,
                    tradeDate = iso,
                    closePrice = closePrice,
                    priceChangePct = priceChgPct,
                    tradedQty = tradedQty,
                    openInterest = oi,
                    oiChange = oiChg,
                    oiChangePct = oiChgPct,
                    deliveryQty = delivQty,
                    deliveryPct = delivPct,
                    updatedAtMs = nowMs,
                )
            )
        }

        return builtAsc.sortedByDescending { it.tradeDate }.take(maxDays)
    }

    private fun parseNseDateToIso(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        // Try yyyy-MM-dd
        runCatching {
            return LocalDate.parse(trimmed.take(10), isoDateFormatter).format(isoDateFormatter)
        }
        // Try dd-MMM-yyyy (e.g. 12-Nov-2026)
        runCatching {
            return LocalDate.parse(trimmed, nseAltDateFormatter).format(isoDateFormatter)
        }
        // Try dd-MM-yyyy
        runCatching {
            return LocalDate.parse(trimmed, nseQueryDateFormatter).format(isoDateFormatter)
        }
        return null
    }
}
