package com.example.domain.indicator

import com.example.domain.model.ChartTimeframe
import com.example.domain.model.OptionSide
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.StockScanResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RsiCrossoverDetector {

    private val istTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("Asia/Kolkata"))

    const val CE_RSI_CROSS_LEVEL = 70.0
    const val PE_RSI_CROSS_LEVEL = 30.0

    data class DetectionOutput(
        val enrichedStocks: List<StockScanResult>,
        val watchAlertsFeed: List<RsiCrossAlert>,
        val newlyTriggeredAlerts: List<RsiCrossAlert> = emptyList(),
    )

    fun evaluateBatch(
        stocks: List<StockScanResult>,
        previousRsiByKey: MutableMap<String, Double>,
        activeAlertsById: MutableMap<String, RsiCrossAlert>,
        nowMs: Long = System.currentTimeMillis(),
    ): DetectionOutput {
        val nowTimeIst = istTimeFormatter.format(Instant.ofEpochMilli(nowMs))
        val newlyTriggered = mutableListOf<RsiCrossAlert>()

        val enriched = stocks.map { stock ->
            val tfSeriesList = listOf(
                ChartTimeframe.M1 to stock.rsi1mSeries,
                ChartTimeframe.M5 to stock.rsi5mSeries,
                ChartTimeframe.M15 to stock.rsi15mSeries,
                ChartTimeframe.M30 to stock.rsi30mSeries,
                ChartTimeframe.H1 to stock.rsi1hSeries,
            )

            val stockCrosses = mutableListOf<RsiCrossAlert>()

            for ((tf, series) in tfSeriesList) {
                val nonNull = series.filterNotNull()
                val currRsi = nonNull.lastOrNull() ?: continue
                val prevBarRsi = nonNull.dropLast(1).lastOrNull()
                val scanKey = "${stock.symbol}_${tf.name}"
                val prevScanRsi = previousRsiByKey.put(scanKey, currRsi)

                val recentLookback = nonNull.dropLast(1).takeLast(3)

                // Check CE crossing above 70
                val ceId = "${stock.symbol}_CE_${tf.label}"
                val existingCe = activeAlertsById[ceId]
                val crossedAbove70 = (prevScanRsi != null && prevScanRsi <= CE_RSI_CROSS_LEVEL && currRsi > CE_RSI_CROSS_LEVEL) ||
                    (prevScanRsi == null && currRsi > CE_RSI_CROSS_LEVEL && recentLookback.any { it <= CE_RSI_CROSS_LEVEL })

                if (crossedAbove70) {
                    val freshAlert = RsiCrossAlert(
                        id = ceId,
                        symbol = stock.symbol,
                        side = OptionSide.CE,
                        timeframe = tf,
                        rsiValue = currRsi,
                        crossedAtEpochMs = nowMs,
                        crossedAtTimeIst = nowTimeIst,
                        lastPrice = stock.lastPrice,
                        changePercent = stock.changePercent,
                        isActiveExtreme = true,
                    )
                    activeAlertsById[ceId] = freshAlert
                    stockCrosses.add(freshAlert)
                    newlyTriggered.add(freshAlert)
                } else if (existingCe != null) {
                    val stillAbove70 = currRsi > CE_RSI_CROSS_LEVEL
                    val updated = existingCe.copy(
                        rsiValue = currRsi,
                        lastPrice = stock.lastPrice,
                        changePercent = stock.changePercent,
                        isActiveExtreme = stillAbove70,
                    )
                    activeAlertsById[ceId] = updated
                    if (stillAbove70) stockCrosses.add(updated)
                }

                // Check PE crossing below 30
                val peId = "${stock.symbol}_PE_${tf.label}"
                val existingPe = activeAlertsById[peId]
                val crossedBelow30 = (prevScanRsi != null && prevScanRsi >= PE_RSI_CROSS_LEVEL && currRsi < PE_RSI_CROSS_LEVEL) ||
                    (prevScanRsi == null && currRsi < PE_RSI_CROSS_LEVEL && recentLookback.any { it >= PE_RSI_CROSS_LEVEL })

                if (crossedBelow30) {
                    val freshAlert = RsiCrossAlert(
                        id = peId,
                        symbol = stock.symbol,
                        side = OptionSide.PE,
                        timeframe = tf,
                        rsiValue = currRsi,
                        crossedAtEpochMs = nowMs,
                        crossedAtTimeIst = nowTimeIst,
                        lastPrice = stock.lastPrice,
                        changePercent = stock.changePercent,
                        isActiveExtreme = true,
                    )
                    activeAlertsById[peId] = freshAlert
                    stockCrosses.add(freshAlert)
                    newlyTriggered.add(freshAlert)
                } else if (existingPe != null) {
                    val stillBelow30 = currRsi < PE_RSI_CROSS_LEVEL
                    val updated = existingPe.copy(
                        rsiValue = currRsi,
                        lastPrice = stock.lastPrice,
                        changePercent = stock.changePercent,
                        isActiveExtreme = stillBelow30,
                    )
                    activeAlertsById[peId] = updated
                    if (stillBelow30) stockCrosses.add(updated)
                }
            }

            stock.copy(highPriorityCrosses = stockCrosses)
        }

        val sortedFeed = activeAlertsById.values
            .sortedWith(
                compareByDescending<RsiCrossAlert> { it.isActiveExtreme }
                    .thenByDescending { it.crossedAtEpochMs }
            )
            .take(30)

        return DetectionOutput(
            enrichedStocks = enriched,
            watchAlertsFeed = sortedFeed,
            newlyTriggeredAlerts = newlyTriggered,
        )
    }
}
