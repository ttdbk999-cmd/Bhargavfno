package com.example.data.remote

import com.squareup.moshi.Moshi
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface NseIndiaApi {

    @GET("/")
    suspend fun warmUpSession(): Response<ResponseBody>

    @GET("option-chain")
    suspend fun warmUpOptionChain(): Response<ResponseBody>

    @GET("api/option-chain-contract-info")
    suspend fun getOptionChainContractInfo(
        @Query("symbol") symbol: String,
    ): Response<ResponseBody>

    @GET("api/option-chain-v3")
    suspend fun getOptionChainV3(
        @Query("type") type: String = "Equity",
        @Query("symbol") symbol: String,
        @Query("expiry") expiry: String? = null,
    ): Response<ResponseBody>

    @GET("api/option-chain-equities")
    suspend fun getOptionChainEquities(
        @Query("symbol") symbol: String,
    ): Response<ResponseBody>

    @GET("api/option-chain-indices")
    suspend fun getOptionChainIndices(
        @Query("symbol") symbol: String,
    ): Response<ResponseBody>

    @GET("api/NextApi/apiClient/GetQuoteApi")
    suspend fun getNextApiQuote(
        @Query("functionName") functionName: String,
        @Query("symbol") symbol: String,
        @Query("params") params: String? = null,
        @Query("marketType") marketType: String? = null,
        @Query("series") series: String? = null,
    ): Response<ResponseBody>

    @GET("api/quote-derivative")
    suspend fun getQuoteDerivativeRaw(
        @Query("symbol") symbol: String,
    ): Response<ResponseBody>

    @GET("api/quote-derivative")
    suspend fun getQuoteDerivative(
        @Query("symbol") symbol: String,
    ): NseDerivativeResponse

    @GET("api/quote-equity")
    suspend fun getEquityTradeInfo(
        @Query("symbol") symbol: String,
        @Query("section") section: String = "trade_info",
    ): NseEquityTradeInfoResponse

    @GET("api/quote-equity")
    suspend fun getEquityTradeInfoRaw(
        @Query("symbol") symbol: String,
        @Query("section") section: String = "trade_info",
    ): Response<ResponseBody>

    @GET("api/quote-equity")
    suspend fun getEquityQuoteRaw(
        @Query("symbol") symbol: String,
    ): Response<ResponseBody>

    @GET("api/historicalOR/cm/equity")
    suspend fun getHistoricalOrEquityRaw(
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("symbol") symbol: String,
        @Query("series") series: String = "[\"EQ\"]",
    ): Response<ResponseBody>

    @GET("api/historicalOR/foCPV")
    suspend fun getHistoricalOrFoRaw(
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("symbol") symbol: String,
        @Query("instrumentType") instrumentType: String = "FUTSTK",
        @Query("year") year: Int? = null,
    ): Response<ResponseBody>

    @GET("api/historical/securityArchives")
    suspend fun getHistoricalDelivery(
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("symbol") symbol: String,
        @Query("dataType") dataType: String = "priceVolumeDeliverable",
        @Query("series") series: String = "EQ",
    ): NseHistoricalEquityResponse

    @GET("api/historical/securityArchives")
    suspend fun getHistoricalDeliveryRaw(
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("symbol") symbol: String,
        @Query("dataType") dataType: String = "priceVolumeDeliverable",
        @Query("series") series: String = "EQ",
    ): Response<ResponseBody>

    @GET("api/historical/foCPV")
    suspend fun getHistoricalFuturesOi(
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("symbol") symbol: String,
        @Query("instrumentType") instrumentType: String = "FUTSTK",
    ): NseHistoricalFoResponse

    @GET("api/historical/foCPV")
    suspend fun getHistoricalFuturesOiRaw(
        @Query("from") fromDate: String,
        @Query("to") toDate: String,
        @Query("symbol") symbol: String,
        @Query("instrumentType") instrumentType: String = "FUTSTK",
    ): Response<ResponseBody>
}

class InMemoryNseCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val existing = store[url.host].orEmpty().associateBy { it.name }.toMutableMap()
        for (c in cookies) {
            existing[c.name] = c
        }
        store[url.host] = existing.values.toList()
    }

    fun importCookieHeader(host: String = "www.nseindia.com", rawCookieHeader: String?) {
        if (rawCookieHeader.isNullOrBlank()) return
        val existing = store[host].orEmpty().associateBy { it.name }.toMutableMap()
        val now = System.currentTimeMillis()
        rawCookieHeader.split(";").forEach { pair ->
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val name = pair.substring(0, eqIdx).trim()
                val value = pair.substring(eqIdx + 1).trim()
                if (name.isNotEmpty()) {
                    existing[name] = Cookie.Builder()
                        .domain(host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .expiresAt(now + 15 * 60_000L)
                        .build()
                }
            }
        }
        if (existing.isNotEmpty()) {
            store[host] = existing.values.toList()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val hostCookies = store[url.host].orEmpty().filter { it.expiresAt >= now }
        if (hostCookies.isNotEmpty()) return hostCookies
        // Share cookies across nseindia.com subdomains if needed
        return store.entries
            .firstOrNull { url.host.endsWith(it.key) || it.key.endsWith(url.host) }
            ?.value
            ?.filter { it.expiresAt >= now }
            .orEmpty()
    }

    fun hasCookies(): Boolean = store.values.any { it.isNotEmpty() }

    fun clear() {
        store.clear()
    }
}

object NseNetworkModule {

    private const val NSE_BASE_URL = "https://www.nseindia.com/"
    const val CHROME_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    val cookieJar = InMemoryNseCookieJar()

    val nseApi: NseIndiaApi by lazy {
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(14, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val isApiCall = original.url.encodedPath.startsWith("/api/")
                val builder = original.newBuilder()
                    .header("User-Agent", CHROME_USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")

                if (isApiCall) {
                    builder
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Referer", "https://www.nseindia.com/option-chain")
                        .header("Origin", "https://www.nseindia.com")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                } else {
                    builder
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        )
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                }

                chain.proceed(builder.build())
            }
            .build()

        val moshi = Moshi.Builder().build()

        Retrofit.Builder()
            .baseUrl(NSE_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NseIndiaApi::class.java)
    }
}
