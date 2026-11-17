package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NseDerivativeResponse(
    @Json(name = "stocks") val stocks: List<NseDerivativeContract>?,
    @Json(name = "underlyingValue") val underlyingValue: Double?,
    @Json(name = "expiryDates") val expiryDates: List<String>?,
    @Json(name = "fut_timestamp") val futTimestamp: String?,
)

@JsonClass(generateAdapter = true)
data class NseDerivativeContract(
    @Json(name = "metadata") val metadata: NseDerivativeMetadata?,
    @Json(name = "marketDeptOrderBook") val marketDeptOrderBook: NseMarketDeptOrderBook?,
)

@JsonClass(generateAdapter = true)
data class NseDerivativeMetadata(
    @Json(name = "instrumentType") val instrumentType: String?,
    @Json(name = "optionType") val optionType: String?,
    @Json(name = "expiryDate") val expiryDate: String?,
    @Json(name = "strikePrice") val strikePrice: Double?,
    @Json(name = "lastPrice") val lastPrice: Double?,
    @Json(name = "change") val change: Double?,
    @Json(name = "pChange") val pChange: Double?,
    @Json(name = "numberOfContractsTraded") val numberOfContractsTraded: Long?,
)

@JsonClass(generateAdapter = true)
data class NseMarketDeptOrderBook(
    @Json(name = "tradeInfo") val tradeInfo: NseDerivativeTradeInfo?,
)

@JsonClass(generateAdapter = true)
data class NseDerivativeTradeInfo(
    @Json(name = "openInterest") val openInterest: Double?,
    @Json(name = "changeinOpenInterest") val changeInOpenInterest: Double?,
    @Json(name = "pchangeinOpenInterest") val pChangeInOpenInterest: Double?,
    @Json(name = "tradedVolume") val tradedVolume: Double?,
    @Json(name = "lastPrice") val lastPrice: Double?,
)

@JsonClass(generateAdapter = true)
data class NseEquityTradeInfoResponse(
    @Json(name = "securityWiseDP") val securityWiseDP: NseSecurityWiseDp?,
)

@JsonClass(generateAdapter = true)
data class NseSecurityWiseDp(
    @Json(name = "quantityTraded") val quantityTraded: Long?,
    @Json(name = "deliveryQuantity") val deliveryQuantity: Long?,
    @Json(name = "deliveryToTradedQuantity") val deliveryToTradedQuantity: Double?,
    @Json(name = "secWiseDelPosDate") val secWiseDelPosDate: String?,
)

@JsonClass(generateAdapter = true)
data class NseHistoricalEquityResponse(
    @Json(name = "data") val data: List<NseHistoricalEquityRow>?,
)

@JsonClass(generateAdapter = true)
data class NseHistoricalEquityRow(
    @Json(name = "CH_TIMESTAMP") val timestamp: String?,
    @Json(name = "CH_CLOSING_PRICE") val closingPrice: Double?,
    @Json(name = "CH_PREVIOUS_CLS_PRICE") val previousClosePrice: Double?,
    @Json(name = "CH_TOT_TRADED_QTY") val totalTradedQty: Long?,
    @Json(name = "COP_DELIV_QTY") val deliveryQty: Long?,
    @Json(name = "COP_DELIV_PERC") val deliveryPerc: Double?,
)

@JsonClass(generateAdapter = true)
data class NseHistoricalFoResponse(
    @Json(name = "data") val data: List<NseHistoricalFoRow>?,
)

@JsonClass(generateAdapter = true)
data class NseHistoricalFoRow(
    @Json(name = "FH_TIMESTAMP") val timestamp: String?,
    @Json(name = "FH_OPEN_INT") val openInterest: Long?,
    @Json(name = "FH_CHANGE_IN_OI") val changeInOi: Long?,
    @Json(name = "FH_CLOSING_PRICE") val closingPrice: Double?,
    @Json(name = "FH_TOT_TRADED_QTY") val totalTradedQty: Long?,
)
