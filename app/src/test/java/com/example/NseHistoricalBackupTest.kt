package com.example

import com.example.data.local.DailyFnoMetricDao
import com.example.data.local.DailyFnoMetricEntity
import com.example.data.remote.NseHistoricalEquityRow
import com.example.data.remote.NseHistoricalFoRow
import com.example.data.remote.NseNetworkModule
import com.example.data.remote.NseOptionChainParser
import com.example.data.repository.NseOiDeliveryRepository
import com.example.domain.indicator.PositionSignalEvaluator
import com.example.domain.indicator.PositionalAnalyzer
import com.example.domain.model.PivotLevels
import com.example.domain.model.StockScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NseHistoricalBackupTest {

    private val repo = NseOiDeliveryRepository(
        nseApi = NseNetworkModule.nseApi,
        dao = NoOpMetricDao(),
        browserClient = null,
    )

    @Test
    fun `parses NextApi nested securityWiseDP delivery json with comma formatted numbers`() {
        val nextApiJson = """
            {
              "equityResponse": [
                {
                  "secInfo": { "symbol": "COFORGE" },
                  "securityWiseDP": {
                    "quantityTraded": "1,450,200",
                    "deliveryQuantity": "685,420",
                    "deliveryToTradedQuantity": "47.26"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = repo.parseDeliveryPairFromJson(nextApiJson)
        assertNotNull(parsed)
        assertEquals(685_420L, parsed!!.first)
        assertEquals(47.26, parsed.second, 0.01)
    }

    @Test
    fun `parses historicalOR equity and foCPV rows with mTIMESTAMP and string decimals`() {
        val eqJson = """
            {
              "data": [
                {
                  "mTIMESTAMP": "16-Nov-2026",
                  "CH_CLOSING_PRICE": "2,041.00",
                  "CH_PREVIOUS_CLS_PRICE": "2,010.00",
                  "CH_TOT_TRADED_QTY": "1,850,000",
                  "COP_DELIV_QTY": "925,000",
                  "COP_DELIV_PERC": "50.00"
                }
              ]
            }
        """.trimIndent()

        val foJson = """
            {
              "data": [
                {
                  "mTIMESTAMP": "16-Nov-2026",
                  "FH_OPEN_INT": "4,250,000.0",
                  "FH_CHANGE_IN_OI": "315,000",
                  "FH_CLOSING_PRICE": "2,045.50",
                  "FH_TOT_TRADED_QTY": "120,000"
                }
              ]
            }
        """.trimIndent()

        val eqRows = repo.parseHistoricalEquityRows(eqJson)
        val foRows = repo.parseHistoricalFoRows(foJson)
        assertEquals(1, eqRows.size)
        assertEquals(1, foRows.size)

        val merged = repo.mergeHistoricalRows(
            symbol = "COFORGE",
            equityRows = eqRows,
            foRows = foRows,
            existingEntities = emptyList(),
        )
        assertEquals(1, merged.size)
        assertEquals("2026-11-16", merged[0].tradeDate)
        assertEquals(4_250_000L, merged[0].openInterest)
        assertEquals(315_000L, merged[0].oiChange)
        assertEquals(925_000L, merged[0].deliveryQty)
        assertEquals(50.0, merged[0].deliveryPct, 0.01)
    }

    @Test
    fun `parses Futures OI from NextApi getSymbolDerivativesData and quote-derivative including FUTIDX`() {
        val nextDerivJson = """
            {
              "underlyingValue": 2041.0,
              "data": [
                {
                  "instrumentType": "FUTSTK",
                  "optionType": "XX",
                  "expiryDate": "26-Nov-2026",
                  "openInterest": 3800000,
                  "changeinOpenInterest": 240000,
                  "lastPrice": 2046.0
                }
              ]
            }
        """.trimIndent()

        val parsedNext = NseOptionChainParser.parse(nextDerivJson, "COFORGE", 2041.0)
        assertNotNull(parsedNext)
        assertEquals(3_800_000L, parsedNext!!.futOi)
        assertEquals(240_000L, parsedNext.futOiChange)

        val quoteDerivIdxJson = """
            {
              "underlyingValue": 24500.0,
              "stocks": [
                {
                  "metadata": {
                    "instrumentType": "FUTIDX",
                    "optionType": "-",
                    "expiryDate": "26-Nov-2026",
                    "lastPrice": 24550.0
                  },
                  "marketDeptOrderBook": {
                    "tradeInfo": {
                      "openInterest": 12500000,
                      "changeinOpenInterest": 450000
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val parsedIdx = NseOptionChainParser.parse(quoteDerivIdxJson, "NIFTY", 24500.0)
        assertNotNull(parsedIdx)
        assertEquals(12_500_000L, parsedIdx!!.futOi)
        assertEquals(450_000L, parsedIdx.futOiChange)
    }

    @Test
    fun `retains 5 plus prior days backup non-destructively and computes missing sequential OI change pct`() {
        val existingBackup = listOf(
            DailyFnoMetricEntity(
                symbol = "RELIANCE",
                tradeDate = "2026-11-11",
                closePrice = 1280.0,
                priceChangePct = 0.4,
                tradedQty = 5_000_000L,
                openInterest = 10_000_000L,
                oiChange = 200_000L,
                oiChangePct = 2.04,
                deliveryQty = 2_000_000L,
                deliveryPct = 40.0,
                updatedAtMs = 1L,
            ),
            DailyFnoMetricEntity(
                symbol = "RELIANCE",
                tradeDate = "2026-11-12",
                closePrice = 1290.0,
                priceChangePct = 0.78,
                tradedQty = 5_200_000L,
                openInterest = 10_500_000L,
                oiChange = 500_000L,
                oiChangePct = 5.0,
                deliveryQty = 2_184_000L,
                deliveryPct = 42.0,
                updatedAtMs = 2L,
            ),
        )

        // Newly fetched equity rows for 11-13 .. 11-17 (omitting 11-11 & 11-12)
        val eqRows = listOf(
            NseHistoricalEquityRow("13-Nov-2026", 1300.0, 1290.0, 6_000_000L, 2_700_000L, 45.0),
            NseHistoricalEquityRow("14-Nov-2026", 1312.0, 1300.0, 6_500_000L, 3_055_000L, 47.0),
            NseHistoricalEquityRow("15-Nov-2026", 1318.0, 1312.0, 6_200_000L, 2_790_000L, 45.0),
            NseHistoricalEquityRow("16-Nov-2026", 1330.0, 1318.0, 7_000_000L, 3_500_000L, 50.0),
        )

        // FO rows: note 2026-11-13 & 2026-11-14 omit FH_CHANGE_IN_OI (0L) so repo must derive from prior day's OI
        val foRows = listOf(
            NseHistoricalFoRow("13-Nov-2026", 11_000_000L, 0L, 1302.0, 100_000L),
            NseHistoricalFoRow("14-Nov-2026", 12_100_000L, 0L, 1314.0, 120_000L),
            NseHistoricalFoRow("15-Nov-2026", 12_400_000L, 300_000L, 1320.0, 90_000L),
            NseHistoricalFoRow("16-Nov-2026", 0L, 0L, 1331.0, 0L), // 0 OI must NOT crash or compute bogus -100%
        )

        val merged = repo.mergeHistoricalRows(
            symbol = "RELIANCE",
            equityRows = eqRows,
            foRows = foRows,
            existingEntities = existingBackup,
            maxDays = 15,
        )

        // All 6 days (11-11 through 11-16) must be preserved
        assertEquals(6, merged.size)
        assertEquals("2026-11-16", merged[0].tradeDate)
        assertEquals("2026-11-11", merged[5].tradeDate)

        // Prior backup day 2026-11-12 preserved intact even though absent from latest API batch
        val nov12 = merged.first { it.tradeDate == "2026-11-12" }
        assertEquals(42.0, nov12.deliveryPct, 0.01)
        assertEquals(10_500_000L, nov12.openInterest)

        // 2026-11-13 derives +500k (+4.76%) from 10.5M -> 11.0M when FH_CHANGE_IN_OI was 0
        val nov13 = merged.first { it.tradeDate == "2026-11-13" }
        assertEquals(500_000L, nov13.oiChange)
        assertEquals((500_000.0 / 10_500_000.0) * 100.0, nov13.oiChangePct, 0.05)

        // 2026-11-14 derives +1.1M (+10.0%) from 11.0M -> 12.1M
        val nov14 = merged.first { it.tradeDate == "2026-11-14" }
        assertEquals(1_100_000L, nov14.oiChange)
        assertEquals(10.0, nov14.oiChangePct, 0.05)

        // Evaluate 5+ day Delivery Change & Positional Candidate backup flags
        val domainHistory = merged.map { it.toDomain() }
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 0.9,
            liveOi = 13_000_000L,
            liveOiChange = 600_000L,
            liveOiChangePct = 4.84,
            livePcr = 1.18,
            liveDeliveryQty = 3_600_000L,
            liveDeliveryPct = 51.0,
            rawHistoryDesc = domainHistory,
        )
        assertTrue(summary.history5Days.size >= 5)
        // Each day except oldest should have deliveryPctChangeVsPrev & deliveryQtyChangePctVsPrev computed
        val latestSnap = summary.history5Days.first()
        assertNotNull(latestSnap.deliveryPctChangeVsPrev)
        assertNotNull(latestSnap.deliveryQtyChangePctVsPrev)
        assertEquals(5.0, latestSnap.deliveryPctChangeVsPrev!!, 0.01) // 50.0 - 45.0

        val stock = StockScanResult(
            symbol = "RELIANCE",
            yahooTicker = "RELIANCE.NS",
            companyName = "Reliance Industries",
            sector = "Energy",
            badgeColorHex = 0xFF2962FF,
            lastPrice = 1335.0,
            previousClose = 1323.0,
            change = 12.0,
            changePercent = 0.91,
            sharesVolume = 7_000_000L,
            latest5mVolume = 120_000L,
            rvol = 1.6,
            rsi1h = 62.0,
            rsi30m = 61.0,
            rsi15m = 60.0,
            rsi5m = 62.0,
            candles5m = emptyList(),
            candles15m = emptyList(),
            candles30m = emptyList(),
            candles1h = emptyList(),
            rsi5mSeries = emptyList(),
            rsi15mSeries = emptyList(),
            rsi30mSeries = emptyList(),
            rsi1hSeries = emptyList(),
            oiDelivery = summary,
            pivotLevels = PivotLevels(1332.0, 1312.0, 1323.0, 1322.0, 1333.0, 1342.0, 1362.0, 1313.0, 1302.0, 1282.0),
        )
        val scored = PositionalAnalyzer.scoreStock(stock)
        assertNotNull(scored)
        assertTrue(scored!!.hasFiveDayBackup)
        assertTrue(scored.backedUpDaysCount >= 5)

        val partialScored = PositionalAnalyzer.scoreStock(
            stock.copy(
                oiDelivery = summary.copy(history5Days = domainHistory.take(2)),
            ),
        )
        assertNotNull(partialScored)
        assertFalse(partialScored!!.hasFiveDayBackup)
        assertEquals(2, partialScored.backedUpDaysCount)
    }

    private class NoOpMetricDao : DailyFnoMetricDao {
        override fun observeSymbolHistory(symbol: String, limit: Int): Flow<List<DailyFnoMetricEntity>> = flowOf(emptyList())
        override suspend fun getSymbolHistoryOnce(symbol: String, limit: Int): List<DailyFnoMetricEntity> = emptyList()
        override fun observeAllHistory(): Flow<List<DailyFnoMetricEntity>> = flowOf(emptyList())
        override suspend fun upsertAll(entries: List<DailyFnoMetricEntity>) = Unit
        override suspend fun pruneSymbolOlderThan(symbol: String, keepDays: Int) = Unit
    }
}
