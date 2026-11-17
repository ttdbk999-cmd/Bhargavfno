package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YahooChartResponse(
    @Json(name = "chart") val chart: ChartContainer?,
)

@JsonClass(generateAdapter = true)
data class ChartContainer(
    @Json(name = "result") val result: List<ChartResult>?,
    @Json(name = "error") val error: ChartError?,
)

@JsonClass(generateAdapter = true)
data class ChartError(
    @Json(name = "code") val code: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class ChartResult(
    @Json(name = "meta") val meta: ChartMeta?,
    @Json(name = "timestamp") val timestamp: List<Long>?,
    @Json(name = "indicators") val indicators: Indicators?,
)

@JsonClass(generateAdapter = true)
data class ChartMeta(
    @Json(name = "symbol") val symbol: String?,
    @Json(name = "shortName") val shortName: String?,
    @Json(name = "longName") val longName: String?,
    @Json(name = "regularMarketPrice") val regularMarketPrice: Double?,
    @Json(name = "chartPreviousClose") val chartPreviousClose: Double?,
    @Json(name = "previousClose") val previousClose: Double?,
    @Json(name = "regularMarketVolume") val regularMarketVolume: Long?,
    @Json(name = "regularMarketTime") val regularMarketTime: Long?,
)

@JsonClass(generateAdapter = true)
data class Indicators(
    @Json(name = "quote") val quote: List<QuoteValues>?,
)

@JsonClass(generateAdapter = true)
data class QuoteValues(
    @Json(name = "open") val open: List<Double?>?,
    @Json(name = "high") val high: List<Double?>?,
    @Json(name = "low") val low: List<Double?>?,
    @Json(name = "close") val close: List<Double?>?,
    @Json(name = "volume") val volume: List<Long?>?,
)
