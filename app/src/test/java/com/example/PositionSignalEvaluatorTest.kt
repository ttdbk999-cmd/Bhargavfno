package com.example

import com.example.domain.indicator.PositionSignalEvaluator
import com.example.domain.model.DailyPositionSnapshot
import com.example.domain.model.FnoBuildupQuadrant
import com.example.domain.model.PositionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PositionSignalEvaluatorTest {

    private val sample5DayHistory = listOf(
        DailyPositionSnapshot("2026-11-13", 3676.7, 1.43, 303_040, 12_400_000, 850_000, 7.36, 145_000, 47.8),
        DailyPositionSnapshot("2026-11-12", 3624.7, 0.82, 240_000, 11_550_000, 420_000, 3.77, 102_000, 42.5),
        DailyPositionSnapshot("2026-11-11", 3595.2, 0.45, 210_000, 11_130_000, 210_000, 1.92, 86_000, 40.9),
        DailyPositionSnapshot("2026-11-10", 3579.1, -0.30, 195_000, 10_920_000, -110_000, -1.00, 78_000, 40.0),
        DailyPositionSnapshot("2026-11-09", 3589.8, 1.10, 225_000, 11_030_000, 300_000, 2.80, 92_000, 40.8),
    )

    @Test
    fun `long buildup with rising delivery vs yesterday signals STRONG_HOLD`() {
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 1.43,
            liveOi = 12_400_000L,
            liveOiChange = 850_000L,
            liveOiChangePct = 7.36,
            livePcr = 1.18,
            liveDeliveryQty = 145_000L,
            liveDeliveryPct = 47.8,
            rawHistoryDesc = sample5DayHistory,
        )

        assertEquals(FnoBuildupQuadrant.LONG_BUILDUP, summary.buildupQuadrant)
        assertEquals(PositionAction.STRONG_HOLD, summary.positionAction)
        assertEquals(5, summary.history5Days.size)
        assertNotNull(summary.deliveryPctChangePp)
        assertEquals(5.3, summary.deliveryPctChangePp!!, 0.05)
        assertNotNull(summary.deliveryQtyChangePct)
        assertEquals(42.15, summary.deliveryQtyChangePct!!, 0.2)
    }

    @Test
    fun `price up with falling oi signals SHORT_COVERING and TRAIL_PARTIAL`() {
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = 1.9,
            liveOi = 9_800_000L,
            liveOiChange = -450_000L,
            liveOiChangePct = -4.39,
            livePcr = 0.92,
            liveDeliveryQty = 80_000L,
            liveDeliveryPct = 33.0,
            rawHistoryDesc = sample5DayHistory,
        )

        assertEquals(FnoBuildupQuadrant.SHORT_COVERING, summary.buildupQuadrant)
        assertEquals(PositionAction.TRAIL_PARTIAL, summary.positionAction)
    }

    @Test
    fun `price down with falling oi signals LONG_UNWINDING and SELL_EXIT`() {
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = -2.4,
            liveOi = 9_500_000L,
            liveOiChange = -600_000L,
            liveOiChangePct = -5.94,
            livePcr = 0.65,
            liveDeliveryQty = 75_000L,
            liveDeliveryPct = 35.0,
            rawHistoryDesc = sample5DayHistory,
        )

        assertEquals(FnoBuildupQuadrant.LONG_UNWINDING, summary.buildupQuadrant)
        assertEquals(PositionAction.SELL_EXIT, summary.positionAction)
    }
}
