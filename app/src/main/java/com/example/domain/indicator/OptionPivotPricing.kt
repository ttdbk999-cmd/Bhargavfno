package com.example.domain.indicator

import com.example.domain.model.OptionSide
import com.example.domain.model.OptionStrikeQuote
import com.example.domain.model.StockScanResult
import kotlin.math.abs
import kotlin.math.pow

data class PivotOptionProjection(
    val levelLabel: String,
    val side: OptionSide,
    val spotLevelPrice: Double,
    val distFromSpotPct: Double,
    val isPriorityLevel: Boolean,
    val projectedAtmOptionPrice: Double?,
    val atmContractLabel: String,
    val nearestStrikeQuote: OptionStrikeQuote?,
)

/**
 * Computes option prices corresponding to Standard Floor Pivots (R1, R2, R3, PP, S1, S2, S3)
 * using live NSE option-chain quotes:
 * 1. [projectedAtmOptionPrice]: Estimates what the current ATM CE (for R1-R3) or ATM PE (for S1-S3)
 *    will trade at when the underlying spot reaches each pivot level, derived from moneyness-shifted
 *    interpolation across the live option chain (automatically reflecting real IV skew & delta).
 * 2. [nearestStrikeQuote]: Live running LTP of the option strike closest to that pivot level.
 */
object OptionPivotPricing {

    fun buildPivotProjections(
        stock: StockScanResult,
        activeSide: OptionSide,
    ): List<PivotOptionProjection> {
        val pivots = stock.pivotLevels ?: return emptyList()
        val bundle = stock.atmOptions
        val ceChain = bundle.allStrikesCe.ifEmpty { bundle.nearbyStrikesCe }
        val peChain = bundle.allStrikesPe.ifEmpty { bundle.nearbyStrikesPe }

        val entries = listOf(
            Triple("R3", pivots.r3, OptionSide.CE),
            Triple("R2", pivots.r2, OptionSide.CE),
            Triple("R1", pivots.r1, OptionSide.CE),
            Triple("PP", pivots.pp, activeSide),
            Triple("S1", pivots.s1, OptionSide.PE),
            Triple("S2", pivots.s2, OptionSide.PE),
            Triple("S3", pivots.s3, OptionSide.PE),
        )

        return entries.map { (label, levelPrice, levelSide) ->
            val atmQuote = if (levelSide == OptionSide.CE) bundle.atmCe else bundle.atmPe
            val chain = if (levelSide == OptionSide.CE) ceChain else peChain
            val projected = estimateAtmOptionPriceAtSpot(
                spotPrice = stock.lastPrice,
                targetSpotPrice = levelPrice,
                atmQuote = atmQuote,
                chainQuotes = chain,
                side = levelSide,
            )
            val nearestStrike = findNearestStrikeQuote(
                targetSpotPrice = levelPrice,
                chainQuotes = chain,
            )
            val distPct = if (stock.lastPrice > 0.0) {
                ((levelPrice - stock.lastPrice) / stock.lastPrice) * 100.0
            } else {
                0.0
            }
            PivotOptionProjection(
                levelLabel = label,
                side = levelSide,
                spotLevelPrice = levelPrice,
                distFromSpotPct = distPct,
                isPriorityLevel = label in PRIORITY_LABELS,
                projectedAtmOptionPrice = projected,
                atmContractLabel = atmQuote?.contractLabel ?: stock.formattedComputedAtmStrike(levelSide),
                nearestStrikeQuote = nearestStrike,
            )
        }
    }

    fun estimateAtmOptionPriceAtSpot(
        spotPrice: Double,
        targetSpotPrice: Double,
        atmQuote: OptionStrikeQuote?,
        chainQuotes: List<OptionStrikeQuote>,
        side: OptionSide,
    ): Double? {
        if (atmQuote == null || atmQuote.runningPrice <= 0.0) return null
        if (spotPrice <= 0.0 || targetSpotPrice <= 0.0) return atmQuote.runningPrice

        val spotDelta = targetSpotPrice - spotPrice
        if (abs(spotDelta) < 1e-6) return atmQuote.runningPrice

        // Equivalent strike at current spot preserving identical moneyness (S_target - K_atm == S_now - K_eq)
        val eqStrike = atmQuote.strikePrice - spotDelta
        val priced = chainQuotes
            .filter { it.runningPrice > 0.0 && it.strikePrice > 0.0 }
            .sortedBy { it.strikePrice }

        val intrinsicAtTarget = when (side) {
            OptionSide.CE -> (targetSpotPrice - atmQuote.strikePrice).coerceAtLeast(0.0)
            OptionSide.PE -> (atmQuote.strikePrice - targetSpotPrice).coerceAtLeast(0.0)
        }

        val estimated = if (priced.size >= 2) {
            interpolateAcrossChain(
                eqStrike = eqStrike,
                priced = priced,
                side = side,
            )
        } else {
            val favorableMove = if (side == OptionSide.CE) spotDelta else -spotDelta
            (atmQuote.runningPrice + favorableMove * 0.50).coerceAtLeast(0.05)
        }

        return maxOf(estimated, intrinsicAtTarget, 0.05)
    }

    fun findNearestStrikeQuote(
        targetSpotPrice: Double,
        chainQuotes: List<OptionStrikeQuote>,
    ): OptionStrikeQuote? {
        if (chainQuotes.isEmpty() || targetSpotPrice <= 0.0) return null
        val active = chainQuotes.filter { it.runningPrice > 0.0 || it.openInterest > 0L }
        val pool = if (active.isNotEmpty()) active else chainQuotes
        return pool.minWithOrNull(
            compareBy<OptionStrikeQuote> { abs(it.strikePrice - targetSpotPrice) }
                .thenByDescending { it.runningPrice > 0.0 }
                .thenByDescending { it.openInterest }
        )
    }

    private fun interpolateAcrossChain(
        eqStrike: Double,
        priced: List<OptionStrikeQuote>,
        side: OptionSide,
    ): Double {
        // 1. Inside live strike range: piecewise linear interpolation
        for (i in 0 until priced.lastIndex) {
            val low = priced[i]
            val high = priced[i + 1]
            if (eqStrike >= low.strikePrice && eqStrike <= high.strikePrice) {
                val span = (high.strikePrice - low.strikePrice).coerceAtLeast(0.01)
                val t = ((eqStrike - low.strikePrice) / span).coerceIn(0.0, 1.0)
                return low.runningPrice + t * (high.runningPrice - low.runningPrice)
            }
        }

        // 2. Beyond lower strike edge (eqStrike < priced.first().strikePrice)
        // For CE this is deeper ITM (higher option price); for PE this is deeper OTM (decays smoothly)
        if (eqStrike < priced.first().strikePrice) {
            val first = priced[0]
            val second = priced[1]
            val span = (second.strikePrice - first.strikePrice).coerceAtLeast(0.01)
            val distBelow = first.strikePrice - eqStrike
            return if (side == OptionSide.CE) {
                val edgeDelta = ((first.runningPrice - second.runningPrice) / span).coerceIn(0.50, 0.95)
                first.runningPrice + distBelow * edgeDelta
            } else {
                val decaySteps = (distBelow / span).coerceAtMost(8.0)
                (first.runningPrice * 0.65.pow(decaySteps)).coerceAtLeast(0.05)
            }
        }

        // 3. Beyond upper strike edge (eqStrike > priced.last().strikePrice)
        // For PE this is deeper ITM (higher option price); for CE this is deeper OTM (decays smoothly)
        val last = priced.last()
        val prev = priced[priced.lastIndex - 1]
        val span = (last.strikePrice - prev.strikePrice).coerceAtLeast(0.01)
        val distAbove = eqStrike - last.strikePrice
        return if (side == OptionSide.PE) {
            val edgeDelta = ((last.runningPrice - prev.runningPrice) / span).coerceIn(0.50, 0.95)
            last.runningPrice + distAbove * edgeDelta
        } else {
            val decaySteps = (distAbove / span).coerceAtMost(8.0)
            (last.runningPrice * 0.65.pow(decaySteps)).coerceAtLeast(0.05)
        }
    }

    private val PRIORITY_LABELS = setOf("R2", "R3", "S2", "S3")
}
