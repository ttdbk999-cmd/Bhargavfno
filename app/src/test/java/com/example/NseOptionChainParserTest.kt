package com.example

import com.example.data.remote.NseOptionChainParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NseOptionChainParserTest {

    @Test
    fun `parses option-chain-v3 JSON for OIL ATM CE and PE running prices and PCR`() {
        val contractInfoJson = """
            {
              "expiryDates": ["26-NOV-2026", "31-DEC-2026"],
              "strikePrices": [420, 430, 440, 450, 460]
            }
        """.trimIndent()
        val nearest = NseOptionChainParser.parseContractInfoNearestExpiry(contractInfoJson)
        assertEquals("26-NOV-2026", nearest)

        val v3Json = """
            {
              "records": {
                "expiryDates": ["26-NOV-2026", "31-DEC-2026"],
                "underlyingValue": 437.75,
                "data": [
                  {
                    "strikePrice": 430,
                    "expiryDate": "26-NOV-2026",
                    "CE": {
                      "strikePrice": 430,
                      "expiryDate": "26-NOV-2026",
                      "openInterest": 10000,
                      "changeinOpenInterest": 1000,
                      "lastPrice": 18.4,
                      "change": 1.2,
                      "pChange": 6.98,
                      "totalTradedVolume": 4500
                    },
                    "PE": {
                      "strikePrice": 430,
                      "expiryDate": "26-NOV-2026",
                      "openInterest": 14000,
                      "changeinOpenInterest": 2500,
                      "lastPrice": 9.8,
                      "change": -0.6,
                      "pChange": -5.77,
                      "totalTradedVolume": 5200
                    }
                  },
                  {
                    "strikePrice": 440,
                    "expiryDate": "26-NOV-2026",
                    "CE": {
                      "strikePrice": 440,
                      "expiryDate": "26-NOV-2026",
                      "openInterest": 25000,
                      "changeinOpenInterest": 3000,
                      "lastPrice": 12.65,
                      "change": -0.85,
                      "pChange": -6.30,
                      "totalTradedVolume": 12800
                    },
                    "PE": {
                      "strikePrice": 440,
                      "expiryDate": "26-NOV-2026",
                      "openInterest": 31000,
                      "changeinOpenInterest": 6000,
                      "lastPrice": 14.30,
                      "change": 1.45,
                      "pChange": 11.28,
                      "totalTradedVolume": 16200
                    }
                  },
                  {
                    "strikePrice": 450,
                    "expiryDate": "26-NOV-2026",
                    "CE": {
                      "strikePrice": 450,
                      "expiryDate": "26-NOV-2026",
                      "openInterest": 35000,
                      "changeinOpenInterest": 5000,
                      "lastPrice": 7.90,
                      "change": -0.40,
                      "pChange": -4.82,
                      "totalTradedVolume": 9000
                    },
                    "PE": {
                      "strikePrice": 450,
                      "expiryDate": "26-NOV-2026",
                      "openInterest": 18000,
                      "changeinOpenInterest": 1500,
                      "lastPrice": 21.10,
                      "change": 2.10,
                      "pChange": 11.05,
                      "totalTradedVolume": 4100
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = NseOptionChainParser.parse(v3Json, symbol = "OIL", fallbackSpotPrice = 437.75)
        assertNotNull(parsed)
        parsed!!

        assertEquals("26-NOV-2026", parsed.atmOptions.expiryDate)
        assertNotNull(parsed.atmOptions.atmCe)
        assertNotNull(parsed.atmOptions.atmPe)
        assertEquals(440.0, parsed.atmOptions.atmCe!!.strikePrice, 0.001)
        assertEquals(12.65, parsed.atmOptions.atmCe!!.runningPrice, 0.001)
        assertEquals(440.0, parsed.atmOptions.atmPe!!.strikePrice, 0.001)
        assertEquals(14.30, parsed.atmOptions.atmPe!!.runningPrice, 0.001)

        assertEquals(3, parsed.atmOptions.nearbyStrikesCe.size)
        assertEquals(450.0, parsed.atmOptions.maxCallOiStrike!!, 0.001)
        assertEquals(440.0, parsed.atmOptions.maxPutOiStrike!!, 0.001)

        // Calls OI = 70000, Puts OI = 63000 -> PCR = 0.90
        assertEquals(0.90, parsed.pcr!!, 0.001)
        assertEquals(70000.0 / 63000.0, parsed.callToPutRatio!!, 0.001)
        assertTrue((parsed.pcrOiChangePct ?: 0.0) > 0.0)
    }

    @Test
    fun `parses NextApi getSymbolDerivativesData format with bid-ask midpoint fallback`() {
        val nextApiJson = """
            {
              "data": [
                {
                  "instrumentType": "FUTSTK",
                  "openInterest": 500000,
                  "changeinOpenInterest": 25000,
                  "lastPrice": 438.5
                },
                {
                  "strikePrice": 440,
                  "expiryDate": "26-Nov-2026",
                  "CE": {
                    "strikePrice": 440,
                    "expiryDate": "26-Nov-2026",
                    "openInterest": 12000,
                    "changeinOpenInterest": 1500,
                    "lastPrice": 0,
                    "bidprice": 12.40,
                    "askPrice": 12.80,
                    "underlyingValue": 437.75
                  },
                  "PE": {
                    "strikePrice": 440,
                    "expiryDate": "26-Nov-2026",
                    "openInterest": 18000,
                    "changeinOpenInterest": 3000,
                    "lastPrice": 14.10,
                    "underlyingValue": 437.75
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = NseOptionChainParser.parse(nextApiJson, symbol = "OIL", fallbackSpotPrice = 437.75)
        assertNotNull(parsed)
        parsed!!

        assertEquals(500000L, parsed.futOi)
        assertEquals(12.60, parsed.atmOptions.atmCe!!.runningPrice, 0.001)
        assertEquals(14.10, parsed.atmOptions.atmPe!!.runningPrice, 0.001)
        assertEquals(1.50, parsed.pcr!!, 0.001)
    }
}
