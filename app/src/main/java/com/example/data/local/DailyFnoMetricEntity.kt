package com.example.data.local

import androidx.room.Entity
import com.example.domain.model.DailyPositionSnapshot

@Entity(
    tableName = "daily_fno_metrics",
    primaryKeys = ["symbol", "tradeDate"],
)
data class DailyFnoMetricEntity(
    val symbol: String,
    val tradeDate: String, // ISO yyyy-MM-dd for deterministic descending sort
    val closePrice: Double,
    val priceChangePct: Double,
    val tradedQty: Long,
    val openInterest: Long,
    val oiChange: Long,
    val oiChangePct: Double,
    val deliveryQty: Long,
    val deliveryPct: Double,
    val updatedAtMs: Long,
) {
    fun toDomain(): DailyPositionSnapshot = DailyPositionSnapshot(
        tradeDate = tradeDate,
        closePrice = closePrice,
        priceChangePct = priceChangePct,
        tradedQty = tradedQty,
        openInterest = openInterest,
        oiChange = oiChange,
        oiChangePct = oiChangePct,
        deliveryQty = deliveryQty,
        deliveryPct = deliveryPct,
    )
}
