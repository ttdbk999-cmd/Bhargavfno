package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.indicator.OptionPivotPricing
import com.example.domain.indicator.PivotOptionProjection
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.CandleBar
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.DailyPositionSnapshot
import com.example.domain.model.OiDeliverySummary
import com.example.domain.model.OptionSide
import com.example.domain.model.OptionStrikeQuote
import com.example.domain.model.PivotLevels
import com.example.domain.model.PositionAction
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.StockScanResult
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAmberContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearContainer
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBearRed
import com.example.ui.theme.TvBullContainer
import com.example.ui.theme.TvBullEmerald
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
import com.example.ui.theme.TvRsiMaAmber
import com.example.ui.theme.TvRsiPurple
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextBright
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.theme.TvTrendBearBg
import com.example.ui.theme.TvTrendBearRed
import com.example.ui.theme.TvTrendBullBg
import com.example.ui.theme.TvTrendBullGreen
import com.example.ui.theme.TvWarningAmber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailChartSheet(
    stock: StockScanResult,
    side: OptionSide,
    timeframe: ChartTimeframe,
    thresholds: ScannerThresholds,
    isSyncingOiDelivery: Boolean,
    nseSyncStatus: String? = null,
    isInWatchlist: Boolean = false,
    onSelectTimeframe: (ChartTimeframe) -> Unit,
    onSyncOiDelivery: () -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isCe = side == OptionSide.CE
    val eval = if (isCe) stock.evaluateCe(thresholds) else stock.evaluatePe(thresholds)
    val chgColor = if (stock.changePercent >= 0.0) TvBullTeal else TvBearCoral

    val (candles, rsiSeries) = when (timeframe) {
        ChartTimeframe.M1 -> stock.candles1m.takeLast(60) to stock.rsi1mSeries.takeLast(60)
        ChartTimeframe.M5 -> stock.candles5m.takeLast(60) to stock.rsi5mSeries.takeLast(60)
        ChartTimeframe.M15 -> stock.candles15m.takeLast(60) to stock.rsi15mSeries.takeLast(60)
        ChartTimeframe.M30 -> stock.candles30m.takeLast(60) to stock.rsi30mSeries.takeLast(60)
        ChartTimeframe.H1 -> stock.candles1h.takeLast(60) to stock.rsi1hSeries.takeLast(60)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TvSurface,
        contentColor = TvTextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(stock.badgeColorHex)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stock.symbol.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stock.symbol,
                                style = MaterialTheme.typography.titleLarge,
                                color = TvTextBright,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isCe) TvBullContainer else TvBearContainer,
                            ) {
                                Text(
                                    text = stock.suggestedStrikeContract(side),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCe) TvBullTeal else TvBearCoral,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Text(
                            text = "${stock.companyName} • ${stock.sector}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextSecondary,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_watchlist_toggle"),
                    ) {
                        Icon(
                            imageVector = if (isInWatchlist) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(
                                if (isInWatchlist) R.string.cd_remove_from_watchlist else R.string.cd_add_to_watchlist,
                            ),
                            tint = if (isInWatchlist) TvWarningAmber else TvTextSecondary,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close_sheet),
                            tint = TvTextSecondary,
                        )
                    }
                }
            }

            // Live Price & Timeframe Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "₹${TechnicalIndicators.formatPrice(stock.lastPrice)}",
                        style = TabularNumberStyle.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TvTextBright,
                        maxLines = 1,
                    )
                    Text(
                        text = "${TechnicalIndicators.formatChange(stock.change)} (${TechnicalIndicators.formatPercent(stock.changePercent)}) • Vol ${TechnicalIndicators.formatVolumeCompact(stock.sharesVolume)}",
                        style = TabularNumberStyle.copy(fontSize = 11.5.sp),
                        color = chgColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TvBackground)
                        .border(1.dp, TvDivider, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ChartTimeframe.entries.forEach { tf ->
                        val selected = tf == timeframe
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) TvAccentBlue else Color.Transparent)
                                .clickable { onSelectTimeframe(tf) }
                                .padding(horizontal = 7.dp, vertical = 6.dp)
                                .testTag("chart_tf_${tf.label}"),
                        ) {
                            Text(
                                text = tf.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) TvTextBright else TvTextSecondary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // ATM Option Strike Price & Live Running Price Card (CE + PE + Nearby Strikes)
            AtmOptionHeroCard(
                stock = stock,
                initialSide = side,
                isSyncing = isSyncingOiDelivery,
                nseSyncStatus = nseSyncStatus,
                onRefresh = onSyncOiDelivery,
            )

            // Candlestick + RSI(14) Dual-Pane TradingView Chart
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvBackground,
                border = BorderStroke(1.dp, TvDivider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    val ema20Series = remember(candles) {
                        TechnicalIndicators.calculateEmaSeries(candles.map { it.close }, period = 20)
                    }
                    val latestEma20 = ema20Series.lastOrNull { it != null }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = buildString {
                                append("${stock.symbol} • ${timeframe.label} Candles")
                                if (latestEma20 != null && latestEma20 > 0.0) {
                                    append(" • EMA20 ₹${TechnicalIndicators.formatPrice(latestEma20)}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (latestEma20 != null) TvWarningAmber else TvTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Prev Close ₹${TechnicalIndicators.formatPrice(stock.previousClose)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvTextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    CandlestickCanvasChart(
                        candles = candles,
                        previousClose = stock.previousClose,
                        pivotLevels = stock.pivotLevels,
                        ema20Series = ema20Series,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(172.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = TvDivider,
                    )
                    val latestRsiVal = rsiSeries.lastOrNull { it != null }
                    val rsiThreshold = when (timeframe) {
                        ChartTimeframe.M1 -> if (isCe) thresholds.minRsi1m else thresholds.effectivePeRsi1m
                        ChartTimeframe.M5 -> if (isCe) thresholds.minRsi5m else thresholds.effectivePeRsi5m
                        ChartTimeframe.M15 -> if (isCe) thresholds.minRsi15m else thresholds.effectivePeRsi15m
                        ChartTimeframe.M30 -> if (isCe) 70.0 else 30.0
                        ChartTimeframe.H1 -> if (isCe) thresholds.minRsi1h else thresholds.effectivePeRsi1h
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "RSI (14, close) • Target ${if (isCe) ">" else "<"} ${rsiThreshold.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvRsiPurple,
                        )
                        Text(
                            text = latestRsiVal?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                            style = TabularNumberStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = TvRsiMaAmber,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    RsiSubChartCanvas(
                        rsiValues = rsiSeries,
                        threshold = rsiThreshold,
                        isBullish = isCe,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp),
                    )
                }
            }

            // Standard Pivots (S1-S3, PP, R1-R3) & F&O PCR OI Change % Card
            PivotAndPcrAnalyticsCard(
                stock = stock,
                side = side,
            )

            // OI, OI Change vs Yesterday, Delivery Change vs Yesterday & 5-Day Hold/Sell Tracker
            OiDeliveryPositionCard(
                summary = stock.oiDelivery,
                isSyncing = isSyncingOiDelivery,
                onSyncNow = onSyncOiDelivery,
            )

            // 5-Rule Scanner Evaluation Checklist
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvDivider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isCe) "CE Bullish Momentum Criteria" else "PE Bearish Momentum Criteria",
                            style = MaterialTheme.typography.titleSmall,
                            color = TvTextBright,
                        )
                        Text(
                            text = if (thresholds.require1mAlignment) {
                                "${eval.passedCount}/5 + 1m ${if (eval.rsi1mPass && stock.rsi1m != null) "Aligned" else "Wait"}"
                            } else {
                                "${eval.passedCount}/5 Passed"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eval.allPassed) (if (isCe) TvBullTeal else TvBearCoral) else TvTextSecondary,
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = TvDivider)
                    CriterionCheckRow(
                        name = "RVOL (Relative Volume)",
                        actual = "${String.format(Locale.US, "%.2f", stock.rvol)}x",
                        target = "> ${thresholds.minRvol}x",
                        passed = eval.rvolPass,
                        isCe = isCe,
                    )
                    CriterionCheckRow(
                        name = "Shares Traded (Volume)",
                        actual = TechnicalIndicators.formatVolumeCompact(stock.sharesVolume),
                        target = if (stock.lastPrice >= 500.0) {
                            "> ${TechnicalIndicators.formatVolumeCompact(thresholds.minShares)} or ₹2.5Cr+"
                        } else {
                            "> ${TechnicalIndicators.formatVolumeCompact(thresholds.minShares)}"
                        },
                        passed = eval.sharesPass,
                        isCe = isCe,
                    )
                    if (stock.volumeSma20d != null && stock.volumeSurgeRatio != null) {
                        CriterionCheckRow(
                            name = "20-Day Vol SMA Surge",
                            actual = "${String.format(Locale.US, "%.2fx", stock.volumeSurgeRatio)} (20D Avg ${TechnicalIndicators.formatVolumeCompact(stock.volumeSma20d.toLong())})",
                            target = ">= 2.00x 20D SMA",
                            passed = stock.isVolumeSurge2x,
                            isCe = isCe,
                        )
                    }
                    CriterionCheckRow(
                        name = "1 hr RSI (14)",
                        actual = stock.rsi1h?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        target = if (isCe) "> ${thresholds.minRsi1h.toInt()}" else "< ${thresholds.effectivePeRsi1h.toInt()}",
                        passed = eval.rsi1hPass,
                        isCe = isCe,
                    )
                    CriterionCheckRow(
                        name = "30 min RSI (14) Cross",
                        actual = stock.rsi30m?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        target = if (isCe) "> 70 (High)" else "< 30 (High)",
                        passed = if (isCe) (stock.rsi30m ?: 0.0) > 70.0 else (stock.rsi30m ?: 100.0) < 30.0,
                        isCe = isCe,
                    )
                    CriterionCheckRow(
                        name = "15 min RSI (14)",
                        actual = stock.rsi15m?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        target = if (isCe) "> ${thresholds.minRsi15m.toInt()}" else "< ${thresholds.effectivePeRsi15m.toInt()}",
                        passed = eval.rsi15mPass,
                        isCe = isCe,
                    )
                    CriterionCheckRow(
                        name = "5 min RSI (14)",
                        actual = stock.rsi5m?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        target = if (isCe) "> ${thresholds.minRsi5m.toInt()}" else "< ${thresholds.effectivePeRsi5m.toInt()}",
                        passed = eval.rsi5mPass,
                        isCe = isCe,
                    )
                    CriterionCheckRow(
                        name = if (thresholds.require1mAlignment) "1 min RSI (14) Align" else "1 min RSI (14) Opt",
                        actual = stock.rsi1m?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        target = if (isCe) "> ${thresholds.minRsi1m.toInt()}" else "< ${thresholds.effectivePeRsi1m.toInt()}",
                        passed = stock.rsi1m != null && stock.is1mAligned(side, thresholds),
                        isCe = isCe,
                    )
                }
            }
        }
    }
}

@Composable
private fun OiDeliveryPositionCard(
    summary: OiDeliverySummary,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    val (verdictBg, verdictAccent) = when (summary.positionAction) {
        PositionAction.STRONG_HOLD -> TvBullContainer to TvBullTeal
        PositionAction.HOLD -> TvBullContainer.copy(alpha = 0.7f) to TvBullEmerald
        PositionAction.TRAIL_PARTIAL -> TvAmberContainer to TvWarningAmber
        PositionAction.SELL_EXIT -> TvBearContainer to TvBearCoral
        PositionAction.WATCH -> TvBackground to TvTextSecondary
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = TvSurfaceElevated,
        border = BorderStroke(1.dp, TvDivider),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("oi_delivery_position_card"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Position Decision • OI & Delivery (5D+)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TvTextBright,
                    )
                    Text(
                        text = "Hold vs Sell from Futures OI & Delivery vs Yesterday",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                    )
                }
                IconButton(
                    onClick = onSyncNow,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sync_oi_delivery_button"),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TvBullTeal,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh OI and Delivery",
                            tint = TvTextPrimary,
                        )
                    }
                }
            }

            // Hold vs Sell Verdict Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = verdictBg,
                border = BorderStroke(1.dp, verdictAccent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = summary.positionHeadline,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = verdictAccent,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PositionActionPill(action = summary.positionAction)
                    }
                    Text(
                        text = summary.positionRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextPrimary,
                    )
                }
            }

            // 4 KPI Grid: OI + Chg vs Yest | Deliv Qty + Chg vs Yest | Deliv % + Chg vs Yest | PCR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val oiChgText = if (summary.oiChange != null && summary.oiChangePct != null) {
                    val s = if (summary.oiChange >= 0L) "+" else ""
                    "$s${TechnicalIndicators.formatVolumeCompact(summary.oiChange)} ($s${String.format(Locale.US, "%.1f%%", summary.oiChangePct)})"
                } else {
                    "vs Yest: --"
                }
                val oiColor = when {
                    (summary.oiChange ?: 0L) > 0L -> TvBullTeal
                    (summary.oiChange ?: 0L) < 0L -> TvBearCoral
                    else -> TvTextSecondary
                }
                PositionKpiTile(
                    label = "Futures OI",
                    value = summary.openInterest?.let { TechnicalIndicators.formatVolumeCompact(it) } ?: "--",
                    subValue = oiChgText,
                    subColor = oiColor,
                    modifier = Modifier.weight(1f),
                )

                val delivQtyChgText = summary.deliveryQtyChangePct?.let {
                    val s = if (it >= 0.0) "+" else ""
                    "$s${String.format(Locale.US, "%.1f%%", it)} vs Yest"
                } ?: "vs Yest: --"
                val delivQtyColor = when {
                    (summary.deliveryQtyChangePct ?: 0.0) > 0.0 -> TvBullTeal
                    (summary.deliveryQtyChangePct ?: 0.0) < 0.0 -> TvBearCoral
                    else -> TvTextSecondary
                }
                PositionKpiTile(
                    label = "Delivery Qty",
                    value = summary.deliveryQty?.let { TechnicalIndicators.formatVolumeCompact(it) } ?: "--",
                    subValue = delivQtyChgText,
                    subColor = delivQtyColor,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val delivPctChgText = summary.deliveryPctChangePp?.let {
                    val s = if (it >= 0.0) "+" else ""
                    "$s${String.format(Locale.US, "%.2f", it)}pp vs Yest"
                } ?: "vs Yest: --"
                val delivPctColor = when {
                    (summary.deliveryPctChangePp ?: 0.0) > 0.0 -> TvBullTeal
                    (summary.deliveryPctChangePp ?: 0.0) < 0.0 -> TvBearCoral
                    else -> TvTextSecondary
                }
                PositionKpiTile(
                    label = "Delivery %",
                    value = summary.deliveryPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "--",
                    subValue = delivPctChgText,
                    subColor = delivPctColor,
                    modifier = Modifier.weight(1f),
                )

                PositionKpiTile(
                    label = "Buildup / PCR",
                    value = summary.buildupQuadrant.label,
                    subValue = summary.pcr?.let { "Options PCR: ${String.format(Locale.US, "%.2f", it)}" } ?: "PCR: --",
                    subColor = TvTextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }

            // 5+ Day Historical OI & Delivery Table
            HorizontalDivider(thickness = 1.dp, color = TvDivider)
            Text(
                text = "Last ${summary.history5Days.size.coerceAtLeast(5)} Days OI & Delivery Trend",
                style = MaterialTheme.typography.labelMedium,
                color = TvTextBright,
            )

            if (summary.history5Days.isEmpty()) {
                Text(
                    text = if (isSyncing) {
                        "Loading 5-day NSE OI & delivery history..."
                    } else {
                        "No cached 5-day NSE history yet. Tap the refresh icon above to sync from NSE India."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextSecondary,
                )
            } else {
                FiveDayHistoryTable(history = summary.history5Days)
            }
        }
    }
}

@Composable
private fun PositionKpiTile(
    label: String,
    value: String,
    subValue: String,
    subColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TvBackground,
        border = BorderStroke(1.dp, TvDivider),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TvTextSecondary,
                maxLines = 1,
            )
            Text(
                text = value,
                style = TabularNumberStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TvTextBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subValue,
                style = TabularNumberStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FiveDayHistoryTable(history: List<DailyPositionSnapshot>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TvBackground)
            .border(1.dp, TvDivider, RoundedCornerShape(8.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Date", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, modifier = Modifier.weight(1.05f))
            Text("Close", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
            Text("OI (Chg%)", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.25f))
            Text("Deliv Qty", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
            Text("Deliv% (Δ)", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
        }
        HorizontalDivider(thickness = 1.dp, color = TvDivider)

        history.forEachIndexed { idx, row ->
            val shortDate = if (row.tradeDate.length >= 10) row.tradeDate.substring(5) else row.tradeDate
            val oiColor = when {
                row.oiChange > 0L -> TvBullTeal
                row.oiChange < 0L -> TvBearCoral
                else -> TvTextSecondary
            }
            val delivColor = when {
                (row.deliveryPctChangeVsPrev ?: 0.0) > 0.0 -> TvBullTeal
                (row.deliveryPctChangeVsPrev ?: 0.0) < 0.0 -> TvBearCoral
                else -> TvTextSecondary
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortDate,
                    style = TabularNumberStyle.copy(fontSize = 10.sp),
                    color = TvTextPrimary,
                    modifier = Modifier.weight(1.05f),
                    maxLines = 1,
                )
                Text(
                    text = if (row.closePrice > 0.0) TechnicalIndicators.formatPrice(row.closePrice) else "--",
                    style = TabularNumberStyle.copy(fontSize = 10.sp),
                    color = TvTextBright,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.85f),
                    maxLines = 1,
                )
                Text(
                    text = if (row.openInterest > 0L) {
                        val s = if (row.oiChangePct >= 0) "+" else ""
                        "${TechnicalIndicators.formatVolumeCompact(row.openInterest)} ($s${String.format(Locale.US, "%.1f%%", row.oiChangePct)})"
                    } else {
                        "--"
                    },
                    style = TabularNumberStyle.copy(fontSize = 9.5.sp),
                    color = oiColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.25f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (row.deliveryQty > 0L) TechnicalIndicators.formatVolumeCompact(row.deliveryQty) else "--",
                    style = TabularNumberStyle.copy(fontSize = 10.sp),
                    color = TvTextPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.95f),
                    maxLines = 1,
                )
                Text(
                    text = if (row.deliveryPct > 0.0) {
                        val pp = row.deliveryPctChangeVsPrev?.let {
                            val s = if (it >= 0) "+" else ""
                            " $s${String.format(Locale.US, "%.1f", it)}"
                        } ?: ""
                        "${String.format(Locale.US, "%.1f%%", row.deliveryPct)}$pp"
                    } else {
                        "--"
                    },
                    style = TabularNumberStyle.copy(fontSize = 9.5.sp),
                    color = delivColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (idx < history.lastIndex) {
                HorizontalDivider(thickness = 0.5.dp, color = TvDivider)
            }
        }
    }
}

@Composable
private fun CriterionCheckRow(
    name: String,
    actual: String,
    target: String,
    passed: Boolean,
    isCe: Boolean,
) {
    val passColor = if (isCe) TvBullTeal else TvBearCoral
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (passed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (passed) passColor else TvTextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = TvTextPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = actual,
                style = TabularNumberStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (passed) passColor else TvTextBright,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($target)",
                style = MaterialTheme.typography.labelSmall,
                color = TvTextSecondary,
            )
        }
    }
}

@Composable
private fun CandlestickCanvasChart(
    candles: List<CandleBar>,
    previousClose: Double,
    pivotLevels: PivotLevels? = null,
    ema20Series: List<Double?> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 24f
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier) {
        if (candles.isEmpty()) return@Canvas

        val priceAreaHeight = size.height * 0.80f
        val volumeAreaTop = size.height * 0.84f
        val volumeAreaHeight = size.height * 0.16f

        val emaValues = ema20Series.filterNotNull()
        val minPrice = minOf(
            candles.minOf { it.low },
            previousClose,
            emaValues.minOrNull() ?: previousClose,
        )
        val maxPrice = maxOf(
            candles.maxOf { it.high },
            previousClose,
            emaValues.maxOrNull() ?: previousClose,
        )
        val priceRange = (maxPrice - minPrice).coerceAtLeast(0.01)
        val maxVol = candles.maxOf { it.volume }.coerceAtLeast(1L).toFloat()

        for (i in 0..3) {
            val y = priceAreaHeight * (i / 3f)
            drawLine(
                color = TvDivider.copy(alpha = 0.7f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        // Standard Pivot Overlay Lines (S3..R3) when inside visible range
        if (pivotLevels != null) {
            val pivotEntries = listOf(
                Triple("R3", pivotLevels.r3, TvTrendBearRed),
                Triple("R2", pivotLevels.r2, TvBearCoral),
                Triple("R1", pivotLevels.r1, TvWarningAmber),
                Triple("PP", pivotLevels.pp, TvAccentBlue),
                Triple("S1", pivotLevels.s1, TvBullTeal),
                Triple("S2", pivotLevels.s2, TvTrendBullGreen),
                Triple("S3", pivotLevels.s3, TvBullEmerald),
            )
            for ((label, level, color) in pivotEntries) {
                if (level in minPrice..maxPrice) {
                    val y = priceAreaHeight - (((level - minPrice) / priceRange).toFloat() * priceAreaHeight)
                    drawLine(
                        color = color.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                    )
                    drawIntoCanvas { canvas ->
                        labelPaint.color = color.copy(alpha = 0.90f).toArgb()
                        canvas.nativeCanvas.drawText(
                            label,
                            size.width - 6f,
                            (y - 4f).coerceAtLeast(22f),
                            labelPaint,
                        )
                    }
                }
            }
        }

        val prevY = priceAreaHeight - (((previousClose - minPrice) / priceRange).toFloat() * priceAreaHeight)
        drawLine(
            color = TvTextSecondary.copy(alpha = 0.7f),
            start = Offset(0f, prevY),
            end = Offset(size.width, prevY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
        )

        val stepX = size.width / candles.size.coerceAtLeast(1)
        val candleBodyWidth = (stepX * 0.62f).coerceIn(2f, 12f)

        candles.forEachIndexed { idx, bar ->
            val centerX = idx * stepX + stepX / 2f
            val isBull = bar.close >= bar.open
            val barColor = if (isBull) TvBullTeal else TvBearRed

            val highY = priceAreaHeight - (((bar.high - minPrice) / priceRange).toFloat() * priceAreaHeight)
            val lowY = priceAreaHeight - (((bar.low - minPrice) / priceRange).toFloat() * priceAreaHeight)
            val openY = priceAreaHeight - (((bar.open - minPrice) / priceRange).toFloat() * priceAreaHeight)
            val closeY = priceAreaHeight - (((bar.close - minPrice) / priceRange).toFloat() * priceAreaHeight)

            drawLine(
                color = barColor,
                start = Offset(centerX, highY),
                end = Offset(centerX, lowY),
                strokeWidth = 1.6f,
            )

            val topY = minOf(openY, closeY)
            val bodyHeight = kotlin.math.abs(closeY - openY).coerceAtLeast(2f)
            drawRect(
                color = barColor,
                topLeft = Offset(centerX - candleBodyWidth / 2f, topY),
                size = Size(candleBodyWidth, bodyHeight),
            )

            val volHeight = (bar.volume.toFloat() / maxVol) * volumeAreaHeight
            drawRect(
                color = barColor.copy(alpha = 0.45f),
                topLeft = Offset(centerX - candleBodyWidth / 2f, volumeAreaTop + (volumeAreaHeight - volHeight)),
                size = Size(candleBodyWidth, volHeight.coerceAtLeast(1f)),
            )
        }

        // Overlay EMA 20 curve across candles
        if (ema20Series.size == candles.size) {
            val emaPath = Path()
            var emaStarted = false
            ema20Series.forEachIndexed { idx, emaVal ->
                if (emaVal != null && !emaVal.isNaN()) {
                    val x = idx * stepX + stepX / 2f
                    val y = priceAreaHeight - (((emaVal - minPrice) / priceRange).toFloat() * priceAreaHeight)
                    if (!emaStarted) {
                        emaPath.moveTo(x, y)
                        emaStarted = true
                    } else {
                        emaPath.lineTo(x, y)
                    }
                }
            }
            if (emaStarted) {
                drawPath(
                    path = emaPath,
                    color = TvWarningAmber,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun RsiSubChartCanvas(
    rsiValues: List<Double?>,
    threshold: Double,
    isBullish: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (rsiValues.isEmpty()) return@Canvas

        fun yForRsi(rsi: Double): Float {
            val clamped = rsi.coerceIn(0.0, 100.0)
            return size.height - ((clamped / 100.0).toFloat() * size.height)
        }

        val y70 = yForRsi(70.0)
        val y30 = yForRsi(30.0)
        val yThresh = yForRsi(threshold)

        drawRect(
            color = TvRsiPurple.copy(alpha = 0.08f),
            topLeft = Offset(0f, y70),
            size = Size(size.width, y30 - y70),
        )

        drawLine(
            color = TvDivider,
            start = Offset(0f, y70),
            end = Offset(size.width, y70),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
        drawLine(
            color = TvDivider,
            start = Offset(0f, y30),
            end = Offset(size.width, y30),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        val threshColor = if (isBullish) TvBullTeal else TvBearCoral
        drawLine(
            color = threshColor.copy(alpha = 0.75f),
            start = Offset(0f, yThresh),
            end = Offset(size.width, yThresh),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)),
        )

        val stepX = size.width / rsiValues.size.coerceAtLeast(1)
        val path = Path()
        var started = false
        rsiValues.forEachIndexed { idx, rsi ->
            if (rsi != null && !rsi.isNaN()) {
                val x = idx * stepX + stepX / 2f
                val y = yForRsi(rsi)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = TvRsiPurple,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun AtmOptionHeroCard(
    stock: StockScanResult,
    initialSide: OptionSide,
    isSyncing: Boolean,
    nseSyncStatus: String?,
    onRefresh: () -> Unit,
) {
    var chainSide by remember(stock.symbol, initialSide) { mutableStateOf(initialSide) }
    val bundle = stock.atmOptions
    val atmCe = bundle.atmCe
    val atmPe = bundle.atmPe
    val nearby = if (chainSide == OptionSide.CE) bundle.nearbyStrikesCe else bundle.nearbyStrikesPe
    val activeAtmStrike = if (chainSide == OptionSide.CE) atmCe?.strikePrice else atmPe?.strikePrice

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = TvSurfaceElevated,
        border = BorderStroke(1.dp, TvDivider),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("atm_option_hero_card"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ATM Strike & Running Option Price",
                        style = MaterialTheme.typography.titleSmall,
                        color = TvTextBright,
                    )
                    Text(
                        text = when {
                            bundle.expiryDate.isNotBlank() && (atmCe != null || atmPe != null) ->
                                "Nearest Expiry: ${bundle.expiryDate} • Refreshes every 10s"
                            isSyncing ->
                                "ATM Est: ${stock.formattedComputedAtmStrike(initialSide)} • Syncing live NSE option chain..."
                            !nseSyncStatus.isNullOrBlank() ->
                                "ATM Est: ${stock.formattedComputedAtmStrike(initialSide)} • $nseSyncStatus"
                            else ->
                                "ATM Est: ${stock.formattedComputedAtmStrike(initialSide)} • Tap ↻ to fetch live LTP"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TvBullTeal,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh ATM option prices",
                            tint = TvTextPrimary,
                        )
                    }
                }
            }

            // Dual ATM CE & ATM PE Running Price Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AtmSidePriceTile(
                    title = "ATM CE (${stock.formattedComputedAtmStrike(OptionSide.CE)})",
                    quote = atmCe,
                    isSyncing = isSyncing,
                    accentColor = TvBullTeal,
                    bgColor = TvBullContainer,
                    selected = chainSide == OptionSide.CE,
                    onClick = { chainSide = OptionSide.CE },
                    modifier = Modifier.weight(1f),
                )
                AtmSidePriceTile(
                    title = "ATM PE (${stock.formattedComputedAtmStrike(OptionSide.PE)})",
                    quote = atmPe,
                    isSyncing = isSyncing,
                    accentColor = TvBearCoral,
                    bgColor = TvBearContainer,
                    selected = chainSide == OptionSide.PE,
                    onClick = { chainSide = OptionSide.PE },
                    modifier = Modifier.weight(1f),
                )
            }

            // Nearby Strikes Option Chain Mini-Table (ITM / ATM / OTM)
            if (nearby.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TvBackground)
                        .border(1.dp, TvDivider, RoundedCornerShape(8.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TvSurface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${chainSide.name} Strike", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, modifier = Modifier.weight(1.1f))
                        Text("Running ₹", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
                        Text("Chg%", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
                        Text("OI (Chg)", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                    }
                    HorizontalDivider(thickness = 1.dp, color = TvDivider)

                    nearby.forEachIndexed { idx, opt ->
                        val isAtm = activeAtmStrike != null && opt.strikePrice == activeAtmStrike
                        val optColor = if (opt.changePercent >= 0.0) TvBullTeal else TvBearCoral
                        val oiChgSign = if (opt.oiChange >= 0L) "+" else ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isAtm) {
                                        (if (chainSide == OptionSide.CE) TvBullContainer else TvBearContainer).copy(alpha = 0.55f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1.1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = opt.contractLabel,
                                    style = TabularNumberStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isAtm) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = TvTextBright,
                                )
                                if (isAtm) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ATM",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                        color = if (chainSide == OptionSide.CE) TvBullTeal else TvBearCoral,
                                    )
                                }
                            }
                            Text(
                                text = if (opt.runningPrice > 0.0) "₹${TechnicalIndicators.formatPrice(opt.runningPrice)}" else "--",
                                style = TabularNumberStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = TvTextBright,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.95f),
                            )
                            Text(
                                text = TechnicalIndicators.formatPercent(opt.changePercent),
                                style = TabularNumberStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                                color = optColor,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.85f),
                            )
                            Text(
                                text = if (opt.openInterest > 0L) {
                                    "${TechnicalIndicators.formatVolumeCompact(opt.openInterest)} ($oiChgSign${TechnicalIndicators.formatVolumeCompact(opt.oiChange)})"
                                } else {
                                    "--"
                                },
                                style = TabularNumberStyle.copy(fontSize = 9.5.sp),
                                color = TvTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.2f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (idx < nearby.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = TvDivider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtmSidePriceTile(
    title: String,
    quote: OptionStrikeQuote?,
    isSyncing: Boolean,
    accentColor: Color,
    bgColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) bgColor.copy(alpha = 0.65f) else TvBackground,
        border = BorderStroke(
            width = if (selected) 1.25.dp else 1.dp,
            color = if (selected) accentColor else TvDivider,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (quote != null && quote.runningPrice > 0.0) {
                val chgColor = if (quote.changePercent >= 0.0) TvBullTeal else TvBearCoral
                Text(
                    text = "₹${TechnicalIndicators.formatPrice(quote.runningPrice)}",
                    style = TabularNumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = TvTextBright,
                    maxLines = 1,
                )
                Text(
                    text = "${TechnicalIndicators.formatChange(quote.change)} (${TechnicalIndicators.formatPercent(quote.changePercent)})",
                    style = TabularNumberStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                    color = chgColor,
                    maxLines = 1,
                )
                Text(
                    text = "OI ${TechnicalIndicators.formatVolumeCompact(quote.openInterest)}",
                    style = TabularNumberStyle.copy(fontSize = 9.5.sp),
                    color = TvTextSecondary,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = "₹--",
                    style = TabularNumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = TvTextSecondary,
                )
                Text(
                    text = if (isSyncing) "Syncing NSE..." else "Tap ↻ to fetch LTP",
                    style = MaterialTheme.typography.labelSmall,
                    color = TvTextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PivotAndPcrAnalyticsCard(
    stock: StockScanResult,
    side: OptionSide,
) {
    val pivots = stock.pivotLevels
    val oiSummary = stock.oiDelivery
    val bundle = stock.atmOptions

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = TvSurfaceElevated,
        border = BorderStroke(1.dp, TvDivider),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pivot_pcr_analytics_card"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Standard Pivots (S1–S3, R1–R3) & F&O PCR",
                        style = MaterialTheme.typography.titleSmall,
                        color = TvTextBright,
                    )
                    Text(
                        text = if (pivots != null) {
                            "Prev H ₹${TechnicalIndicators.formatPrice(pivots.prevHigh)} • L ₹${TechnicalIndicators.formatPrice(pivots.prevLow)} • C ₹${TechnicalIndicators.formatPrice(pivots.prevClose)}"
                        } else {
                            "Waiting for prior session bars to compute Standard Floor Pivots"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (stock.isAboveR2 || stock.isBelowS2) {
                val isCeBreak = stock.isAboveR2
                val bannerAccent = if (isCeBreak) TvTrendBullGreen else TvTrendBearRed
                val bannerBg = if (isCeBreak) TvTrendBullBg else TvTrendBearBg
                val msg = when {
                    stock.isAboveR3 -> "⚡ ABOVE STANDARD R3 BREAKOUT — High CE Momentum Zone"
                    stock.isAboveR2 -> "⚡ ABOVE STANDARD R2 BREAKOUT — Watching R2 → R3 (CE Zone)"
                    stock.isBelowS3 -> "⚡ BELOW STANDARD S3 BREAKDOWN — Heavy PE Selling Zone"
                    else -> "⚡ BELOW STANDARD S2 BREAKDOWN — Watching S2 → S3 (PE Zone)"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bannerBg,
                    border = BorderStroke(1.dp, bannerAccent.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = bannerAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            // F&O PCR Analytics (4 KPI Tiles: Put/Call, Call/Put, PCR OI Chg%, Intraday ΔPut/ΔCall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pcrVal = oiSummary.pcr
                val pcrBias = when {
                    pcrVal == null -> "Sync NSE"
                    pcrVal >= 1.15 -> "Bullish Support (CE)"
                    pcrVal <= 0.75 -> "Bearish Cap (PE)"
                    else -> "Balanced Range"
                }
                val pcrBiasColor = when {
                    pcrVal == null -> TvTextSecondary
                    pcrVal >= 1.15 -> TvTrendBullGreen
                    pcrVal <= 0.75 -> TvTrendBearRed
                    else -> TvTextSecondary
                }
                PositionKpiTile(
                    label = "PCR (Put/Call OI)",
                    value = pcrVal?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    subValue = pcrBias,
                    subColor = pcrBiasColor,
                    modifier = Modifier.weight(1f),
                )

                val cpVal = oiSummary.callToPutRatio ?: pcrVal?.takeIf { it > 0.0 }?.let { 1.0 / it }
                val cpBias = when {
                    cpVal == null -> "Sync NSE"
                    cpVal >= 1.35 -> "Call Writers Dominant"
                    cpVal <= 0.85 -> "Put Writers Dominant"
                    else -> "Balanced Writers"
                }
                PositionKpiTile(
                    label = "Call/Put Ratio",
                    value = cpVal?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    subValue = cpBias,
                    subColor = if ((cpVal ?: 0.0) >= 1.35) TvTrendBearRed else TvTextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pcrChg = oiSummary.pcrOiChangePct
                val pcrChgText = pcrChg?.let {
                    val s = if (it >= 0.0) "+" else ""
                    "$s${String.format(Locale.US, "%.2f%%", it)}"
                } ?: "--"
                val pcrChgSub = when {
                    pcrChg == null -> "vs Prev Session OI"
                    pcrChg > 2.0 -> "Put writing rising (CE+)"
                    pcrChg < -2.0 -> "Call writing rising (PE+)"
                    else -> "Steady vs Prev OI"
                }
                val pcrChgColor = when {
                    pcrChg == null -> TvTextSecondary
                    pcrChg > 0.0 -> TvTrendBullGreen
                    pcrChg < 0.0 -> TvTrendBearRed
                    else -> TvTextSecondary
                }
                PositionKpiTile(
                    label = "F&O PCR OI Chg %",
                    value = pcrChgText,
                    subValue = pcrChgSub,
                    subColor = pcrChgColor,
                    modifier = Modifier.weight(1f),
                )

                val putChg = oiSummary.putOiChange
                val callChg = oiSummary.callOiChange
                val deltaVal = oiSummary.intradayDeltaPcr?.let {
                    String.format(Locale.US, "%.2fx", it)
                } ?: if (putChg != null && callChg != null) {
                    if (putChg > callChg) "Put Add+" else "Call Add+"
                } else {
                    "--"
                }
                val deltaSub = if (putChg != null && callChg != null) {
                    "ΔP ${TechnicalIndicators.formatVolumeCompact(putChg)} / ΔC ${TechnicalIndicators.formatVolumeCompact(callChg)}"
                } else {
                    "Intraday ΔPut / ΔCall"
                }
                PositionKpiTile(
                    label = "Intraday ΔP/ΔC OI",
                    value = deltaVal,
                    subValue = deltaSub,
                    subColor = if ((putChg ?: 0L) >= (callChg ?: 0L)) TvTrendBullGreen else TvTrendBearRed,
                    modifier = Modifier.weight(1f),
                )
            }

            // Option-Chain Max OI Walls
            if (bundle.maxPutOiStrike != null || bundle.maxCallOiStrike != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TvTrendBullBg.copy(alpha = 0.55f),
                        border = BorderStroke(0.75.dp, TvTrendBullGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Put Wall (Sup): ${bundle.maxPutOiStrike?.toInt() ?: "--"} (${TechnicalIndicators.formatVolumeCompact(bundle.maxPutOi)})",
                            style = TabularNumberStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = TvTrendBullGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TvTrendBearBg.copy(alpha = 0.55f),
                        border = BorderStroke(0.75.dp, TvTrendBearRed.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Call Wall (Res): ${bundle.maxCallOiStrike?.toInt() ?: "--"} (${TechnicalIndicators.formatVolumeCompact(bundle.maxCallOi)})",
                            style = TabularNumberStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = TvTrendBearRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 7-Level Standard Floor Pivot Ladder (R3 -> R2 -> R1 -> PP -> S1 -> S2 -> S3) + Option Prices
            if (pivots != null) {
                val projections = remember(stock.pivotLevels, stock.lastPrice, stock.atmOptions, side) {
                    OptionPivotPricing.buildPivotProjections(stock, side)
                }
                val r1Proj = projections.firstOrNull { it.levelLabel == "R1" }
                val r2Proj = projections.firstOrNull { it.levelLabel == "R2" }
                val r3Proj = projections.firstOrNull { it.levelLabel == "R3" }
                val s1Proj = projections.firstOrNull { it.levelLabel == "S1" }
                val s2Proj = projections.firstOrNull { it.levelLabel == "S2" }
                val s3Proj = projections.firstOrNull { it.levelLabel == "S3" }

                val formatOptOrDash: (PivotOptionProjection?) -> String = { proj ->
                    proj?.projectedAtmOptionPrice?.let { "₹${TechnicalIndicators.formatPrice(it)}" } ?: "--"
                }

                // Option Target Summary Strip: ATM CE @ R1/R2/R3 and ATM PE @ S1/S2/S3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pivot_option_target_strip"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val hasCeTargets = listOf(r1Proj, r2Proj, r3Proj).any { it?.projectedAtmOptionPrice != null }
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = TvTrendBullBg.copy(alpha = 0.45f),
                        border = BorderStroke(0.75.dp, TvBullTeal.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "ATM CE @ R1–R3 (${stock.formattedComputedAtmStrike(OptionSide.CE)})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = TvBullTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (hasCeTargets) {
                                    "R1 ${formatOptOrDash(r1Proj)} • R2 ${formatOptOrDash(r2Proj)} • R3 ${formatOptOrDash(r3Proj)}"
                                } else {
                                    "Sync NSE for R1–R3 CE LTP"
                                },
                                style = TabularNumberStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = if (hasCeTargets) TvTextBright else TvTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    val hasPeTargets = listOf(s1Proj, s2Proj, s3Proj).any { it?.projectedAtmOptionPrice != null }
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = TvTrendBearBg.copy(alpha = 0.45f),
                        border = BorderStroke(0.75.dp, TvBearCoral.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "ATM PE @ S1–S3 (${stock.formattedComputedAtmStrike(OptionSide.PE)})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = TvBearCoral,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (hasPeTargets) {
                                    "S1 ${formatOptOrDash(s1Proj)} • S2 ${formatOptOrDash(s2Proj)} • S3 ${formatOptOrDash(s3Proj)}"
                                } else {
                                    "Sync NSE for S1–S3 PE LTP"
                                },
                                style = TabularNumberStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = if (hasPeTargets) TvTextBright else TvTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                val levelColors = mapOf(
                    "R3" to TvTrendBearRed,
                    "R2" to TvBearCoral,
                    "R1" to TvWarningAmber,
                    "PP" to TvAccentBlue,
                    "S1" to TvBullTeal,
                    "S2" to TvTrendBullGreen,
                    "S3" to TvBullEmerald,
                )
                val nearestLevelLabel = projections.minByOrNull { kotlin.math.abs(it.spotLevelPrice - stock.lastPrice) }?.levelLabel

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TvBackground)
                        .border(1.dp, TvDivider, RoundedCornerShape(8.dp))
                        .testTag("pivot_option_ladder_table"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TvSurface)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Level", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, modifier = Modifier.weight(0.82f))
                        Text("Spot / Dist", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.98f))
                        Text("ATM Opt Est", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
                        Text("Strike @ Lvl", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.05f))
                    }
                    HorizontalDivider(thickness = 1.dp, color = TvDivider)

                    projections.forEachIndexed { idx, proj ->
                        val color = levelColors[proj.levelLabel] ?: TvTextBright
                        val isCrossedUp = proj.levelLabel.startsWith("R") && stock.lastPrice >= proj.spotLevelPrice
                        val isCrossedDown = proj.levelLabel.startsWith("S") && stock.lastPrice <= proj.spotLevelPrice
                        val isNearest = proj.levelLabel == nearestLevelLabel

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isNearest -> color.copy(alpha = 0.14f)
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(0.82f),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = if (proj.isPriorityLevel) "${proj.levelLabel} ★" else proj.levelLabel,
                                    style = TabularNumberStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = color,
                                )
                                when {
                                    isNearest -> Text(
                                        text = "SPOT NEAR",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                        color = TvTextBright,
                                    )
                                    isCrossedUp && side == OptionSide.CE -> Text(
                                        text = "BROKEN ↑",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                        color = TvTrendBullGreen,
                                    )
                                    isCrossedDown && side == OptionSide.PE -> Text(
                                        text = "BROKEN ↓",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                        color = TvTrendBearRed,
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(0.98f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = "₹${TechnicalIndicators.formatPrice(proj.spotLevelPrice)}",
                                    style = TabularNumberStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                                    color = TvTextBright,
                                    textAlign = TextAlign.End,
                                )
                                val sign = if (proj.distFromSpotPct > 0.0) "+" else ""
                                Text(
                                    text = "$sign${String.format(Locale.US, "%.2f%%", proj.distFromSpotPct)}",
                                    style = TabularNumberStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                                    color = if (proj.distFromSpotPct >= 0.0) TvBullTeal else TvBearCoral,
                                    textAlign = TextAlign.End,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(0.95f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                val optEst = proj.projectedAtmOptionPrice
                                Text(
                                    text = if (optEst != null) "~₹${TechnicalIndicators.formatPrice(optEst)}" else "--",
                                    style = TabularNumberStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                    color = if (optEst != null) {
                                        if (proj.side == OptionSide.CE) TvBullTeal else TvBearCoral
                                    } else {
                                        TvTextSecondary
                                    },
                                    textAlign = TextAlign.End,
                                )
                                Text(
                                    text = proj.atmContractLabel,
                                    style = TabularNumberStyle.copy(fontSize = 8.5.sp),
                                    color = TvTextSecondary,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1.05f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                val nq = proj.nearestStrikeQuote
                                if (nq != null && nq.runningPrice > 0.0) {
                                    Text(
                                        text = "₹${TechnicalIndicators.formatPrice(nq.runningPrice)}",
                                        style = TabularNumberStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                                        color = TvTextBright,
                                        textAlign = TextAlign.End,
                                    )
                                    Text(
                                        text = nq.contractLabel,
                                        style = TabularNumberStyle.copy(fontSize = 8.5.sp),
                                        color = TvTextSecondary,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        text = nq?.contractLabel ?: "--",
                                        style = TabularNumberStyle.copy(fontSize = 9.5.sp),
                                        color = TvTextSecondary,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (nq != null) {
                                        Text(
                                            text = "LTP --",
                                            style = TabularNumberStyle.copy(fontSize = 8.5.sp),
                                            color = TvTextSecondary,
                                            textAlign = TextAlign.End,
                                        )
                                    }
                                }
                            }
                        }
                        if (idx < projections.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = TvDivider)
                        }
                    }
                }
            }
        }
    }
}
