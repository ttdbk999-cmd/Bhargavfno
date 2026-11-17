package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.indicator.RsiFrequencyStat
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.indicator.TrendPersistence
import com.example.domain.model.OptionSide
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAmberContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearContainer
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBullContainer
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextBright
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextSecondary
import com.example.ui.theme.TvTrendBearBg
import com.example.ui.theme.TvTrendBearRed
import com.example.ui.theme.TvTrendBullBg
import com.example.ui.theme.TvTrendBullGreen
import com.example.ui.theme.TvWarningAmber

@Composable
fun RsiTriggerFrequencyWidget(
    stats: List<RsiFrequencyStat>,
    savedWatchlistSymbols: Set<String> = emptySet(),
    onStockClick: (symbol: String, preferredSide: OptionSide) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val sustainedCount = stats.count { it.persistence == TrendPersistence.SUSTAINED }
    val fleetingCount = stats.count { it.persistence == TrendPersistence.FLEETING }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .testTag("rsi_frequency_dashboard_widget"),
        shape = RoundedCornerShape(10.dp),
        color = TvSurfaceElevated,
        border = BorderStroke(1.dp, TvDivider),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(TvSurface, TvSurfaceElevated),
                        ),
                    )
                    .clickable { expanded = !expanded }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Insights,
                        contentDescription = null,
                        tint = TvAccentBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text(
                            text = "60m RSI (70/30) TRIGGER PULSE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                            ),
                            color = TvTextBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (stats.isEmpty()) {
                                "Monitoring 1m/5m/15m RSI 70/30 crosses over rolling 60m window"
                            } else {
                                "Top momentum trigger frequency • Sustained vs fleeting"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = TvTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = TvTrendBullBg,
                        border = BorderStroke(0.6.dp, TvTrendBullGreen.copy(alpha = 0.55f)),
                    ) {
                        Text(
                            text = "Sust:$sustainedCount",
                            style = TabularNumberStyle.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = TvTrendBullGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = TvBackground,
                        border = BorderStroke(0.6.dp, TvDivider),
                    ) {
                        Text(
                            text = "Fleet:$fleetingCount",
                            style = TabularNumberStyle.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TvWarningAmber,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Toggle 60m RSI trigger pulse widget",
                        tint = TvTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                if (stats.isEmpty()) {
                    Text(
                        text = "No RSI >70 or <30 triggers recorded in the last 60 minutes across scanned F&O stocks yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 9.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = stats,
                            key = { "rsi_freq_${it.symbol}" },
                        ) { stat ->
                            RsiFrequencyStockCard(
                                stat = stat,
                                isStarred = stat.symbol in savedWatchlistSymbols,
                                onClick = { onStockClick(stat.symbol, stat.dominantSide) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RsiFrequencyStockCard(
    stat: RsiFrequencyStat,
    isStarred: Boolean,
    onClick: () -> Unit,
) {
    val isCe = stat.dominantSide == OptionSide.CE
    val sideAccent = if (isCe) TvBullTeal else TvBearCoral
    val strongAccent = if (isCe) TvTrendBullGreen else TvTrendBearRed
    val cardBorder = when (stat.persistence) {
        TrendPersistence.SUSTAINED -> strongAccent.copy(alpha = 0.75f)
        TrendPersistence.BUILDING -> sideAccent.copy(alpha = 0.55f)
        TrendPersistence.FLEETING -> TvDivider
    }
    val persistenceBg = when (stat.persistence) {
        TrendPersistence.SUSTAINED -> if (isCe) TvTrendBullBg else TvTrendBearBg
        TrendPersistence.BUILDING -> if (isCe) TvBullContainer.copy(alpha = 0.65f) else TvBearContainer.copy(alpha = 0.65f)
        TrendPersistence.FLEETING -> TvAmberContainer.copy(alpha = 0.55f)
    }
    val persistenceColor = when (stat.persistence) {
        TrendPersistence.SUSTAINED -> strongAccent
        TrendPersistence.BUILDING -> sideAccent
        TrendPersistence.FLEETING -> TvWarningAmber
    }

    val tfSummary = stat.timeframeBreakdown.entries
        .joinToString(" • ") { (tf, cnt) -> "${tf.label}×$cnt" }
        .ifEmpty { "Dwell only" }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = TvSurface,
        border = BorderStroke(
            width = if (stat.persistence == TrendPersistence.SUSTAINED) 1.1.dp else 0.85.dp,
            color = cardBorder,
        ),
        modifier = Modifier
            .width(196.dp)
            .testTag("rsi_freq_card_${stat.symbol}"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Top Row: Symbol + Star + Side + Frequency Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isStarred) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = TvWarningAmber,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = stat.symbol,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                        ),
                        color = TvTextBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isCe) ">70" else "<30",
                        style = TabularNumberStyle.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = sideAccent,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = sideAccent.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = "${stat.totalTriggers60m}× / 60m",
                        style = TabularNumberStyle.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TvTextBright,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
            }

            // Middle Row: Persistence classification + 1m Dwell & Chg%
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = persistenceBg,
                ) {
                    Text(
                        text = stat.persistence.label,
                        style = TabularNumberStyle.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = persistenceColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
                Text(
                    text = "${stat.extremeDwellMinutes1m}m dwell • ${TechnicalIndicators.formatPercent(stat.changePercent)}",
                    style = TabularNumberStyle.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (stat.changePercent >= 0.0) TvBullTeal else TvBearCoral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 12-Bucket 60m Frequency Heatmap (-60m -> NOW)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            ) {
                stat.buckets12.forEach { bucket ->
                    val bucketHeight = when {
                        bucket.triggerCount >= 2 -> 14.dp
                        bucket.triggerCount == 1 -> 10.dp
                        bucket.hasExtremeDwell -> 6.dp
                        else -> 3.dp
                    }
                    val bucketColor = when {
                        bucket.triggerCount > 0 -> {
                            if (bucket.dominantSide == OptionSide.PE) TvTrendBearRed else TvTrendBullGreen
                        }
                        bucket.hasExtremeDwell -> {
                            if (bucket.dominantSide == OptionSide.PE) TvBearCoral.copy(alpha = 0.65f) else TvBullTeal.copy(alpha = 0.65f)
                        }
                        else -> TvDivider.copy(alpha = 0.7f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(bucketHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(bucketColor),
                    )
                }
            }

            // Bottom Footer: 60m Axis + Timeframe Triggers Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tfSummary,
                    style = TabularNumberStyle.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.5.sp,
                    ),
                    color = TvTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "-60m → NOW",
                    style = TabularNumberStyle.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 7.5.sp,
                    ),
                    color = TvTextMuted,
                )
            }
        }
    }
}
