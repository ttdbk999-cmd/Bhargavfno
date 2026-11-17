package com.example.domain.indicator

import com.example.domain.model.DailyPositionSnapshot
import com.example.domain.model.FnoBuildupQuadrant
import com.example.domain.model.OiDeliverySummary
import com.example.domain.model.PositionAction
import java.util.Locale

object PositionSignalEvaluator {

    /**
     * Enriches daily snapshots (sorted newest-first) with Day-over-Day delivery changes
     * and computes the institutional Hold vs Sell recommendation from OI + Delivery + Price trend.
     */
    fun buildSummary(
        priceChangePct: Double,
        liveOi: Long?,
        liveOiChange: Long?,
        liveOiChangePct: Double?,
        livePcr: Double?,
        liveDeliveryQty: Long?,
        liveDeliveryPct: Double?,
        rawHistoryDesc: List<DailyPositionSnapshot>,
        liveCallToPutRatio: Double? = null,
        livePcrOiChangePct: Double? = null,
        livePutOiChange: Long? = null,
        liveCallOiChange: Long? = null,
        liveIntradayDeltaPcr: Double? = null,
    ): OiDeliverySummary {
        // Compute day-over-day delivery changes across the 5+ day history
        val enrichedHistory = rawHistoryDesc.mapIndexed { index, current ->
            val prev = rawHistoryDesc.getOrNull(index + 1)
            val delivQtyChgPct = if (prev != null && prev.deliveryQty > 0L && current.deliveryQty > 0L) {
                ((current.deliveryQty - prev.deliveryQty).toDouble() / prev.deliveryQty.toDouble()) * 100.0
            } else {
                null
            }
            val delivPctChgPp = if (prev != null && prev.deliveryPct > 0.0 && current.deliveryPct > 0.0) {
                current.deliveryPct - prev.deliveryPct
            } else {
                null
            }
            current.copy(
                deliveryQtyChangePctVsPrev = delivQtyChgPct,
                deliveryPctChangeVsPrev = delivPctChgPp,
            )
        }

        val todaySnap = enrichedHistory.firstOrNull()
        val yestSnap = enrichedHistory.getOrNull(1)

        val effectiveOi = liveOi ?: todaySnap?.openInterest?.takeIf { it > 0L }
        val effectiveOiChange = liveOiChange ?: todaySnap?.oiChange
        val effectiveOiChangePct = liveOiChangePct ?: todaySnap?.oiChangePct?.takeIf { effectiveOi != null }

        val effectiveDelivQty = liveDeliveryQty ?: todaySnap?.deliveryQty?.takeIf { it > 0L }
        val effectiveDelivPct = liveDeliveryPct ?: todaySnap?.deliveryPct?.takeIf { it > 0.0 }

        // Compare live/today delivery vs yesterday's archived delivery
        val baselineYest = when {
            yestSnap != null -> yestSnap
            todaySnap != null && liveDeliveryQty != null && todaySnap.deliveryQty != liveDeliveryQty -> todaySnap
            else -> null
        }

        val delivQtyChangePct = if (
            effectiveDelivQty != null &&
            effectiveDelivQty > 0L &&
            baselineYest != null &&
            baselineYest.deliveryQty > 0L
        ) {
            ((effectiveDelivQty - baselineYest.deliveryQty).toDouble() / baselineYest.deliveryQty.toDouble()) * 100.0
        } else {
            todaySnap?.deliveryQtyChangePctVsPrev
        }

        val delivPctChangePp = if (
            effectiveDelivPct != null &&
            effectiveDelivPct > 0.0 &&
            baselineYest != null &&
            baselineYest.deliveryPct > 0.0
        ) {
            effectiveDelivPct - baselineYest.deliveryPct
        } else {
            todaySnap?.deliveryPctChangeVsPrev
        }

        val oiChg = effectiveOiChange ?: 0L
        val hasOi = effectiveOi != null && effectiveOi > 0L

        val quadrant = when {
            !hasOi -> FnoBuildupQuadrant.NEUTRAL
            priceChangePct >= 0.0 && oiChg > 0L -> FnoBuildupQuadrant.LONG_BUILDUP
            priceChangePct >= 0.0 && oiChg < 0L -> FnoBuildupQuadrant.SHORT_COVERING
            priceChangePct < 0.0 && oiChg > 0L -> FnoBuildupQuadrant.SHORT_BUILDUP
            priceChangePct < 0.0 && oiChg < 0L -> FnoBuildupQuadrant.LONG_UNWINDING
            else -> FnoBuildupQuadrant.NEUTRAL
        }

        val avg5DayDelivPct = enrichedHistory
            .mapNotNull { it.deliveryPct.takeIf { pct -> pct > 0.0 } }
            .take(5)
            .takeIf { it.isNotEmpty() }
            ?.average()

        val deliveryRising = (delivPctChangePp != null && delivPctChangePp > 0.0) ||
            (delivQtyChangePct != null && delivQtyChangePct > 10.0) ||
            (effectiveDelivPct != null && avg5DayDelivPct != null && effectiveDelivPct > avg5DayDelivPct)

        val (action, headline, rationale) = when (quadrant) {
            FnoBuildupQuadrant.LONG_BUILDUP -> {
                if (deliveryRising) {
                    Triple(
                        PositionAction.STRONG_HOLD,
                        "STRONG HOLD CE • Institutional Longs",
                        buildRationale(effectiveOiChangePct, delivPctChangePp, "Price + OI + Delivery rising together"),
                    )
                } else {
                    Triple(
                        PositionAction.HOLD,
                        "HOLD CE • Long Buildup",
                        buildRationale(effectiveOiChangePct, delivPctChangePp, "Fresh futures longs added; monitor delivery"),
                    )
                }
            }

            FnoBuildupQuadrant.SHORT_COVERING -> {
                Triple(
                    PositionAction.TRAIL_PARTIAL,
                    "TRAIL / PARTIAL BOOK • Short Covering",
                    buildRationale(effectiveOiChangePct, delivPctChangePp, "Price up on falling OI — tighten trailing SL"),
                )
            }

            FnoBuildupQuadrant.SHORT_BUILDUP -> {
                if (deliveryRising) {
                    Triple(
                        PositionAction.SELL_EXIT,
                        "SELL LONGS / HOLD PE • Heavy Shorting",
                        buildRationale(effectiveOiChangePct, delivPctChangePp, "Price down with rising OI & delivery distribution"),
                    )
                } else {
                    Triple(
                        PositionAction.SELL_EXIT,
                        "SELL / HOLD PE • Short Buildup",
                        buildRationale(effectiveOiChangePct, delivPctChangePp, "Fresh bearish positions added in futures"),
                    )
                }
            }

            FnoBuildupQuadrant.LONG_UNWINDING -> {
                Triple(
                    PositionAction.SELL_EXIT,
                    "SELL / EXIT CE • Long Unwinding",
                    buildRationale(effectiveOiChangePct, delivPctChangePp, "Bulls exiting positions (Price & OI both falling)"),
                )
            }

            FnoBuildupQuadrant.NEUTRAL -> {
                if (effectiveDelivPct != null && delivPctChangePp != null) {
                    val act = if (priceChangePct >= 0.0 && deliveryRising) PositionAction.HOLD else PositionAction.WATCH
                    Triple(
                        act,
                        if (act == PositionAction.HOLD) "HOLD • Delivery Accumulation" else "WATCH • Awaiting OI Confirmation",
                        buildRationale(effectiveOiChangePct, delivPctChangePp, "Using 5-day NSE delivery trend"),
                    )
                } else {
                    Triple(
                        PositionAction.WATCH,
                        "Awaiting NSE OI & Delivery",
                        "Tap Sync or open detail sheet to load 5-day NSE OI & delivery history",
                    )
                }
            }
        }

        return OiDeliverySummary(
            openInterest = effectiveOi,
            oiChange = effectiveOiChange,
            oiChangePct = effectiveOiChangePct,
            pcr = livePcr,
            callToPutRatio = liveCallToPutRatio ?: livePcr?.takeIf { it > 0.0 }?.let { 1.0 / it },
            pcrOiChangePct = livePcrOiChangePct,
            putOiChange = livePutOiChange,
            callOiChange = liveCallOiChange,
            intradayDeltaPcr = liveIntradayDeltaPcr,
            deliveryQty = effectiveDelivQty,
            deliveryPct = effectiveDelivPct,
            deliveryQtyChangePct = delivQtyChangePct,
            deliveryPctChangePp = delivPctChangePp,
            buildupQuadrant = quadrant,
            positionAction = action,
            positionHeadline = headline,
            positionRationale = rationale,
            history5Days = enrichedHistory.take(7),
        )
    }

    private fun buildRationale(
        oiChgPct: Double?,
        delivPctPp: Double?,
        baseNote: String,
    ): String {
        val parts = mutableListOf(baseNote)
        if (oiChgPct != null) {
            val sign = if (oiChgPct >= 0) "+" else ""
            parts.add("OI $sign${String.format(Locale.US, "%.1f%%", oiChgPct)}")
        }
        if (delivPctPp != null) {
            val sign = if (delivPctPp >= 0) "+" else ""
            parts.add("Deliv $sign${String.format(Locale.US, "%.1f", delivPctPp)}pp vs Yest")
        }
        return parts.joinToString(" • ")
    }
}
