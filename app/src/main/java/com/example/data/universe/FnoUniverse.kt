package com.example.data.universe

import com.example.domain.model.FnoTickerMeta
import kotlin.math.abs

object FnoUniverse {

    val defaultFnoStocks: List<FnoTickerMeta> = listOf(
        // Stocks featured in user's CE / PE Gapboard screenshots + Core NSE F&O heavyweights
        FnoTickerMeta("POWERINDIA", "Hitachi Energy India", "Power Equip", 0xFFE53935),
        FnoTickerMeta("AMBER", "Amber Enterprises", "Consumer Durables", 0xFF1E88E5),
        FnoTickerMeta("MCX", "Multi Commodity Exchange", "Exchanges", 0xFF546E7A),
        FnoTickerMeta("ABB", "ABB India Ltd", "Capital Goods", 0xFFD81B60),
        FnoTickerMeta("AUROPHARMA", "Aurobindo Pharma", "Pharma", 0xFF3949AB),
        FnoTickerMeta("SUNPHARMA", "Sun Pharma Inds", "Pharma", 0xFFFB8C00),
        FnoTickerMeta("LAURUSLABS", "Laurus Labs Ltd", "Pharma", 0xFF00897B),
        FnoTickerMeta("OIL", "Oil India Ltd", "Oil & Gas", 0xFF43A047),
        FnoTickerMeta("APOLLOHOSP", "Apollo Hospitals", "Healthcare", 0xFF039BE5),
        FnoTickerMeta("PAGEIND", "Page Industries", "Textiles", 0xFF6D4C41),
        FnoTickerMeta("ADANIENSOL", "Adani Energy Solutions", "Utilities", 0xFF7B1FA2),
        FnoTickerMeta("ADANIGREEN", "Adani Green Energy", "Renewables", 0xFF2E7D32),
        FnoTickerMeta("ONGC", "Oil & Natural Gas Corp", "Oil & Gas", 0xFFC62828),
        FnoTickerMeta("MANKIND", "Mankind Pharma", "Pharma", 0xFF1565C0),
        FnoTickerMeta("DRREDDY", "Dr. Reddy's Labs", "Pharma", 0xFF5E35B1),
        FnoTickerMeta("CIPLA", "Cipla Ltd", "Pharma", 0xFF00ACC1),
        FnoTickerMeta("PATANJALI", "Patanjali Foods", "FMCG", 0xFFEF6C00),
        FnoTickerMeta("COFORGE", "Coforge Ltd", "IT Services", 0xFFE65100),
        FnoTickerMeta("PNB", "Punjab National Bank", "PSU Bank", 0xFF8E24AA),
        FnoTickerMeta("NAM-INDIA", "Nippon Life AMC", "Asset Mgmt", 0xFF455A64),
        FnoTickerMeta("ALKEM", "Alkem Laboratories", "Pharma", 0xFF1976D2),
        FnoTickerMeta("KALYANKJIL", "Kalyan Jewellers", "Retail", 0xFFE53935),
        FnoTickerMeta("INOXWIND", "Inox Wind Ltd", "Renewables", 0xFF00838F),
        FnoTickerMeta("SHRIRAMFIN", "Shriram Finance", "NBFC", 0xFFFBC02D),
        FnoTickerMeta("NMDC", "NMDC Ltd", "Metals & Mining", 0xFF1E88E5),
        FnoTickerMeta("LTF", "L&T Finance Ltd", "NBFC", 0xFFFDD835),
        FnoTickerMeta("IDEA", "Vodafone Idea Ltd", "Telecom", 0xFFEF5350),
        FnoTickerMeta("ABCAPITAL", "Aditya Birla Capital", "Financials", 0xFFD32F2F),
        FnoTickerMeta("BPCL", "Bharat Petroleum", "Oil & Gas", 0xFF7CB342),
        FnoTickerMeta("ASHOKLEY", "Ashok Leyland", "Auto", 0xFF0288D1),
        FnoTickerMeta("MUTHOOTFIN", "Muthoot Finance", "NBFC", 0xFFD81B60),
        FnoTickerMeta("SBICARD", "SBI Cards", "Financials", 0xFF1976D2),
        FnoTickerMeta("INDIGO", "InterGlobe Aviation", "Aviation", 0xFF283593),
        FnoTickerMeta("POLICYBZR", "PB Fintech", "Fintech", 0xFF039BE5),
        FnoTickerMeta("PIDILITIND", "Pidilite Industries", "Chemicals", 0xFF1E88E5),
        FnoTickerMeta("INDUSINDBK", "IndusInd Bank", "Private Bank", 0xFFC62828),
        FnoTickerMeta("HDFCBANK", "HDFC Bank Ltd", "Private Bank", 0xFF1565C0),
        FnoTickerMeta("M&M", "Mahindra & Mahindra", "Auto", 0xFFD32F2F),
        FnoTickerMeta("SWIGGY", "Swiggy Ltd", "Consumer Tech", 0xFFFB8C00),
        FnoTickerMeta("CHOLAFIN", "Cholamandalam Inv", "NBFC", 0xFF00796B),
        FnoTickerMeta("KPITTECH", "KPIT Technologies", "IT Services", 0xFF9E9D24),
        FnoTickerMeta("IREDA", "IREDA Ltd", "Power Finance", 0xFF3949AB),
        FnoTickerMeta("GLENMARK", "Glenmark Pharma", "Pharma", 0xFFE53935),
        // Core Nifty / BankNifty F&O Leaders
        FnoTickerMeta("RELIANCE", "Reliance Industries", "Energy", 0xFF0277BD),
        FnoTickerMeta("TCS", "Tata Consultancy", "IT Services", 0xFF6A1B9A),
        FnoTickerMeta("INFY", "Infosys Ltd", "IT Services", 0xFF1976D2),
        FnoTickerMeta("ICICIBANK", "ICICI Bank Ltd", "Private Bank", 0xFFE65100),
        FnoTickerMeta("SBIN", "State Bank of India", "PSU Bank", 0xFF00ACC1),
        FnoTickerMeta("BAJFINANCE", "Bajaj Finance", "NBFC", 0xFF1565C0),
        FnoTickerMeta("HAL", "Hindustan Aeronautics", "Defence", 0xFF2E7D32),
        FnoTickerMeta("BEL", "Bharat Electronics", "Defence", 0xFF00897B),
        FnoTickerMeta("TRENT", "Trent Ltd", "Retail", 0xFF37474F),
        FnoTickerMeta("DIXON", "Dixon Technologies", "Electronics", 0xFF5E35B1),
    )

    private val bySymbolMap: Map<String, FnoTickerMeta> = defaultFnoStocks.associateBy { it.symbol }

    private val paletteColors = listOf(
        0xFF1E88E5, 0xFF00897B, 0xFFE53935, 0xFF8E24AA,
        0xFFFB8C00, 0xFF3949AB, 0xFFD81B60, 0xFF43A047,
    )

    fun resolveMeta(symbol: String): FnoTickerMeta {
        val clean = symbol.trim().uppercase().removeSuffix(".NS").removeSuffix(".BO")
        return bySymbolMap[clean] ?: FnoTickerMeta(
            symbol = clean,
            companyName = "$clean NSE F&O",
            sector = "F&O Watchlist",
            badgeColorHex = paletteColors[abs(clean.hashCode()) % paletteColors.size],
        )
    }

    val defaultSymbols: List<String> = defaultFnoStocks.map { it.symbol }
}
