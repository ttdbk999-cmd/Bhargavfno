package com.example

import com.example.domain.model.AtmOptionBundle
import com.example.domain.model.OptionSide
import com.example.domain.model.OptionStrikeQuote
import com.example.domain.model.StockScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtmOptionTest {

    @Test
    fun `atm option quote formats integer and decimal strikes cleanly`() {
        val intQuote = OptionStrikeQuote(
            symbol = "RELIANCE",
            side = OptionSide.CE,
            strikePrice = 1300.0,
            expiryDate = "28-Nov-2026",
            runningPrice = 42.35,
            change = 5.10,
            changePercent = 13.7,
            openInterest = 2_450_000L,
            oiChange = 320_000L,
        )
        val halfQuote = OptionStrikeQuote(
            symbol = "NMDC",
            side = OptionSide.PE,
            strikePrice = 67.5,
            expiryDate = "28-Nov-2026",
            runningPrice = 2.45,
            change = 0.35,
            changePercent = 16.6,
            openInterest = 1_100_000L,
            oiChange = 150_000L,
        )

        assertEquals("1300 CE", intQuote.contractLabel)
        assertEquals("67.5 PE", halfQuote.contractLabel)
    }

    @Test
    fun `stock scan result returns live atm quote when populated and falls back to computed strike`() {
        val ceQuote = OptionStrikeQuote(
            symbol = "MCX",
            side = OptionSide.CE,
            strikePrice = 3700.0,
            expiryDate = "28-Nov-2026",
            runningPrice = 118.5,
            change = 14.2,
            changePercent = 13.6,
            openInterest = 450_000L,
            oiChange = 40_000L,
        )
        val peQuote = OptionStrikeQuote(
            symbol = "MCX",
            side = OptionSide.PE,
            strikePrice = 3700.0,
            expiryDate = "28-Nov-2026",
            runningPrice = 94.0,
            change = -11.5,
            changePercent = -10.9,
            openInterest = 380_000L,
            oiChange = -12_000L,
        )

        val withOptions = sampleStock(price = 3688.0).copy(
            atmOptions = AtmOptionBundle(
                expiryDate = "28-Nov-2026",
                atmCe = ceQuote,
                atmPe = peQuote,
                nearbyStrikesCe = listOf(ceQuote),
                nearbyStrikesPe = listOf(peQuote),
            ),
        )

        assertEquals(ceQuote, withOptions.atmOptionFor(OptionSide.CE))
        assertEquals(peQuote, withOptions.atmOptionFor(OptionSide.PE))
        assertEquals(listOf(ceQuote), withOptions.nearbyOptionsFor(OptionSide.CE))
        assertEquals(listOf(peQuote), withOptions.nearbyOptionsFor(OptionSide.PE))
        assertEquals("3700 CE", withOptions.formattedComputedAtmStrike(OptionSide.CE))

        val withoutOptions = sampleStock(price = 276.4)
        assertNull(withoutOptions.atmOptionFor(OptionSide.CE))
        assertEquals(275.0, withoutOptions.computedAtmStrike(), 0.001)
        assertEquals("275 PE", withoutOptions.formattedComputedAtmStrike(OptionSide.PE))
    }

    private fun sampleStock(price: Double) = StockScanResult(
        symbol = "MCX",
        yahooTicker = "MCX.NS",
        companyName = "MCX",
        sector = "Exchanges",
        badgeColorHex = 0xFF546E7A,
        lastPrice = price,
        previousClose = price - 10.0,
        change = 10.0,
        changePercent = 1.0,
        sharesVolume = 200_000L,
        latest5mVolume = 15_000L,
        rvol = 2.0,
        rsi1h = 65.0,
        rsi30m = 66.0,
        rsi15m = 68.0,
        rsi5m = 71.0,
        candles5m = emptyList(),
        candles15m = emptyList(),
        candles30m = emptyList(),
        candles1h = emptyList(),
        rsi5mSeries = emptyList(),
        rsi15mSeries = emptyList(),
        rsi30mSeries = emptyList(),
        rsi1hSeries = emptyList(),
    )
}
