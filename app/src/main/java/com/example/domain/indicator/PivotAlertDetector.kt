package com.example.domain.indicator

import com.example.domain.model.AlertCategory
import com.example.domain.model.LiveScannerAlert
import com.example.domain.model.OptionSide
import com.example.domain.model.PivotWatchLevel
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.StockScanResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PivotAlertBatchResult(
    val activePivotAlerts: List<LiveScannerAlert>,
    val newlyTriggeredAlerts: List<LiveScannerAlert>,
    val newlyCrossedPrioritySymbols: Set<String>,
)

object PivotAlertDetector {

    private val istFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("Asia/Kolkata"))

    fun evaluateBatch(
        stocks: List<StockScanResult>,
        previousCeRankBySymbol: MutableMap<String, Int>,
        previousPeRankBySymbol: MutableMap<String, Int>,
        activeAlertsById: MutableMap<String, LiveScannerAlert>,
        hasBaseline: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): PivotAlertBatchResult {
        val timeIst = "${istFormatter.format(Instant.ofEpochMilli(nowMs))} IST"
        val newlyTriggered = mutableListOf<LiveScannerAlert>()
        val newlyPrioritySymbols = mutableSetOf<String>()

        for (stock in stocks) {
            val pivots = stock.pivotLevels ?: continue

            // Evaluate CE resistance levels (R1, R2, R3 — with R2 & R3 as priority watch)
            val ceLevel = stock.currentCePivotLevel()
            val prevCeRank = previousCeRankBySymbol[stock.symbol] ?: 0
            val newCeRank = ceLevel?.rank ?: 0
            if (ceLevel != null) {
                val levelPrice = when (ceLevel) {
                    PivotWatchLevel.ABOVE_R3 -> pivots.r3
                    PivotWatchLevel.ABOVE_R2 -> pivots.r2
                    PivotWatchLevel.ABOVE_R1 -> pivots.r1
                    else -> pivots.r1
                }
                val distPct = if (levelPrice > 0.0) ((stock.lastPrice - levelPrice) / levelPrice) * 100.0 else 0.0
                val isStepUpCross = hasBaseline && newCeRank > prevCeRank
                val id = "PIVOT_CE_${stock.symbol}"
                val existing = activeAlertsById[id]
                val isPriority = ceLevel.rank >= 2

                val alert = LiveScannerAlert(
                    id = id,
                    symbol = stock.symbol,
                    side = OptionSide.CE,
                    category = AlertCategory.PIVOT_BREAKOUT,
                    title = "Above ${ceLevel.shortLabel} (₹${TechnicalIndicators.formatPrice(levelPrice)})",
                    detailText = "Spot ₹${TechnicalIndicators.formatPrice(stock.lastPrice)} (${TechnicalIndicators.formatPercent(stock.changePercent)}) • +${String.format(Locale.US, "%.2f%%", distPct)} > ${ceLevel.shortLabel}",
                    level = ceLevel,
                    triggeredAtEpochMs = if (isStepUpCross || existing == null) nowMs else existing.triggeredAtEpochMs,
                    triggeredAtTimeIst = if (isStepUpCross || existing == null) timeIst else existing.triggeredAtTimeIst,
                    isPriorityWatch = isPriority,
                    isNew = isStepUpCross || (existing?.isNew == true && existing.level == ceLevel),
                )
                activeAlertsById[id] = alert

                if (isStepUpCross) {
                    newlyTriggered.add(alert)
                    if (isPriority) {
                        newlyPrioritySymbols.add(stock.symbol)
                    }
                }
            } else {
                activeAlertsById.remove("PIVOT_CE_${stock.symbol}")
            }
            previousCeRankBySymbol[stock.symbol] = newCeRank

            // Evaluate PE support levels (S1, S2, S3 — with S2 & S3 as priority watch)
            val peLevel = stock.currentPePivotLevel()
            val prevPeRank = previousPeRankBySymbol[stock.symbol] ?: 0
            val newPeRank = peLevel?.rank ?: 0
            if (peLevel != null) {
                val levelPrice = when (peLevel) {
                    PivotWatchLevel.BELOW_S3 -> pivots.s3
                    PivotWatchLevel.BELOW_S2 -> pivots.s2
                    PivotWatchLevel.BELOW_S1 -> pivots.s1
                    else -> pivots.s1
                }
                val distPct = if (levelPrice > 0.0) ((levelPrice - stock.lastPrice) / levelPrice) * 100.0 else 0.0
                val isStepUpCross = hasBaseline && newPeRank > prevPeRank
                val id = "PIVOT_PE_${stock.symbol}"
                val existing = activeAlertsById[id]
                val isPriority = peLevel.rank >= 2

                val alert = LiveScannerAlert(
                    id = id,
                    symbol = stock.symbol,
                    side = OptionSide.PE,
                    category = AlertCategory.PIVOT_BREAKOUT,
                    title = "Below ${peLevel.shortLabel} (₹${TechnicalIndicators.formatPrice(levelPrice)})",
                    detailText = "Spot ₹${TechnicalIndicators.formatPrice(stock.lastPrice)} (${TechnicalIndicators.formatPercent(stock.changePercent)}) • -${String.format(Locale.US, "%.2f%%", distPct)} < ${peLevel.shortLabel}",
                    level = peLevel,
                    triggeredAtEpochMs = if (isStepUpCross || existing == null) nowMs else existing.triggeredAtEpochMs,
                    triggeredAtTimeIst = if (isStepUpCross || existing == null) timeIst else existing.triggeredAtTimeIst,
                    isPriorityWatch = isPriority,
                    isNew = isStepUpCross || (existing?.isNew == true && existing.level == peLevel),
                )
                activeAlertsById[id] = alert

                if (isStepUpCross) {
                    newlyTriggered.add(alert)
                    if (isPriority) {
                        newlyPrioritySymbols.add(stock.symbol)
                    }
                }
            } else {
                activeAlertsById.remove("PIVOT_PE_${stock.symbol}")
            }
            previousPeRankBySymbol[stock.symbol] = newPeRank
        }

        val sortedActive = activeAlertsById.values.sortedWith(
            compareByDescending<LiveScannerAlert> { it.isNew }
                .thenByDescending { it.isPriorityWatch }
                .thenByDescending { it.level?.rank ?: 0 }
                .thenByDescending { it.triggeredAtEpochMs }
        )

        return PivotAlertBatchResult(
            activePivotAlerts = sortedActive,
            newlyTriggeredAlerts = newlyTriggered.sortedByDescending { it.level?.rank ?: 0 },
            newlyCrossedPrioritySymbols = newlyPrioritySymbols,
        )
    }

    data class VolumeSurgeBatchResult(
        val activeVolumeAlerts: List<LiveScannerAlert>,
        val newlyTriggeredVolumeAlerts: List<LiveScannerAlert>,
    )

    fun evaluateVolumeSurgeBatch(
        stocks: List<StockScanResult>,
        previousSurgeSymbols: MutableSet<String>,
        activeVolumeAlertsById: MutableMap<String, LiveScannerAlert>,
        hasBaseline: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): VolumeSurgeBatchResult {
        val timeIst = "${istFormatter.format(Instant.ofEpochMilli(nowMs))} IST"
        val newlyTriggered = mutableListOf<LiveScannerAlert>()
        val currentSurgingSymbols = mutableSetOf<String>()

        for (stock in stocks) {
            val avg20d = stock.volumeSma20d ?: continue
            val ratio = stock.volumeSurgeRatio ?: continue
            val id = "VOL2X_${stock.symbol}"

            if (stock.isVolumeSurge2x && avg20d > 0.0) {
                currentSurgingSymbols.add(stock.symbol)
                val existing = activeVolumeAlertsById[id]
                val isFresh = hasBaseline && stock.symbol !in previousSurgeSymbols
                val side = when (stock.oiDelivery.buildupQuadrant) {
                    com.example.domain.model.FnoBuildupQuadrant.SHORT_BUILDUP,
                    com.example.domain.model.FnoBuildupQuadrant.LONG_UNWINDING -> OptionSide.PE
                    com.example.domain.model.FnoBuildupQuadrant.LONG_BUILDUP,
                    com.example.domain.model.FnoBuildupQuadrant.SHORT_COVERING -> OptionSide.CE
                    com.example.domain.model.FnoBuildupQuadrant.NEUTRAL ->
                        if (stock.changePercent >= 0.0) OptionSide.CE else OptionSide.PE
                }
                val alert = LiveScannerAlert(
                    id = id,
                    symbol = stock.symbol,
                    side = side,
                    category = AlertCategory.VOLUME_SURGE,
                    title = "Volume Surge ${String.format(Locale.US, "%.1fx", ratio)} > 20D SMA",
                    detailText = "Vol ${TechnicalIndicators.formatVolumeCompact(stock.sharesVolume)} vs 20D Avg ${TechnicalIndicators.formatVolumeCompact(avg20d.toLong())} • Spot ₹${TechnicalIndicators.formatPrice(stock.lastPrice)} (${TechnicalIndicators.formatPercent(stock.changePercent)})",
                    triggeredAtEpochMs = if (isFresh || existing == null) nowMs else existing.triggeredAtEpochMs,
                    triggeredAtTimeIst = if (isFresh || existing == null) timeIst else existing.triggeredAtTimeIst,
                    isPriorityWatch = true,
                    isNew = isFresh || (existing?.isNew == true),
                )
                activeVolumeAlertsById[id] = alert
                if (isFresh) {
                    newlyTriggered.add(alert)
                }
            } else {
                activeVolumeAlertsById.remove(id)
            }
        }

        previousSurgeSymbols.clear()
        previousSurgeSymbols.addAll(currentSurgingSymbols)

        val sortedActive = activeVolumeAlertsById.values.sortedWith(
            compareByDescending<LiveScannerAlert> { it.isNew }
                .thenByDescending { it.triggeredAtEpochMs }
        )

        return VolumeSurgeBatchResult(
            activeVolumeAlerts = sortedActive,
            newlyTriggeredVolumeAlerts = newlyTriggered,
        )
    }

    data class PositionalBuzzBatchResult(
        val activeBuzzAlerts: List<LiveScannerAlert>,
        val newlyTriggeredBuzzAlerts: List<LiveScannerAlert>,
    )

    /**
     * Detects Positional stocks simultaneously achieving 20-Day Volume > 2.0x AND multi-TF RSI > 65.
     * Triggers an initial-or-fresh buzz event whenever a symbol enters this high-conviction state.
     */
    fun evaluatePositionalVolRsiBuzzBatch(
        stocks: List<StockScanResult>,
        previousBuzzSymbols: MutableSet<String>,
        activeBuzzAlertsById: MutableMap<String, LiveScannerAlert>,
        nowMs: Long = System.currentTimeMillis(),
    ): PositionalBuzzBatchResult {
        val timeIst = "${istFormatter.format(Instant.ofEpochMilli(nowMs))} IST"
        val newlyTriggered = mutableListOf<LiveScannerAlert>()
        val currentBuzzSymbols = mutableSetOf<String>()

        for (stock in stocks) {
            val signal = PositionalAnalyzer.evaluateVol2xRsi65(stock)
            val id = "POS_BUZZ_${stock.symbol}"
            if (signal.matched) {
                currentBuzzSymbols.add(stock.symbol)
                val existing = activeBuzzAlertsById[id]
                val isFresh = stock.symbol !in previousBuzzSymbols
                val ratioText = signal.volumeRatio?.let { String.format(Locale.US, "%.1fx", it) } ?: "2.0x+"
                val tfText = signal.matchedTfLabels.joinToString("/")
                val alert = LiveScannerAlert(
                    id = id,
                    symbol = stock.symbol,
                    side = OptionSide.CE,
                    category = AlertCategory.POSITIONAL_VOL_RSI,
                    title = "Vol $ratioText (>2x) + ${signal.rsiAbove65Count}/${signal.totalRsiCount} RSI > 65 BUZZ",
                    detailText = "TF >65: $tfText • Spot ₹${TechnicalIndicators.formatPrice(stock.lastPrice)} (${TechnicalIndicators.formatPercent(stock.changePercent)})",
                    triggeredAtEpochMs = if (isFresh || existing == null) nowMs else existing.triggeredAtEpochMs,
                    triggeredAtTimeIst = if (isFresh || existing == null) timeIst else existing.triggeredAtTimeIst,
                    isPriorityWatch = true,
                    isNew = isFresh || (existing?.isNew == true),
                )
                activeBuzzAlertsById[id] = alert
                if (isFresh) {
                    newlyTriggered.add(alert)
                }
            } else {
                activeBuzzAlertsById.remove(id)
            }
        }

        previousBuzzSymbols.clear()
        previousBuzzSymbols.addAll(currentBuzzSymbols)

        val sortedActive = activeBuzzAlertsById.values.sortedWith(
            compareByDescending<LiveScannerAlert> { it.isNew }
                .thenByDescending { it.triggeredAtEpochMs }
        )
        return PositionalBuzzBatchResult(
            activeBuzzAlerts = sortedActive,
            newlyTriggeredBuzzAlerts = newlyTriggered,
        )
    }

    fun buildUnifiedAlerts(
        pivotAlerts: List<LiveScannerAlert>,
        rsiAlerts: List<RsiCrossAlert>,
        volumeSurgeAlerts: List<LiveScannerAlert> = emptyList(),
        positionalBuzzAlerts: List<LiveScannerAlert> = emptyList(),
    ): List<LiveScannerAlert> {
        val convertedRsi = rsiAlerts.map { rsi ->
            LiveScannerAlert(
                id = "RSI_${rsi.id}",
                symbol = rsi.symbol,
                side = rsi.side,
                category = AlertCategory.RSI_CROSS,
                title = "${rsi.timeframe.label} RSI ${if (rsi.side == OptionSide.CE) "> 70" else "< 30"} (${String.format(Locale.US, "%.1f", rsi.rsiValue)})",
                detailText = "Spot ₹${TechnicalIndicators.formatPrice(rsi.lastPrice)} (${TechnicalIndicators.formatPercent(rsi.changePercent)})",
                timeframe = rsi.timeframe,
                triggeredAtEpochMs = rsi.crossedAtEpochMs,
                triggeredAtTimeIst = rsi.crossedAtTimeIst,
                isPriorityWatch = rsi.isActiveExtreme,
                isNew = rsi.isActiveExtreme,
            )
        }
        return (positionalBuzzAlerts + volumeSurgeAlerts + pivotAlerts + convertedRsi).sortedWith(
            compareByDescending<LiveScannerAlert> { it.category == AlertCategory.POSITIONAL_VOL_RSI }
                .thenByDescending { it.isPriorityWatch }
                .thenByDescending { it.isNew }
                .thenByDescending { it.triggeredAtEpochMs }
        )
    }
}
