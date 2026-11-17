package com.example.data.remote

import com.example.domain.model.AtmOptionBundle
import com.example.domain.model.OptionSide
import com.example.domain.model.OptionStrikeQuote
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.abs

data class LiveDerivativeParsed(
    val futOi: Long,
    val futOiChange: Long,
    val futOiChangePct: Double,
    val pcr: Double?,
    val callToPutRatio: Double?,
    val pcrOiChangePct: Double?,
    val putOiChange: Long?,
    val callOiChange: Long?,
    val intradayDeltaPcr: Double?,
    val atmOptions: AtmOptionBundle,
)

/**
 * Multi-schema parser for NSE India option chain & derivative endpoints:
 * - /api/option-chain-v3?type=Equity&symbol=...&expiry=...
 * - /api/option-chain-equities?symbol=... / /api/option-chain-indices?symbol=...
 * - /api/NextApi/apiClient/GetQuoteApi?functionName=getSymbolDerivativesData|getOptionChainData
 * - /api/quote-derivative?symbol=...
 */
object NseOptionChainParser {

    private val istZone = ZoneId.of("Asia/Kolkata")
    private val nseAltDateFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.US)
    private val nseNumericDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    private val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun isLikelyJsonObject(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trimStart()
        return trimmed.startsWith("{")
    }

    fun parseContractInfoNearestExpiry(json: String): String? {
        val root = parseRootMap(json) ?: return null
        val expiryList = asStringList(root["expiryDates"]).ifEmpty {
            asStringList(asMap(root["records"])?.get("expiryDates"))
        }
        return pickNearestExpiry(expiryList)
    }

    fun parse(
        json: String,
        symbol: String,
        fallbackSpotPrice: Double,
    ): LiveDerivativeParsed? {
        val root = parseRootMap(json) ?: return null

        // Format A: Option Chain v3 / equities / indices / NextApi getOptionChainData
        val records = asMap(root["records"])
        val filtered = asMap(root["filtered"])
        if (records != null || filtered != null) {
            parseOptionChainFormat(
                root = root,
                records = records,
                filtered = filtered,
                symbol = symbol,
                fallbackSpotPrice = fallbackSpotPrice,
            )?.let { return it }
        }

        // Format B: Legacy quote-derivative (`stocks` array)
        val stocks = asMapList(root["stocks"])
        if (stocks.isNotEmpty()) {
            parseQuoteDerivativeFormat(
                root = root,
                stocks = stocks,
                symbol = symbol,
                fallbackSpotPrice = fallbackSpotPrice,
            )?.let { return it }
        }

        // Format C: NextApi `getSymbolDerivativesData` (`data` array)
        val dataList = asMapList(root["data"])
        if (dataList.isNotEmpty()) {
            parseNextApiDerivativesFormat(
                root = root,
                dataList = dataList,
                symbol = symbol,
                fallbackSpotPrice = fallbackSpotPrice,
            )?.let { return it }
        }

        return null
    }

    private fun parseOptionChainFormat(
        root: Map<String, Any?>,
        records: Map<String, Any?>?,
        filtered: Map<String, Any?>?,
        symbol: String,
        fallbackSpotPrice: Double,
    ): LiveDerivativeParsed? {
        val filteredData = asMapList(filtered?.get("data"))
        val recordsData = asMapList(records?.get("data"))
        val rows = if (recordsData.isNotEmpty()) recordsData else filteredData
        if (rows.isEmpty()) return null

        val spotPrice = asDouble(records?.get("underlyingValue"))
            ?: asDouble(root["underlyingValue"])
            ?: findUnderlyingInChainRows(rows)
            ?: fallbackSpotPrice

        val expiryDates = asStringList(records?.get("expiryDates"))
            .ifEmpty { rows.mapNotNull { asString(it["expiryDate"]) }.distinct() }

        val targetExpiry = pickNearestExpiry(expiryDates) ?: ""

        val allCeQuotes = mutableListOf<OptionStrikeQuote>()
        val allPeQuotes = mutableListOf<OptionStrikeQuote>()

        // Iterate records.data (or filtered.data) to compute full-chain PCR & nearest-expiry ATM quotes
        var totalCallOi = 0.0
        var totalPutOi = 0.0
        var totalCallOiChg = 0.0
        var totalPutOiChg = 0.0

        for (row in rows) {
            val rowExpiry = asString(row["expiryDate"]) ?: targetExpiry
            val rowStrike = asDouble(row["strikePrice"])

            asMap(row["CE"])?.let { ceMap ->
                val oi = asDouble(ceMap["openInterest"]) ?: 0.0
                val oiChg = asDouble(ceMap["changeinOpenInterest"]) ?: 0.0
                totalCallOi += oi
                totalCallOiChg += oiChg
                val exp = asString(ceMap["expiryDate"]) ?: rowExpiry
                val strike = asDouble(ceMap["strikePrice"]) ?: rowStrike ?: 0.0
                if (strike > 0.0) {
                    buildQuoteFromLeaf(
                        leaf = ceMap,
                        symbol = symbol,
                        side = OptionSide.CE,
                        strike = strike,
                        expiry = exp,
                    )?.let { allCeQuotes.add(it) }
                }
            }

            asMap(row["PE"])?.let { peMap ->
                val oi = asDouble(peMap["openInterest"]) ?: 0.0
                val oiChg = asDouble(peMap["changeinOpenInterest"]) ?: 0.0
                totalPutOi += oi
                totalPutOiChg += oiChg
                val exp = asString(peMap["expiryDate"]) ?: rowExpiry
                val strike = asDouble(peMap["strikePrice"]) ?: rowStrike ?: 0.0
                if (strike > 0.0) {
                    buildQuoteFromLeaf(
                        leaf = peMap,
                        symbol = symbol,
                        side = OptionSide.PE,
                        strike = strike,
                        expiry = exp,
                    )?.let { allPeQuotes.add(it) }
                }
            }
        }

        // Also check filtered.CE.totOI / filtered.PE.totOI if records rows were pre-filtered or zero
        if (totalCallOi <= 0.0) {
            totalCallOi = asDouble(asMap(filtered?.get("CE"))?.get("totOI")) ?: 0.0
        }
        if (totalPutOi <= 0.0) {
            totalPutOi = asDouble(asMap(filtered?.get("PE"))?.get("totOI")) ?: 0.0
        }

        // Prefer filteredData expiry or nearestExpiry for ATM selection
        val chosenExpiry = if (filteredData.isNotEmpty() && targetExpiry.isBlank()) {
            filteredData.firstNotNullOfOrNull { asString(it["expiryDate"]) } ?: ""
        } else {
            targetExpiry
        }

        val nearestCe = if (chosenExpiry.isNotBlank()) {
            allCeQuotes.filter { it.expiryDate.equals(chosenExpiry, ignoreCase = true) }.ifEmpty { allCeQuotes }
        } else {
            allCeQuotes
        }.sortedBy { it.strikePrice }

        val nearestPe = if (chosenExpiry.isNotBlank()) {
            allPeQuotes.filter { it.expiryDate.equals(chosenExpiry, ignoreCase = true) }.ifEmpty { allPeQuotes }
        } else {
            allPeQuotes
        }.sortedBy { it.strikePrice }

        val atmBundle = buildAtmBundle(
            expiry = chosenExpiry.ifBlank { nearestCe.firstOrNull()?.expiryDate ?: nearestPe.firstOrNull()?.expiryDate.orEmpty() },
            ceQuotes = nearestCe,
            peQuotes = nearestPe,
            spotPrice = spotPrice,
        )

        val pcrMetrics = computePcrMetrics(
            callsOi = totalCallOi,
            putsOi = totalPutOi,
            callsOiChg = totalCallOiChg,
            putsOiChg = totalPutOiChg,
            hasCalls = allCeQuotes.isNotEmpty(),
            hasPuts = allPeQuotes.isNotEmpty(),
        )

        if (totalCallOi <= 0.0 && totalPutOi <= 0.0 && atmBundle.atmCe == null && atmBundle.atmPe == null) {
            return null
        }

        return LiveDerivativeParsed(
            futOi = 0L,
            futOiChange = 0L,
            futOiChangePct = 0.0,
            pcr = pcrMetrics.pcr,
            callToPutRatio = pcrMetrics.callToPutRatio,
            pcrOiChangePct = pcrMetrics.pcrOiChangePct,
            putOiChange = pcrMetrics.putOiChange,
            callOiChange = pcrMetrics.callOiChange,
            intradayDeltaPcr = pcrMetrics.intradayDeltaPcr,
            atmOptions = atmBundle,
        )
    }

    private fun parseQuoteDerivativeFormat(
        root: Map<String, Any?>,
        stocks: List<Map<String, Any?>>,
        symbol: String,
        fallbackSpotPrice: Double,
    ): LiveDerivativeParsed? {
        val spotPrice = asDouble(root["underlyingValue"]) ?: fallbackSpotPrice

        var futOi = 0L
        var futOiChg = 0L
        var callsOi = 0.0
        var putsOi = 0.0
        var callsOiChg = 0.0
        var putsOiChg = 0.0

        val allCe = mutableListOf<OptionStrikeQuote>()
        val allPe = mutableListOf<OptionStrikeQuote>()
        val distinctExpiries = linkedSetOf<String>()

        for (contract in stocks) {
            val meta = asMap(contract["metadata"]) ?: continue
            val orderBook = asMap(contract["marketDeptOrderBook"])
            val tradeInfo = asMap(orderBook?.get("tradeInfo"))

            val instr = asString(meta["instrumentType"]).orEmpty()
            val optType = asString(meta["optionType"]).orEmpty()
            val oi = asDouble(tradeInfo?.get("openInterest")) ?: 0.0
            val oiChg = asDouble(tradeInfo?.get("changeinOpenInterest")) ?: 0.0

            if (instr.contains("Futures", ignoreCase = true) ||
                instr.equals("FUTSTK", ignoreCase = true) ||
                instr.equals("FUTIDX", ignoreCase = true)
            ) {
                futOi += oi.toLong()
                futOiChg += oiChg.toLong()
                continue
            }

            val isCe = optType.equals("Call", ignoreCase = true) || optType.equals("CE", ignoreCase = true)
            val isPe = optType.equals("Put", ignoreCase = true) || optType.equals("PE", ignoreCase = true)
            if (!isCe && !isPe) continue

            if (isCe) {
                callsOi += oi
                callsOiChg += oiChg
            } else {
                putsOi += oi
                putsOiChg += oiChg
            }

            val strike = asDouble(meta["strikePrice"]) ?: 0.0
            val expiry = asString(meta["expiryDate"]).orEmpty()
            if (expiry.isNotBlank()) distinctExpiries.add(expiry)
            if (strike <= 0.0) continue

            val ltp = firstPositiveDouble(
                asDouble(meta["lastPrice"]),
                asDouble(tradeInfo?.get("lastPrice")),
                asDouble(meta["closePrice"]),
            )
            val quote = OptionStrikeQuote(
                symbol = symbol,
                side = if (isCe) OptionSide.CE else OptionSide.PE,
                strikePrice = strike,
                expiryDate = expiry,
                runningPrice = ltp,
                change = asDouble(meta["change"]) ?: 0.0,
                changePercent = asDouble(meta["pChange"]) ?: 0.0,
                openInterest = oi.toLong(),
                oiChange = oiChg.toLong(),
                tradedContracts = asLong(meta["numberOfContractsTraded"]) ?: 0L,
            )
            if (isCe) allCe.add(quote) else allPe.add(quote)
        }

        val expiryOrder = asStringList(root["expiryDates"]).ifEmpty { distinctExpiries.toList() }
        val nearestExpiry = pickNearestExpiry(expiryOrder) ?: ""

        val nearestCe = if (nearestExpiry.isNotBlank()) {
            allCe.filter { it.expiryDate.equals(nearestExpiry, ignoreCase = true) }.ifEmpty { allCe }
        } else {
            allCe
        }.sortedBy { it.strikePrice }

        val nearestPe = if (nearestExpiry.isNotBlank()) {
            allPe.filter { it.expiryDate.equals(nearestExpiry, ignoreCase = true) }.ifEmpty { allPe }
        } else {
            allPe
        }.sortedBy { it.strikePrice }

        val atmBundle = buildAtmBundle(nearestExpiry, nearestCe, nearestPe, spotPrice)
        val prevOi = (futOi - futOiChg).coerceAtLeast(1L)
        val futOiChgPct = if (futOi > 0L) (futOiChg.toDouble() / prevOi.toDouble()) * 100.0 else 0.0
        val pcrMetrics = computePcrMetrics(
            callsOi = callsOi,
            putsOi = putsOi,
            callsOiChg = callsOiChg,
            putsOiChg = putsOiChg,
            hasCalls = allCe.isNotEmpty(),
            hasPuts = allPe.isNotEmpty(),
        )

        if (futOi <= 0L && callsOi <= 0.0 && putsOi <= 0.0 && atmBundle.atmCe == null && atmBundle.atmPe == null) {
            return null
        }

        return LiveDerivativeParsed(
            futOi = futOi,
            futOiChange = futOiChg,
            futOiChangePct = futOiChgPct,
            pcr = pcrMetrics.pcr,
            callToPutRatio = pcrMetrics.callToPutRatio,
            pcrOiChangePct = pcrMetrics.pcrOiChangePct,
            putOiChange = pcrMetrics.putOiChange,
            callOiChange = pcrMetrics.callOiChange,
            intradayDeltaPcr = pcrMetrics.intradayDeltaPcr,
            atmOptions = atmBundle,
        )
    }

    private fun parseNextApiDerivativesFormat(
        root: Map<String, Any?>,
        dataList: List<Map<String, Any?>>,
        symbol: String,
        fallbackSpotPrice: Double,
    ): LiveDerivativeParsed? {
        var spotPrice = asDouble(root["underlyingValue"]) ?: fallbackSpotPrice
        var futOi = 0L
        var futOiChg = 0L
        var callsOi = 0.0
        var putsOi = 0.0
        var callsOiChg = 0.0
        var putsOiChg = 0.0

        val allCe = mutableListOf<OptionStrikeQuote>()
        val allPe = mutableListOf<OptionStrikeQuote>()
        val distinctExpiries = linkedSetOf<String>()

        for (entry in dataList) {
            // Check nested CE/PE nodes first (NextApi strike-grouped format)
            val ceLeaf = asMap(entry["CE"])
            val peLeaf = asMap(entry["PE"])
            val rowStrike = asDouble(entry["strikePrice"]) ?: 0.0
            val rowExpiry = asString(entry["expiryDate"]).orEmpty()

            if (ceLeaf != null || peLeaf != null) {
                if (ceLeaf != null) {
                    val oi = asDouble(ceLeaf["openInterest"]) ?: 0.0
                    val oiChg = asDouble(ceLeaf["changeinOpenInterest"]) ?: 0.0
                    callsOi += oi
                    callsOiChg += oiChg
                    val exp = asString(ceLeaf["expiryDate"]) ?: rowExpiry
                    if (exp.isNotBlank()) distinctExpiries.add(exp)
                    val strike = asDouble(ceLeaf["strikePrice"]) ?: rowStrike
                    asDouble(ceLeaf["underlyingValue"])?.takeIf { it > 0.0 }?.let { spotPrice = it }
                    if (strike > 0.0) {
                        buildQuoteFromLeaf(ceLeaf, symbol, OptionSide.CE, strike, exp)?.let { allCe.add(it) }
                    }
                }
                if (peLeaf != null) {
                    val oi = asDouble(peLeaf["openInterest"]) ?: 0.0
                    val oiChg = asDouble(peLeaf["changeinOpenInterest"]) ?: 0.0
                    putsOi += oi
                    putsOiChg += oiChg
                    val exp = asString(peLeaf["expiryDate"]) ?: rowExpiry
                    if (exp.isNotBlank()) distinctExpiries.add(exp)
                    val strike = asDouble(peLeaf["strikePrice"]) ?: rowStrike
                    asDouble(peLeaf["underlyingValue"])?.takeIf { it > 0.0 }?.let { spotPrice = it }
                    if (strike > 0.0) {
                        buildQuoteFromLeaf(peLeaf, symbol, OptionSide.PE, strike, exp)?.let { allPe.add(it) }
                    }
                }
                continue
            }

            // Flat NextApi contract record (metadata or direct fields)
            val node = asMap(entry["metadata"]) ?: entry
            val tradeInfo = asMap(asMap(entry["marketDeptOrderBook"])?.get("tradeInfo")) ?: entry
            val instr = asString(node["instrumentType"]) ?: asString(entry["instrumentType"]).orEmpty()
            val optType = asString(node["optionType"]) ?: asString(entry["optionType"]).orEmpty()
            val oi = asDouble(tradeInfo["openInterest"]) ?: asDouble(node["openInterest"]) ?: 0.0
            val oiChg = asDouble(tradeInfo["changeinOpenInterest"]) ?: asDouble(node["changeinOpenInterest"]) ?: 0.0

            asDouble(node["underlyingValue"])?.takeIf { it > 0.0 }?.let { spotPrice = it }

            if (instr.contains("Futures", ignoreCase = true) ||
                instr.equals("FUTSTK", ignoreCase = true) ||
                instr.equals("FUTIDX", ignoreCase = true) ||
                optType.equals("XX", ignoreCase = true) ||
                (optType.isBlank() && rowStrike <= 0.0)
            ) {
                futOi += oi.toLong()
                futOiChg += oiChg.toLong()
                continue
            }

            val isCe = optType.equals("Call", ignoreCase = true) || optType.equals("CE", ignoreCase = true)
            val isPe = optType.equals("Put", ignoreCase = true) || optType.equals("PE", ignoreCase = true)
            if (!isCe && !isPe) continue

            if (isCe) {
                callsOi += oi
                callsOiChg += oiChg
            } else {
                putsOi += oi
                putsOiChg += oiChg
            }

            val strike = asDouble(node["strikePrice"]) ?: rowStrike
            val exp = asString(node["expiryDate"]) ?: rowExpiry
            if (exp.isNotBlank()) distinctExpiries.add(exp)
            if (strike <= 0.0) continue

            val ltp = firstPositiveDouble(
                asDouble(node["lastPrice"]),
                asDouble(tradeInfo["lastPrice"]),
                asDouble(node["closePrice"]),
            )
            val quote = OptionStrikeQuote(
                symbol = symbol,
                side = if (isCe) OptionSide.CE else OptionSide.PE,
                strikePrice = strike,
                expiryDate = exp,
                runningPrice = ltp,
                change = asDouble(node["change"]) ?: 0.0,
                changePercent = asDouble(node["pChange"]) ?: 0.0,
                openInterest = oi.toLong(),
                oiChange = oiChg.toLong(),
                tradedContracts = asLong(node["totalTradedVolume"])
                    ?: asLong(node["numberOfContractsTraded"])
                    ?: 0L,
            )
            if (isCe) allCe.add(quote) else allPe.add(quote)
        }

        val expiryOrder = asStringList(root["expiryDates"]).ifEmpty { distinctExpiries.toList() }
        val nearestExpiry = pickNearestExpiry(expiryOrder) ?: ""

        val nearestCe = if (nearestExpiry.isNotBlank()) {
            allCe.filter { it.expiryDate.equals(nearestExpiry, ignoreCase = true) }.ifEmpty { allCe }
        } else {
            allCe
        }.sortedBy { it.strikePrice }

        val nearestPe = if (nearestExpiry.isNotBlank()) {
            allPe.filter { it.expiryDate.equals(nearestExpiry, ignoreCase = true) }.ifEmpty { allPe }
        } else {
            allPe
        }.sortedBy { it.strikePrice }

        val atmBundle = buildAtmBundle(nearestExpiry, nearestCe, nearestPe, spotPrice)
        val prevOi = (futOi - futOiChg).coerceAtLeast(1L)
        val futOiChgPct = if (futOi > 0L) (futOiChg.toDouble() / prevOi.toDouble()) * 100.0 else 0.0
        val pcrMetrics = computePcrMetrics(
            callsOi = callsOi,
            putsOi = putsOi,
            callsOiChg = callsOiChg,
            putsOiChg = putsOiChg,
            hasCalls = allCe.isNotEmpty(),
            hasPuts = allPe.isNotEmpty(),
        )

        if (futOi <= 0L && callsOi <= 0.0 && putsOi <= 0.0 && atmBundle.atmCe == null && atmBundle.atmPe == null) {
            return null
        }

        return LiveDerivativeParsed(
            futOi = futOi,
            futOiChange = futOiChg,
            futOiChangePct = futOiChgPct,
            pcr = pcrMetrics.pcr,
            callToPutRatio = pcrMetrics.callToPutRatio,
            pcrOiChangePct = pcrMetrics.pcrOiChangePct,
            putOiChange = pcrMetrics.putOiChange,
            callOiChange = pcrMetrics.callOiChange,
            intradayDeltaPcr = pcrMetrics.intradayDeltaPcr,
            atmOptions = atmBundle,
        )
    }

    private fun buildQuoteFromLeaf(
        leaf: Map<String, Any?>,
        symbol: String,
        side: OptionSide,
        strike: Double,
        expiry: String,
    ): OptionStrikeQuote? {
        val lastPrice = asDouble(leaf["lastPrice"]) ?: 0.0
        val bid = asDouble(leaf["bidprice"]) ?: asDouble(leaf["buyPrice1"]) ?: 0.0
        val ask = asDouble(leaf["askPrice"]) ?: asDouble(leaf["sellPrice1"]) ?: 0.0
        val midQuote = when {
            bid > 0.0 && ask > 0.0 -> (bid + ask) / 2.0
            bid > 0.0 -> bid
            ask > 0.0 -> ask
            else -> 0.0
        }
        val runningPrice = when {
            lastPrice > 0.0 -> lastPrice
            midQuote > 0.0 -> midQuote
            else -> 0.0
        }
        val oi = asDouble(leaf["openInterest"])?.toLong() ?: 0L
        val oiChg = asDouble(leaf["changeinOpenInterest"])?.toLong() ?: 0L

        return OptionStrikeQuote(
            symbol = symbol,
            side = side,
            strikePrice = strike,
            expiryDate = expiry,
            runningPrice = runningPrice,
            change = asDouble(leaf["change"]) ?: 0.0,
            changePercent = asDouble(leaf["pChange"]) ?: 0.0,
            openInterest = oi,
            oiChange = oiChg,
            tradedContracts = asLong(leaf["totalTradedVolume"])
                ?: asLong(leaf["numberOfContractsTraded"])
                ?: 0L,
        )
    }

    private data class PcrMetrics(
        val pcr: Double?,
        val callToPutRatio: Double?,
        val pcrOiChangePct: Double?,
        val putOiChange: Long?,
        val callOiChange: Long?,
        val intradayDeltaPcr: Double?,
    )

    private fun computePcrMetrics(
        callsOi: Double,
        putsOi: Double,
        callsOiChg: Double,
        putsOiChg: Double,
        hasCalls: Boolean,
        hasPuts: Boolean,
    ): PcrMetrics {
        val pcr = if (callsOi > 0.0) putsOi / callsOi else null
        val callToPutRatio = if (putsOi > 0.0) callsOi / putsOi else null
        val prevCallsOi = callsOi - callsOiChg
        val prevPutsOi = putsOi - putsOiChg
        val prevPcr = if (prevCallsOi > 0.0 && prevPutsOi > 0.0) prevPutsOi / prevCallsOi else null
        val pcrOiChangePct = if (pcr != null && prevPcr != null && prevPcr > 0.0) {
            ((pcr - prevPcr) / prevPcr) * 100.0
        } else {
            null
        }
        val intradayDeltaPcr = if (callsOiChg > 0.0) putsOiChg / callsOiChg else null
        return PcrMetrics(
            pcr = pcr,
            callToPutRatio = callToPutRatio,
            pcrOiChangePct = pcrOiChangePct,
            putOiChange = if (hasPuts) putsOiChg.toLong() else null,
            callOiChange = if (hasCalls) callsOiChg.toLong() else null,
            intradayDeltaPcr = intradayDeltaPcr,
        )
    }

    private fun buildAtmBundle(
        expiry: String,
        ceQuotes: List<OptionStrikeQuote>,
        peQuotes: List<OptionStrikeQuote>,
        spotPrice: Double,
    ): AtmOptionBundle {
        val atmCe = pickAtmQuote(ceQuotes, spotPrice)
        val atmPe = pickAtmQuote(peQuotes, spotPrice)
        val nearbyCe = sliceAroundAtm(ceQuotes, atmCe?.strikePrice ?: spotPrice)
        val nearbyPe = sliceAroundAtm(peQuotes, atmPe?.strikePrice ?: spotPrice)
        val maxCallWall = ceQuotes.maxByOrNull { it.openInterest }?.takeIf { it.openInterest > 0L }
        val maxPutWall = peQuotes.maxByOrNull { it.openInterest }?.takeIf { it.openInterest > 0L }

        return AtmOptionBundle(
            expiryDate = expiry,
            atmCe = atmCe,
            atmPe = atmPe,
            nearbyStrikesCe = nearbyCe,
            nearbyStrikesPe = nearbyPe,
            maxCallOiStrike = maxCallWall?.strikePrice,
            maxCallOi = maxCallWall?.openInterest ?: 0L,
            maxPutOiStrike = maxPutWall?.strikePrice,
            maxPutOi = maxPutWall?.openInterest ?: 0L,
            allStrikesCe = ceQuotes,
            allStrikesPe = peQuotes,
        )
    }

    private fun pickAtmQuote(quotes: List<OptionStrikeQuote>, spotPrice: Double): OptionStrikeQuote? {
        if (quotes.isEmpty()) return null
        // Prefer active liquid strikes near spot; if exact ATM strike has 0 price & 0 OI, fall back cleanly
        val pricedOrActive = quotes.filter { it.runningPrice > 0.0 || it.openInterest > 0L }
        val pool = if (pricedOrActive.isNotEmpty()) pricedOrActive else quotes
        return pool.minWithOrNull(
            compareBy<OptionStrikeQuote> { abs(it.strikePrice - spotPrice) }
                .thenByDescending { it.runningPrice > 0.0 }
                .thenByDescending { it.openInterest }
        )
    }

    private fun sliceAroundAtm(quotes: List<OptionStrikeQuote>, atmStrike: Double): List<OptionStrikeQuote> {
        if (quotes.size <= 5) return quotes
        val atmIdx = quotes.indices.minByOrNull { abs(quotes[it].strikePrice - atmStrike) } ?: return quotes.take(5)
        val start = (atmIdx - 2).coerceIn(0, (quotes.size - 5).coerceAtLeast(0))
        val end = (start + 5).coerceAtMost(quotes.size)
        return quotes.subList(start, end)
    }

    private fun pickNearestExpiry(expiries: List<String>): String? {
        if (expiries.isEmpty()) return null
        val today = LocalDate.now(istZone)
        val valid = expiries.mapNotNull { exp ->
            val parsed = parseExpiryDate(exp)
            if (parsed == null || !parsed.isBefore(today)) {
                exp to (parsed?.toEpochDay() ?: Long.MAX_VALUE)
            } else {
                null
            }
        }
        return valid.minByOrNull { it.second }?.first ?: expiries.firstOrNull()
    }

    private fun parseExpiryDate(raw: String): LocalDate? {
        val clean = raw.trim()
        runCatching { return LocalDate.parse(clean, nseAltDateFormatter) }
        runCatching { return LocalDate.parse(clean, nseNumericDateFormatter) }
        runCatching { return LocalDate.parse(clean.take(10), isoDateFormatter) }
        return null
    }

    private fun findUnderlyingInChainRows(rows: List<Map<String, Any?>>): Double? {
        for (row in rows.take(10)) {
            asDouble(asMap(row["CE"])?.get("underlyingValue"))?.takeIf { it > 0.0 }?.let { return it }
            asDouble(asMap(row["PE"])?.get("underlyingValue"))?.takeIf { it > 0.0 }?.let { return it }
        }
        return null
    }

    private fun firstPositiveDouble(vararg values: Double?): Double {
        return values.firstOrNull { it != null && it > 0.0 } ?: 0.0
    }

    private fun parseRootMap(json: String): Map<String, Any?>? {
        if (!isLikelyJsonObject(json)) return null
        return runCatching { mapAdapter.fromJson(json) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMap(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    private fun asMapList(value: Any?): List<Map<String, Any?>> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { asMap(it) }
    }

    private fun asStringList(value: Any?): List<String> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { asString(it) }
    }

    private fun asString(value: Any?): String? {
        val s = value?.toString()?.trim()
        return if (s.isNullOrEmpty() || s == "-" || s == "null") null else s
    }

    private fun asDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }
        is String -> {
            val clean = value.replace(",", "").trim()
            if (clean.isEmpty() || clean == "-") null else clean.toDoubleOrNull()
        }
        else -> null
    }

    private fun asLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> {
            val clean = value.replace(",", "").trim()
            if (clean.isEmpty() || clean == "-") null else clean.toDoubleOrNull()?.toLong()
        }
        else -> null
    }
}
