package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.indicator.OptionPivotPricing
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.BoardViewMode
import com.example.domain.model.BreakoutFilterMode
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.OptionSide
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.ScanProgress
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.SortColumn
import com.example.domain.model.StockScanResult
import com.example.domain.model.TrendDirection
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAccentBlueContainer
import com.example.ui.theme.TvAmberContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearContainer
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBearFlash
import com.example.ui.theme.TvBearRed
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvBullContainer
import com.example.ui.theme.TvBullEmerald
import com.example.ui.theme.TvBullFlash
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextBright
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.theme.TvTrendBearBg
import com.example.ui.theme.TvTrendBearRed
import com.example.ui.theme.TvTrendBullBg
import com.example.ui.theme.TvTrendBullGreen
import com.example.ui.theme.TvTrendFlat
import com.example.ui.theme.TvWarningAmber
import kotlinx.coroutines.delay

@Composable
fun GapboardTopHeader(
    secondsUntilRefresh: Int,
    refreshIntervalSec: Int,
    autoRefreshEnabled: Boolean,
    scanProgress: ScanProgress,
    lastScanTimeIst: String,
    unreadAlertCount: Int = 0,
    totalAlertCount: Int = 0,
    savedWatchlistCount: Int = 0,
    onToggleAutoRefresh: () -> Unit,
    onRefreshNow: () -> Unit,
    onOpenAlertCenter: () -> Unit = {},
    onOpenFilters: () -> Unit,
    onOpenWatchlist: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin_angle",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Brand badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(TvBullContainer, TvAccentBlueContainer),
                            )
                        )
                        .border(1.dp, TvBorderSubtle, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = TvBullTeal,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FnO GapBoard",
                            style = MaterialTheme.typography.titleMedium,
                            color = TvTextBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Countdown ring pill
                        LiveCountdownPill(
                            secondsRemaining = secondsUntilRefresh,
                            intervalSeconds = refreshIntervalSec,
                            autoRefreshEnabled = autoRefreshEnabled,
                            isScanning = scanProgress.isScanning,
                            onClick = onToggleAutoRefresh,
                        )
                    }
                    Text(
                        text = if (scanProgress.isScanning) {
                            "Scanning NSE F&O ${scanProgress.completed}/${scanProgress.total}..."
                        } else {
                            "NSE F&O • Updated $lastScanTimeIst"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenAlertCenter,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_alert_center_button"),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = stringResource(R.string.cd_open_alert_center),
                            tint = if (unreadAlertCount > 0) TvWarningAmber else if (totalAlertCount > 0) TvBullTeal else TvTextPrimary,
                        )
                        if (unreadAlertCount > 0 || totalAlertCount > 0) {
                            val badgeNum = if (unreadAlertCount > 0) unreadAlertCount else totalAlertCount
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (unreadAlertCount > 0) TvBearRed else TvAccentBlue)
                                    .padding(horizontal = 4.dp, vertical = 0.5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (badgeNum > 99) "99+" else badgeNum.toString(),
                                    style = TextStyleMiniMono.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onToggleAutoRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_autorefresh_button"),
                ) {
                    Icon(
                        imageVector = if (autoRefreshEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_toggle_autorefresh),
                        tint = if (autoRefreshEnabled) TvBullTeal else TvWarningAmber,
                    )
                }
                IconButton(
                    onClick = onRefreshNow,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_now_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_now),
                        tint = TvTextPrimary,
                        modifier = if (scanProgress.isScanning) Modifier.rotate(spinAngle) else Modifier,
                    )
                }
                IconButton(
                    onClick = onOpenFilters,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_filters_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.cd_filter_settings),
                        tint = TvTextPrimary,
                    )
                }
                IconButton(
                    onClick = onOpenWatchlist,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_watchlist_button"),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = if (savedWatchlistCount > 0) Icons.Rounded.Star else Icons.Rounded.ListAlt,
                            contentDescription = stringResource(R.string.cd_manage_watchlist),
                            tint = if (savedWatchlistCount > 0) TvWarningAmber else TvTextPrimary,
                        )
                        if (savedWatchlistCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TvAccentBlue)
                                    .padding(horizontal = 4.dp, vertical = 0.5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (savedWatchlistCount > 99) "99+" else savedWatchlistCount.toString(),
                                    style = TextStyleMiniMono.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (scanProgress.isScanning && scanProgress.total > 0) {
            LinearProgressIndicator(
                progress = { (scanProgress.completed.toFloat() / scanProgress.total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = TvBullTeal,
                trackColor = TvDivider,
            )
        } else {
            HorizontalDivider(thickness = 1.dp, color = TvDivider)
        }
    }
}

@Composable
private fun LiveCountdownPill(
    secondsRemaining: Int,
    intervalSeconds: Int,
    autoRefreshEnabled: Boolean,
    isScanning: Boolean,
    onClick: () -> Unit,
) {
    val progress = if (intervalSeconds > 0) {
        (secondsRemaining.toFloat() / intervalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val ringColor = when {
        !autoRefreshEnabled -> TvWarningAmber
        isScanning -> TvAccentBlue
        else -> TvBullTeal
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = TvSurfaceElevated,
        border = BorderStroke(1.dp, ringColor.copy(alpha = 0.45f)),
        modifier = Modifier.testTag("countdown_pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.25f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (!autoRefreshEnabled) "PAUSED" else if (isScanning) "SYNC" else "${secondsRemaining}s",
                style = TabularNumberStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = ringColor,
            )
        }
    }
}

@Composable
fun ActiveRulesStrip(
    thresholds: ScannerThresholds,
    ceMatchedCount: Int,
    peMatchedCount: Int,
    matchedOnly: Boolean,
    watchlistOnly: Boolean = false,
    savedWatchlistCount: Int = 0,
    breakoutFilter: BreakoutFilterMode = BreakoutFilterMode.ALL,
    ceAboveR1Count: Int = 0,
    ceAboveR2Count: Int = 0,
    ceAboveR3Count: Int = 0,
    peBelowS1Count: Int = 0,
    peBelowS2Count: Int = 0,
    peBelowS3Count: Int = 0,
    onToggleMatchedOnly: () -> Unit,
    onToggleWatchlistOnly: () -> Unit = {},
    onToggleR2R3: () -> Unit = {},
    onToggleAboveR3: () -> Unit = {},
    onToggleS2S3: () -> Unit = {},
    onToggleBelowS3: () -> Unit = {},
    onToggleSoundAlerts: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvBackground)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dedicated Room Watchlist Filter Chip
        Surface(
            onClick = onToggleWatchlistOnly,
            shape = RoundedCornerShape(6.dp),
            color = when {
                watchlistOnly -> TvAmberContainer
                savedWatchlistCount > 0 -> TvAmberContainer.copy(alpha = 0.45f)
                else -> TvSurface
            },
            border = BorderStroke(
                width = if (watchlistOnly) 1.25.dp else 1.dp,
                color = if (watchlistOnly || savedWatchlistCount > 0) TvWarningAmber.copy(alpha = if (watchlistOnly) 1f else 0.6f) else TvDivider,
            ),
            modifier = Modifier.testTag("watchlist_only_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (savedWatchlistCount > 0 || watchlistOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (watchlistOnly || savedWatchlistCount > 0) TvWarningAmber else TvTextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Watchlist ($savedWatchlistCount)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (watchlistOnly || savedWatchlistCount > 0) TvWarningAmber else TvTextSecondary,
                )
            }
        }

        // CE / PE signal summary quick toggle
        Surface(
            onClick = onToggleMatchedOnly,
            shape = RoundedCornerShape(6.dp),
            color = if (matchedOnly) TvAccentBlueContainer else TvSurface,
            border = BorderStroke(1.dp, if (matchedOnly) TvAccentBlue else TvDivider),
            modifier = Modifier.testTag("matched_only_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = if (matchedOnly) TvTextBright else TvTextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (matchedOnly) "5/5 Signals Only" else "All Gapboard",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (matchedOnly) TvTextBright else TvTextPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CE:$ceMatchedCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = TvTrendBullGreen,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PE:$peMatchedCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = TvTrendBearRed,
                )
            }
        }

        // Dedicated R2 & R3 Watch Filter Chip (Important CE levels)
        val isR2R3Active = breakoutFilter == BreakoutFilterMode.R2_R3
        Surface(
            onClick = onToggleR2R3,
            shape = RoundedCornerShape(6.dp),
            color = when {
                isR2R3Active -> TvTrendBullBg
                ceAboveR2Count > 0 -> TvTrendBullBg.copy(alpha = 0.55f)
                else -> TvSurface
            },
            border = BorderStroke(
                width = if (isR2R3Active) 1.25.dp else 1.dp,
                color = when {
                    isR2R3Active -> TvTrendBullGreen
                    ceAboveR2Count > 0 -> TvTrendBullGreen.copy(alpha = 0.65f)
                    else -> TvDivider
                },
            ),
            modifier = Modifier.testTag("r2_r3_filter_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = TvTrendBullGreen,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "R2 & R3 ($ceAboveR2Count)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isR2R3Active || ceAboveR2Count > 0) TvTrendBullGreen else TvTextSecondary,
                )
            }
        }

        // Dedicated > R3 Only Filter Chip
        val isAboveR3Active = breakoutFilter == BreakoutFilterMode.ABOVE_R3
        Surface(
            onClick = onToggleAboveR3,
            shape = RoundedCornerShape(6.dp),
            color = if (isAboveR3Active) TvTrendBullBg else TvSurface,
            border = BorderStroke(
                width = if (isAboveR3Active) 1.25.dp else 1.dp,
                color = if (isAboveR3Active) TvTrendBullGreen else if (ceAboveR3Count > 0) TvBullTeal.copy(alpha = 0.6f) else TvDivider,
            ),
            modifier = Modifier.testTag("above_r3_filter_chip"),
        ) {
            Text(
                text = "> R3 ($ceAboveR3Count)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isAboveR3Active || ceAboveR3Count > 0) TvBullTeal else TvTextSecondary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }

        // Dedicated S2 & S3 Watch Filter Chip (Important PE levels)
        val isS2S3Active = breakoutFilter == BreakoutFilterMode.S2_S3
        Surface(
            onClick = onToggleS2S3,
            shape = RoundedCornerShape(6.dp),
            color = when {
                isS2S3Active -> TvTrendBearBg
                peBelowS2Count > 0 -> TvTrendBearBg.copy(alpha = 0.50f)
                else -> TvSurface
            },
            border = BorderStroke(
                width = if (isS2S3Active) 1.25.dp else 1.dp,
                color = when {
                    isS2S3Active -> TvTrendBearRed
                    peBelowS2Count > 0 -> TvTrendBearRed.copy(alpha = 0.6f)
                    else -> TvDivider
                },
            ),
            modifier = Modifier.testTag("s2_s3_filter_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = TvTrendBearRed,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "S2 & S3 ($peBelowS2Count)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isS2S3Active || peBelowS2Count > 0) TvTrendBearRed else TvTextSecondary,
                )
            }
        }

        // Dedicated < S3 Only Filter Chip
        val isBelowS3Active = breakoutFilter == BreakoutFilterMode.BELOW_S3
        Surface(
            onClick = onToggleBelowS3,
            shape = RoundedCornerShape(6.dp),
            color = if (isBelowS3Active) TvTrendBearBg else TvSurface,
            border = BorderStroke(
                width = if (isBelowS3Active) 1.25.dp else 1.dp,
                color = if (isBelowS3Active) TvTrendBearRed else if (peBelowS3Count > 0) TvBearCoral.copy(alpha = 0.55f) else TvDivider,
            ),
            modifier = Modifier.testTag("below_s3_filter_chip"),
        ) {
            Text(
                text = "< S3 ($peBelowS3Count)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isBelowS3Active || peBelowS3Count > 0) TvBearCoral else TvTextSecondary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }

        // Early R1 / S1 awareness counter pill
        RulePill("R1:$ceAboveR1Count • S1:$peBelowS1Count", onOpenFilters)

        // Quick Sound Alert Mute/Unmute Chip
        val soundOn = thresholds.soundAlertsEnabled
        Surface(
            onClick = onToggleSoundAlerts,
            shape = RoundedCornerShape(6.dp),
            color = if (soundOn) TvTrendBullBg else TvSurface,
            border = BorderStroke(1.dp, if (soundOn) TvTrendBullGreen.copy(alpha = 0.65f) else TvDivider),
            modifier = Modifier.testTag("sound_quick_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (soundOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = stringResource(R.string.cd_toggle_sound),
                    tint = if (soundOn) TvTrendBullGreen else TvTextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (soundOn) "Sound ON" else "Muted",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (soundOn) TvTrendBullGreen else TvTextSecondary,
                )
            }
        }

        RulePill("1m RSI > ${thresholds.minRsi1m.toInt()} (${if (thresholds.require1mAlignment) "Align" else "Opt"})", onOpenFilters)
        RulePill("5m RSI > ${thresholds.minRsi5m.toInt()}", onOpenFilters)
        RulePill("15m RSI > ${thresholds.minRsi15m.toInt()}", onOpenFilters)
        RulePill("1h RSI > ${thresholds.minRsi1h.toInt()}", onOpenFilters)
        RulePill("RVOL > ${thresholds.minRvol}", onOpenFilters)
        RulePill("Shares > ${TechnicalIndicators.formatVolumeCompact(thresholds.minShares)}", onOpenFilters)
        RulePill("⟳ ${thresholds.refreshIntervalSec}s", onOpenFilters)
    }
}

@Composable
private fun RulePill(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = TvSurface,
        border = BorderStroke(1.dp, TvDivider),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TvTextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun GapboardModeAndSearchBar(
    boardViewMode: BoardViewMode,
    searchQuery: String,
    onSelectMode: (BoardViewMode) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvBackground)
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Segmented Board View Switcher (Gapboard CE | PE, CE Calls, PE Puts)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvSurface)
                    .border(1.dp, TvDivider, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                BoardViewMode.entries.forEach { mode ->
                    val selected = mode == boardViewMode
                    val bgColor = when {
                        selected && mode == BoardViewMode.CE_ONLY -> TvBullContainer
                        selected && mode == BoardViewMode.PE_ONLY -> TvBearContainer
                        selected -> TvSurfaceHighlight
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        selected && mode == BoardViewMode.CE_ONLY -> TvBullTeal
                        selected && mode == BoardViewMode.PE_ONLY -> TvBearCoral
                        selected -> TvTextBright
                        else -> TvTextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .clickable { onSelectMode(mode) }
                            .padding(vertical = 8.dp)
                            .testTag("mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Compact TradingView symbol search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TvSurface)
                .border(1.dp, TvDivider, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = TvTextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Filter F&O ticker (e.g. POWERINDIA, MCX, NMDC)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TvTextBright),
                    cursorBrush = SolidColor(TvAccentBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("symbol_search_input"),
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_sheet),
                        tint = TvTextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PivotWatchAlertBanner(
    priorityStocks: List<StockScanResult>,
    newlyCrossedSymbols: Set<String>,
    breakoutFilter: BreakoutFilterMode,
    totalAlertsCount: Int,
    dismissed: Boolean,
    onToggleR2R3Filter: () -> Unit,
    onToggleS2S3Filter: () -> Unit,
    onOpenAlertCenter: () -> Unit,
    onDismiss: () -> Unit,
    onStockClick: (StockScanResult, OptionSide) -> Unit,
) {
    val visible = priorityStocks.isNotEmpty() && !dismissed
    val ceCount = priorityStocks.count { it.isAboveR2 }
    val peCount = priorityStocks.count { it.isBelowS2 }
    val pulseTransition = rememberInfiniteTransition(label = "pivot_banner_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pivot_pulse_alpha",
    )

    val borderAccent = when {
        breakoutFilter == BreakoutFilterMode.S2_S3 || breakoutFilter == BreakoutFilterMode.BELOW_S3 -> TvTrendBearRed
        ceCount > 0 && peCount == 0 -> TvTrendBullGreen
        peCount > 0 && ceCount == 0 -> TvTrendBearRed
        else -> TvWarningAmber
    }

    AnimatedVisibility(visible = visible) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
                .testTag("pivot_watch_notification_banner"),
            shape = RoundedCornerShape(10.dp),
            color = TvSurfaceElevated,
            border = BorderStroke(
                width = 1.25.dp,
                color = borderAccent.copy(alpha = pulseAlpha),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                if (ceCount >= peCount) TvTrendBullBg else TvTrendBearBg,
                                TvSurfaceElevated,
                            )
                        )
                    )
                    .padding(top = 6.dp, bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = borderAccent.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(16.dp),
                        )
                        Column {
                            Text(
                                text = "⚡ LIVE PIVOT WATCH • R2/R3: $ceCount  |  S2/S3: $peCount",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                ),
                                color = TvTextBright,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (newlyCrossedSymbols.isNotEmpty()) {
                                Text(
                                    text = "JUST CROSSED R2/R3/S2/S3: ${newlyCrossedSymbols.joinToString(", ")}",
                                    style = TextStyleMiniMono.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.5.sp,
                                    ),
                                    color = TvWarningAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = "Priority levels: R2, R3 (CE) & S2, S3 (PE) • Tap stock or filter",
                                    style = TextStyleMiniMono.copy(fontSize = 8.5.sp),
                                    color = TvTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val r2r3Selected = breakoutFilter == BreakoutFilterMode.R2_R3 || breakoutFilter == BreakoutFilterMode.ABOVE_R3
                        val s2s3Selected = breakoutFilter == BreakoutFilterMode.S2_S3 || breakoutFilter == BreakoutFilterMode.BELOW_S3
                        if (ceCount > 0) {
                            Surface(
                                onClick = onToggleR2R3Filter,
                                shape = RoundedCornerShape(6.dp),
                                color = if (r2r3Selected) TvTrendBullGreen else TvBullContainer,
                                border = BorderStroke(1.dp, TvTrendBullGreen),
                                modifier = Modifier.testTag("banner_filter_r2_r3_button"),
                            ) {
                                Text(
                                    text = if (r2r3Selected) "R2/R3 ✓" else "R2/R3",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (r2r3Selected) TvBackground else TvTrendBullGreen,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                )
                            }
                        }
                        if (peCount > 0) {
                            Surface(
                                onClick = onToggleS2S3Filter,
                                shape = RoundedCornerShape(6.dp),
                                color = if (s2s3Selected) TvTrendBearRed else TvBearContainer,
                                border = BorderStroke(1.dp, TvTrendBearRed),
                                modifier = Modifier.testTag("banner_filter_s2_s3_button"),
                            ) {
                                Text(
                                    text = if (s2s3Selected) "S2/S3 ✓" else "S2/S3",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (s2s3Selected) TvBackground else TvTrendBearRed,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Surface(
                            onClick = onOpenAlertCenter,
                            shape = RoundedCornerShape(6.dp),
                            color = TvAccentBlueContainer,
                            border = BorderStroke(0.75.dp, TvAccentBlue),
                            modifier = Modifier.testTag("banner_open_alerts_button"),
                        ) {
                            Text(
                                text = "Log ($totalAlertsCount)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TvTextBright,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dismiss_r3_banner_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_dismiss_r3_banner),
                                tint = TvTextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = priorityStocks,
                        key = { "pivot_banner_${it.symbol}" },
                    ) { stock ->
                        val isCeSide = stock.isAboveR2
                        val side = if (isCeSide) OptionSide.CE else OptionSide.PE
                        val isNew = stock.symbol in newlyCrossedSymbols
                        val levelTag = when {
                            stock.isAboveR3 -> "+${String.format(java.util.Locale.US, "%.2f%%", stock.percentAboveR3() ?: 0.0)} >R3"
                            stock.isAboveR2 -> "+${String.format(java.util.Locale.US, "%.2f%%", stock.percentAboveR2() ?: 0.0)} >R2"
                            stock.isBelowS3 -> "-${String.format(java.util.Locale.US, "%.2f%%", stock.percentBelowS3() ?: 0.0)} <S3"
                            else -> "-${String.format(java.util.Locale.US, "%.2f%%", stock.percentBelowS2() ?: 0.0)} <S2"
                        }
                        val chipAccent = if (isCeSide) TvTrendBullGreen else TvTrendBearRed
                        val atmOption = stock.atmOptionFor(side)

                        Surface(
                            onClick = { onStockClick(stock, side) },
                            shape = RoundedCornerShape(7.dp),
                            color = TvSurface,
                            border = BorderStroke(
                                width = if (isNew) 1.25.dp else 0.75.dp,
                                color = if (isNew) TvWarningAmber else chipAccent.copy(alpha = 0.7f),
                            ),
                            modifier = Modifier.testTag("pivot_banner_chip_${stock.symbol}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (isNew) {
                                    Text(
                                        text = "NEW",
                                        style = TextStyleMiniMono.copy(
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = TvBackground,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(TvWarningAmber)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                                Text(
                                    text = stock.symbol,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TvTextBright,
                                )
                                Text(
                                    text = levelTag,
                                    style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold),
                                    color = chipAccent,
                                )
                                Text(
                                    text = "₹${TechnicalIndicators.formatPrice(stock.lastPrice)}",
                                    style = TextStyleMiniMono,
                                    color = TvTextPrimary,
                                )
                                if (atmOption != null && atmOption.runningPrice > 0.0) {
                                    Text(
                                        text = "• ${atmOption.contractLabel} ₹${TechnicalIndicators.formatPrice(atmOption.runningPrice)}",
                                        style = TextStyleMiniMono,
                                        color = if (isCeSide) TvBullTeal else TvBearCoral,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GapboardTablePanel(
    side: OptionSide,
    stocks: List<StockScanResult>,
    thresholds: ScannerThresholds,
    sortColumn: SortColumn,
    sortDescending: Boolean,
    matchedOnly: Boolean,
    watchlistOnly: Boolean = false,
    savedWatchlistSymbols: Set<String> = emptySet(),
    breakoutFilter: BreakoutFilterMode = BreakoutFilterMode.ALL,
    newlyCrossedPrioritySymbols: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    onClearBreakoutFilter: () -> Unit = {},
    onClearWatchlistFilter: () -> Unit = {},
    onSortClick: (SortColumn) -> Unit,
    onStockClick: (StockScanResult) -> Unit,
    onToggleWatchlistStock: (StockScanResult) -> Unit = {},
) {
    val isCe = side == OptionSide.CE
    val isDedicatedR2R3 = isCe && breakoutFilter == BreakoutFilterMode.R2_R3
    val isDedicatedR3 = isCe && breakoutFilter == BreakoutFilterMode.ABOVE_R3
    val isDedicatedS2S3 = !isCe && breakoutFilter == BreakoutFilterMode.S2_S3
    val isDedicatedS3 = !isCe && breakoutFilter == BreakoutFilterMode.BELOW_S3
    val isDedicatedBreakout = isDedicatedR2R3 || isDedicatedR3 || isDedicatedS2S3 || isDedicatedS3

    val accentColor = when {
        isDedicatedR2R3 || isDedicatedR3 -> TvTrendBullGreen
        isDedicatedS2S3 || isDedicatedS3 -> TvTrendBearRed
        isCe -> TvBullTeal
        else -> TvBearCoral
    }
    val headerBg = if (isCe) TvBullContainer else TvBearContainer
    val passedCount = stocks.count {
        if (isCe) it.evaluateCe(thresholds).allPassed else it.evaluatePe(thresholds).allPassed
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("gapboard_${side.name.lowercase()}_panel"),
        shape = RoundedCornerShape(10.dp),
        color = TvSurface,
        border = BorderStroke(
            width = if (isDedicatedBreakout) 1.25.dp else 1.dp,
            color = when {
                isDedicatedR2R3 || isDedicatedR3 -> TvTrendBullGreen.copy(alpha = 0.7f)
                isDedicatedS2S3 || isDedicatedS3 -> TvTrendBearRed.copy(alpha = 0.7f)
                else -> TvDivider
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Board Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg.copy(alpha = if (isDedicatedBreakout) 0.9f else 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isDedicatedR3 -> "⚡ > R3 BREAKOUTS • CE MOMENTUM ↑"
                            isDedicatedR2R3 -> "⚡ R2 & R3 WATCHLIST • CE BREAKOUTS ↑"
                            isDedicatedS3 -> "⚡ < S3 BREAKDOWNS • PE MOMENTUM ↓"
                            isDedicatedS2S3 -> "⚡ S2 & S3 WATCHLIST • PE BREAKDOWNS ↓"
                            isCe -> "CE GAPBOARD • BULLISH ↑"
                            else -> "PE GAPBOARD • BEARISH ↓"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = accentColor,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isDedicatedBreakout) {
                        Surface(
                            onClick = onClearBreakoutFilter,
                            shape = RoundedCornerShape(4.dp),
                            color = TvSurfaceElevated,
                            border = BorderStroke(0.75.dp, TvDivider),
                            modifier = Modifier.testTag("header_clear_breakout_button"),
                        ) {
                            Text(
                                text = "All Stocks",
                                style = MaterialTheme.typography.labelSmall,
                                color = TvTextBright,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = "$passedCount 5/5",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "${stocks.size} stocks",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                    )
                }
            }

            // TradingView Column Header Row (Symbol | Last | Chg | Chg% | Vol)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortableHeaderCell(
                    label = "Symbol",
                    column = SortColumn.SYMBOL,
                    activeColumn = sortColumn,
                    descending = sortDescending,
                    alignStart = true,
                    modifier = Modifier.weight(1.45f),
                    onClick = { onSortClick(SortColumn.SYMBOL) },
                )
                SortableHeaderCell(
                    label = "Last",
                    column = SortColumn.LAST,
                    activeColumn = sortColumn,
                    descending = sortDescending,
                    modifier = Modifier.weight(0.9f),
                    onClick = { onSortClick(SortColumn.LAST) },
                )
                SortableHeaderCell(
                    label = "Chg",
                    column = SortColumn.CHG,
                    activeColumn = sortColumn,
                    descending = sortDescending,
                    modifier = Modifier.weight(0.75f),
                    onClick = { onSortClick(SortColumn.CHG) },
                )
                SortableHeaderCell(
                    label = "Chg%",
                    column = SortColumn.CHG_PCT,
                    activeColumn = sortColumn,
                    descending = sortDescending,
                    modifier = Modifier.weight(0.85f),
                    onClick = { onSortClick(SortColumn.CHG_PCT) },
                )
                SortableHeaderCell(
                    label = "Vol",
                    column = SortColumn.VOL,
                    activeColumn = sortColumn,
                    descending = sortDescending,
                    modifier = Modifier.weight(0.85f),
                    onClick = { onSortClick(SortColumn.VOL) },
                )
            }

            HorizontalDivider(thickness = 1.dp, color = TvDivider)

            if (stocks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                watchlistOnly -> "No Watchlist stocks on ${side.name} board"
                                isDedicatedR3 -> "No F&O stocks currently trading above R3"
                                isDedicatedR2R3 -> "No F&O stocks currently trading above R2 or R3"
                                isDedicatedS3 -> "No F&O stocks currently trading below S3"
                                isDedicatedS2S3 -> "No F&O stocks currently trading below S2 or S3"
                                matchedOnly -> "No ${side.name} stocks currently match all 5 criteria"
                                else -> "No ${side.name} movers matching search"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = TvTextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                watchlistOnly -> "Tap ★ on any F&O stock row or chart to save it locally to your Watchlist for faster tracking."
                                isDedicatedR2R3 || isDedicatedR3 -> "Watching Standard R2 & R3 resistance every ${thresholds.refreshIntervalSec}s. Alerts trigger automatically on crosses."
                                isDedicatedS2S3 || isDedicatedS3 -> "Watching Standard S2 & S3 support every ${thresholds.refreshIntervalSec}s. Alerts trigger automatically on crosses."
                                matchedOnly -> "Tap '5/5 Signals Only' above to view the full live Gapboard, or adjust RSI/RVOL thresholds."
                                else -> "Refreshing automatically every ${thresholds.refreshIntervalSec}s."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (isDedicatedBreakout || watchlistOnly) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                onClick = if (watchlistOnly) onClearWatchlistFilter else onClearBreakoutFilter,
                                shape = RoundedCornerShape(8.dp),
                                color = TvAccentBlueContainer,
                                border = BorderStroke(1.dp, TvAccentBlue),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag(if (watchlistOnly) "clear_watchlist_filter_button" else "clear_r3_filter_button"),
                            ) {
                                Text(
                                    text = "Show Full Gapboard",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TvTextBright,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(
                        items = stocks,
                        key = { "${side.name}_${it.symbol}" },
                    ) { stock ->
                        TradingViewStockRow(
                            stock = stock,
                            side = side,
                            thresholds = thresholds,
                            isNewPriorityPivot = stock.symbol in newlyCrossedPrioritySymbols &&
                                ((isCe && stock.isAboveR2) || (!isCe && stock.isBelowS2)),
                            isInWatchlist = stock.symbol in savedWatchlistSymbols,
                            onClick = { onStockClick(stock) },
                            onToggleWatchlist = { onToggleWatchlistStock(stock) },
                        )
                        HorizontalDivider(thickness = 0.75.dp, color = TvDivider)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortableHeaderCell(
    label: String,
    column: SortColumn,
    activeColumn: SortColumn,
    descending: Boolean,
    alignStart: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isActive = column == activeColumn
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (isActive) TvTextBright else TvTextSecondary,
            maxLines = 1,
        )
        if (isActive) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.cd_sort_column),
                tint = TvAccentBlue,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun TradingViewStockRow(
    stock: StockScanResult,
    side: OptionSide,
    thresholds: ScannerThresholds,
    isNewPriorityPivot: Boolean = false,
    isInWatchlist: Boolean = false,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit = {},
) {
    val isCe = side == OptionSide.CE
    val eval = if (isCe) stock.evaluateCe(thresholds) else stock.evaluatePe(thresholds)
    val trend = stock.overallTrendDirection(thresholds)
    val trendColor = when (trend) {
        TrendDirection.STRONG_BULLISH -> TvTrendBullGreen
        TrendDirection.BULLISH -> TvBullTeal
        TrendDirection.STRONG_BEARISH -> TvTrendBearRed
        TrendDirection.BEARISH -> TvBearCoral
        TrendDirection.NEUTRAL -> TvTrendFlat
    }
    val trendPillBg = when (trend) {
        TrendDirection.STRONG_BULLISH, TrendDirection.BULLISH -> TvTrendBullBg
        TrendDirection.STRONG_BEARISH, TrendDirection.BEARISH -> TvTrendBearBg
        TrendDirection.NEUTRAL -> TvSurfaceElevated
    }
    val trendIcon = when (trend) {
        TrendDirection.STRONG_BULLISH, TrendDirection.BULLISH -> Icons.AutoMirrored.Rounded.TrendingUp
        TrendDirection.STRONG_BEARISH, TrendDirection.BEARISH -> Icons.AutoMirrored.Rounded.TrendingDown
        TrendDirection.NEUTRAL -> Icons.Rounded.TrendingFlat
    }
    val rowAccent = if (stock.changePercent >= 0.0) TvTrendBullGreen else TvTrendBearRed

    var activeFlash by remember(stock.lastUpdatedEpochMs) { mutableStateOf(stock.priceFlashDirection) }
    LaunchedEffect(stock.lastUpdatedEpochMs) {
        if (stock.priceFlashDirection != 0) {
            activeFlash = stock.priceFlashDirection
            delay(650L)
            activeFlash = 0
        }
    }
    val flashColor by animateColorAsState(
        targetValue = when (activeFlash) {
            1 -> TvBullFlash
            -1 -> TvBearFlash
            else -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "price_flash",
    )

    val activeCrosses = stock.activeCrossesFor(side)
    val isHighPriority = activeCrosses.isNotEmpty()
    val isR3Breakout = isCe && stock.isAboveR3
    val isR2Breakout = isCe && !stock.isAboveR3 && stock.isAboveR2
    val isS3Breakdown = !isCe && stock.isBelowS3
    val isS2Breakdown = !isCe && !stock.isBelowS3 && stock.isBelowS2

    val pulseTransition = rememberInfiniteTransition(label = "high_prio_pulse_${stock.symbol}")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.44f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val rowBg = when {
        isHighPriority -> (if (isCe) TvTrendBullGreen else TvTrendBearRed).copy(alpha = pulseAlpha * 0.38f)
        isR3Breakout -> TvTrendBullGreen.copy(alpha = if (isNewPriorityPivot) pulseAlpha * 0.36f else 0.15f)
        isR2Breakout -> TvBullTeal.copy(alpha = if (isNewPriorityPivot) pulseAlpha * 0.32f else 0.10f)
        isS3Breakdown -> TvTrendBearRed.copy(alpha = if (isNewPriorityPivot) pulseAlpha * 0.36f else 0.15f)
        isS2Breakdown -> TvBearCoral.copy(alpha = if (isNewPriorityPivot) pulseAlpha * 0.32f else 0.10f)
        eval.allPassed -> if (isCe) TvBullContainer.copy(alpha = 0.42f) else TvBearContainer.copy(alpha = 0.42f)
        trend == TrendDirection.STRONG_BULLISH -> TvTrendBullBg.copy(alpha = 0.35f)
        trend == TrendDirection.STRONG_BEARISH -> TvTrendBearBg.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val rowModifier = when {
        isHighPriority || isNewPriorityPivot -> Modifier
            .fillMaxWidth()
            .background(rowBg)
            .border(
                width = 1.25.dp,
                color = (if (isCe) TvTrendBullGreen else TvTrendBearRed).copy(alpha = (pulseAlpha + 0.45f).coerceAtMost(1f)),
            )
        isR3Breakout -> Modifier
            .fillMaxWidth()
            .background(rowBg)
            .border(width = 1.dp, color = TvTrendBullGreen.copy(alpha = 0.7f))
        isR2Breakout -> Modifier
            .fillMaxWidth()
            .background(rowBg)
            .border(width = 0.85.dp, color = TvBullTeal.copy(alpha = 0.55f))
        isS3Breakdown -> Modifier
            .fillMaxWidth()
            .background(rowBg)
            .border(width = 1.dp, color = TvTrendBearRed.copy(alpha = 0.7f))
        isS2Breakdown -> Modifier
            .fillMaxWidth()
            .background(rowBg)
            .border(width = 0.85.dp, color = TvBearCoral.copy(alpha = 0.55f))
        else -> Modifier
            .fillMaxWidth()
            .background(rowBg)
    }

    Row(
        modifier = rowModifier
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .testTag("stock_row_${side.name.lowercase()}_${stock.symbol}"),
    ) {
        // Color-coded vertical Trend Indicator Rail (Green = CE/Bullish, Red = PE/Bearish)
        Box(
            modifier = Modifier
                .width(if (trend == TrendDirection.STRONG_BULLISH || trend == TrendDirection.STRONG_BEARISH) 5.dp else 3.5.dp)
                .fillMaxHeight()
                .background(trendColor),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 9.dp, vertical = 9.dp),
        ) {
            // Line 1: Authentic TradingView Watchlist Row (Avatar + Symbol + Trend Pill | Last | Chg | Chg% | Vol)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1.45f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(stock.badgeColorHex))
                            .border(1.25.dp, trendColor.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stock.symbol.take(1),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = onToggleWatchlist,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("watchlist_toggle_${stock.symbol}"),
                            ) {
                                Icon(
                                    imageVector = if (isInWatchlist) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = stringResource(
                                        if (isInWatchlist) R.string.cd_remove_from_watchlist else R.string.cd_add_to_watchlist,
                                    ),
                                    tint = if (isInWatchlist) TvWarningAmber else TvTextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = stock.symbol,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = TvTextBright,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // Compact Green/Red Trend Arrow Pill
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(trendPillBg)
                                    .border(0.6.dp, trendColor.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .testTag("trend_pill_${stock.symbol}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = trendIcon,
                                    contentDescription = trend.shortLabel,
                                    tint = trendColor,
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = trend.shortLabel,
                                    style = TextStyleMiniMono.copy(
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = trendColor,
                                    maxLines = 1,
                                )
                            }
                        }
                        val aligned1m5m = stock.is1mAnd5mAligned(side, thresholds)
                        if (isHighPriority) {
                            val tfs = activeCrosses.joinToString(",") { it.timeframe.label }
                            val latestTime = activeCrosses.maxByOrNull { it.crossedAtEpochMs }?.crossedAtTimeIst ?: ""
                            Text(
                                text = "${if (isCe) "CE HIGH" else "PE HIGH"} • $tfs @ $latestTime",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (isCe) TvTrendBullGreen else TvTrendBearRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (eval.allPassed) {
                            val alignTag = if (thresholds.require1mAlignment && stock.rsi1m != null) " + 1m ALIGN" else ""
                            Text(
                                text = "${if (isCe) "CE 5/5" else "PE 5/5"}$alignTag",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (isCe) TvTrendBullGreen else TvTrendBearRed,
                                maxLines = 1,
                            )
                        } else if (aligned1m5m) {
                            Text(
                                text = "1m+5m ALIGN • ${eval.passedCount}/5",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (isCe) TvBullTeal else TvBearCoral,
                                maxLines = 1,
                            )
                        } else if (eval.passedCount == 5 && !eval.rsi1mPass) {
                            Text(
                                text = "5/5 • Wait 1m RSI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = TvWarningAmber,
                                maxLines = 1,
                            )
                        } else {
                            Text(
                                text = "${eval.passedCount}/5 rules",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TvTextMuted,
                            )
                        }
                    }
                }

                // Last Price with tick flash
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(flashColor)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = TechnicalIndicators.formatPrice(stock.lastPrice),
                        style = TabularNumberStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TvTextBright,
                        maxLines = 1,
                    )
                }

                // Chg
                Text(
                    text = TechnicalIndicators.formatChange(stock.change),
                    style = TabularNumberStyle.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = rowAccent,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(0.75f),
                )

                // Color-coded Chg% pill for instant scan contrast
                Box(
                    modifier = Modifier.weight(0.85f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (stock.changePercent >= 0.0) TvTrendBullBg else TvTrendBearBg)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = TechnicalIndicators.formatPercent(stock.changePercent),
                            style = TabularNumberStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = rowAccent,
                            maxLines = 1,
                        )
                    }
                }

                // Vol (Shares)
                Text(
                    text = TechnicalIndicators.formatVolumeCompact(stock.sharesVolume),
                    style = TabularNumberStyle.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (eval.sharesPass) TvTextBright else TvTextSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(0.85f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: 4-TF Color Trend Alignment Dots + Multi-TF RSI (5m, 15m, 30m, 1h) & RVOL Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 33.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrendAlignmentDots(stock = stock)

                val crossTfs = activeCrosses.map { it.timeframe }.toSet()
                MetricMiniBadge(
                    label = "RVOL ${String.format(java.util.Locale.US, "%.2f", stock.rvol)}x",
                    passed = eval.rvolPass,
                    isCe = isCe,
                )
                MetricMiniBadge(
                    label = "1h ${formatRsi(stock.rsi1h)}",
                    passed = eval.rsi1hPass || ChartTimeframe.H1 in crossTfs,
                    isCe = isCe,
                    isCrossedExtreme = ChartTimeframe.H1 in crossTfs,
                )
                MetricMiniBadge(
                    label = "30m ${formatRsi(stock.rsi30m)}",
                    passed = ChartTimeframe.M30 in crossTfs ||
                        (if (isCe) (stock.rsi30m ?: 0.0) > 60.0 else (stock.rsi30m ?: 100.0) < 40.0),
                    isCe = isCe,
                    isCrossedExtreme = ChartTimeframe.M30 in crossTfs,
                )
                MetricMiniBadge(
                    label = "15m ${formatRsi(stock.rsi15m)}",
                    passed = eval.rsi15mPass || ChartTimeframe.M15 in crossTfs,
                    isCe = isCe,
                    isCrossedExtreme = ChartTimeframe.M15 in crossTfs,
                )
                MetricMiniBadge(
                    label = "5m ${formatRsi(stock.rsi5m)}",
                    passed = eval.rsi5mPass || ChartTimeframe.M5 in crossTfs,
                    isCe = isCe,
                    isCrossedExtreme = ChartTimeframe.M5 in crossTfs,
                )
                MetricMiniBadge(
                    label = "1m ${formatRsi(stock.rsi1m)}",
                    passed = (stock.rsi1m != null && stock.is1mAligned(side, thresholds)) || ChartTimeframe.M1 in crossTfs,
                    isCe = isCe,
                    isCrossedExtreme = ChartTimeframe.M1 in crossTfs,
                )
            }

        Spacer(modifier = Modifier.height(5.dp))

        // Line 2.5: ATM Option Strike Price, Live Running Price (LTP + Chg%), & R1-R3/S1-S3 Option Targets
        val atmQuote = stock.atmOptionFor(side)
        val atmAccent = if (isCe) TvBullTeal else TvBearCoral
        val rowPivots = stock.pivotLevels
        val pivotTargetsLine = remember(
            stock.symbol,
            stock.lastPrice,
            rowPivots,
            atmQuote?.strikePrice,
            atmQuote?.runningPrice,
            stock.atmOptions,
            side,
        ) {
            if (atmQuote != null && atmQuote.runningPrice > 0.0 && rowPivots != null) {
                val chain = if (isCe) {
                    stock.atmOptions.allStrikesCe.ifEmpty { stock.atmOptions.nearbyStrikesCe }
                } else {
                    stock.atmOptions.allStrikesPe.ifEmpty { stock.atmOptions.nearbyStrikesPe }
                }
                val targets = if (isCe) {
                    listOf("R1" to rowPivots.r1, "R2" to rowPivots.r2, "R3" to rowPivots.r3)
                } else {
                    listOf("S1" to rowPivots.s1, "S2" to rowPivots.s2, "S3" to rowPivots.s3)
                }
                targets.joinToString(" • ") { (lvl, lvlPrice) ->
                    val est = OptionPivotPricing.estimateAtmOptionPriceAtSpot(
                        spotPrice = stock.lastPrice,
                        targetSpotPrice = lvlPrice,
                        atmQuote = atmQuote,
                        chainQuotes = chain,
                        side = side,
                    )
                    if (est != null) "$lvl ₹${TechnicalIndicators.formatPrice(est)}" else "$lvl --"
                }
            } else {
                null
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 33.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(atmAccent.copy(alpha = 0.08f))
                .border(0.6.dp, atmAccent.copy(alpha = 0.30f), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ATM ${stock.formattedComputedAtmStrike(side)}",
                    style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold),
                    color = TvTextBright,
                    maxLines = 1,
                )
                if (atmQuote != null && atmQuote.runningPrice > 0.0) {
                    val optChgColor = if (atmQuote.changePercent >= 0.0) TvBullTeal else TvBearCoral
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "₹${TechnicalIndicators.formatPrice(atmQuote.runningPrice)}",
                            style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = TvTextBright,
                        )
                        Text(
                            text = TechnicalIndicators.formatPercent(atmQuote.changePercent),
                            style = TextStyleMiniMono.copy(fontWeight = FontWeight.SemiBold),
                            color = optChgColor,
                        )
                        if (atmQuote.expiryDate.isNotBlank()) {
                            Text(
                                text = atmQuote.expiryDate.take(6),
                                style = TextStyleMiniMono.copy(fontSize = 8.5.sp),
                                color = TvTextSecondary,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "LTP --",
                        style = TextStyleMiniMono,
                        color = TvTextSecondary,
                    )
                }
            }
            if (pivotTargetsLine != null) {
                Text(
                    text = "${side.name} Opt @ $pivotTargetsLine",
                    style = TextStyleMiniMono.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                    color = atmAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Line 2.75: Standard Pivot Zone (R1/R2/R3 & S1/S2/S3, highlighting R2/R3 & S2/S3) & F&O PCR OI Change %
        val pivots = stock.pivotLevels
        val oiSummary = stock.oiDelivery
        val newTag = if (isNewPriorityPivot) " • NEW" else ""
        val pivotBadgeText = when {
            isR3Breakout && pivots != null -> {
                val pctStr = stock.percentAboveR3()?.let { "+${String.format(java.util.Locale.US, "%.2f%%", it)} " } ?: ""
                "⚡ >R3 ${pctStr}(₹${TechnicalIndicators.formatPrice(pivots.r3)})$newTag"
            }
            isR2Breakout && pivots != null -> {
                val pctStr = stock.percentAboveR2()?.let { "+${String.format(java.util.Locale.US, "%.2f%%", it)} " } ?: ""
                "⚡ >R2 ${pctStr}→ R3 ₹${TechnicalIndicators.formatPrice(pivots.r3)}$newTag"
            }
            isS3Breakdown && pivots != null -> {
                val pctStr = stock.percentBelowS3()?.let { "-${String.format(java.util.Locale.US, "%.2f%%", it)} " } ?: ""
                "⚡ <S3 ${pctStr}(₹${TechnicalIndicators.formatPrice(pivots.s3)})$newTag"
            }
            isS2Breakdown && pivots != null -> {
                val pctStr = stock.percentBelowS2()?.let { "-${String.format(java.util.Locale.US, "%.2f%%", it)} " } ?: ""
                "⚡ <S2 ${pctStr}→ S3 ₹${TechnicalIndicators.formatPrice(pivots.s3)}$newTag"
            }
            pivots != null -> TechnicalIndicators.formatPivotZone(pivots, stock.lastPrice, side)
            else -> "Pivots --"
        }
        val pivotTextColor = when {
            isR3Breakout -> TvTrendBullGreen
            isR2Breakout -> TvBullTeal
            isS3Breakdown -> TvTrendBearRed
            isS2Breakdown -> TvBearCoral
            pivots != null && isCe && stock.lastPrice >= pivots.r1 -> TvBullEmerald
            pivots != null && !isCe && stock.lastPrice <= pivots.s1 -> TvBearCoral
            else -> TvTextSecondary
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 33.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isR3Breakout || isR2Breakout -> TvTrendBullBg
                            isS3Breakdown || isS2Breakdown -> TvTrendBearBg
                            else -> TvBackground.copy(alpha = 0.6f)
                        }
                    )
                    .border(
                        width = if (isR3Breakout || isR2Breakout || isS3Breakdown || isS2Breakdown) 0.75.dp else 0.5.dp,
                        color = when {
                            isR3Breakout -> TvTrendBullGreen
                            isR2Breakout -> TvBullTeal
                            isS3Breakdown -> TvTrendBearRed
                            isS2Breakdown -> TvBearCoral
                            else -> TvDivider
                        },
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = pivotBadgeText,
                    style = TextStyleMiniMono.copy(
                        fontWeight = if (isR3Breakout || isR2Breakout || isS3Breakdown || isS2Breakdown) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 8.5.sp,
                    ),
                    color = pivotTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val pcrText = if (oiSummary.pcr != null) {
                val mainRatio = if (isCe) {
                    "PCR ${String.format(java.util.Locale.US, "%.2f", oiSummary.pcr)}"
                } else {
                    val cp = oiSummary.callToPutRatio ?: (if (oiSummary.pcr > 0.0) 1.0 / oiSummary.pcr else 0.0)
                    "C/P ${String.format(java.util.Locale.US, "%.2f", cp)}"
                }
                val chgStr = oiSummary.pcrOiChangePct?.let {
                    val s = if (it >= 0.0) "+" else ""
                    " ($s${String.format(java.util.Locale.US, "%.1f%%", it)})"
                } ?: ""
                mainRatio + chgStr
            } else {
                "PCR --"
            }
            val pcrColor = when {
                (oiSummary.pcrOiChangePct ?: 0.0) > 0.0 -> TvTrendBullGreen
                (oiSummary.pcrOiChangePct ?: 0.0) < 0.0 -> TvTrendBearRed
                oiSummary.pcr != null && oiSummary.pcr >= 1.15 -> TvBullTeal
                oiSummary.pcr != null && oiSummary.pcr <= 0.75 -> TvBearCoral
                else -> TvTextSecondary
            }
            Text(
                text = pcrText,
                style = TextStyleMiniMono.copy(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold),
                color = pcrColor,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Line 3: OI & OI Change vs Yest + Delivery & Delivery Change vs Yest + Hold/Sell Verdict
        val oiDeliv = stock.oiDelivery
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 33.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                val oiText = if (oiDeliv.openInterest != null) {
                    val chgStr = oiDeliv.oiChangePct?.let {
                        val s = if (it >= 0) "+" else ""
                        " ($s${String.format(java.util.Locale.US, "%.1f%%", it)})"
                    } ?: ""
                    "OI ${TechnicalIndicators.formatVolumeCompact(oiDeliv.openInterest)}$chgStr"
                } else {
                    "OI --"
                }
                val oiColor = when {
                    (oiDeliv.oiChange ?: 0L) > 0L -> TvBullTeal
                    (oiDeliv.oiChange ?: 0L) < 0L -> TvBearCoral
                    else -> TvTextSecondary
                }
                Text(
                    text = oiText,
                    style = TextStyleMiniMono,
                    color = oiColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val delivText = if (oiDeliv.deliveryPct != null) {
                    val ppStr = oiDeliv.deliveryPctChangePp?.let {
                        val s = if (it >= 0) "+" else ""
                        " ($s${String.format(java.util.Locale.US, "%.1f", it)})"
                    } ?: ""
                    "Deliv ${String.format(java.util.Locale.US, "%.1f%%", oiDeliv.deliveryPct)}$ppStr"
                } else {
                    "Deliv --"
                }
                val delivColor = when {
                    (oiDeliv.deliveryPctChangePp ?: 0.0) > 0.0 -> TvBullTeal
                    (oiDeliv.deliveryPctChangePp ?: 0.0) < 0.0 -> TvBearCoral
                    else -> TvTextSecondary
                }
                Text(
                    text = delivText,
                    style = TextStyleMiniMono,
                    color = delivColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PositionActionPill(action = oiDeliv.positionAction)
        }
        }
    }
}

@Composable
private fun TrendAlignmentDots(stock: StockScanResult) {
    val rsiList = listOf(stock.rsi1h, stock.rsi30m, stock.rsi15m, stock.rsi5m, stock.rsi1m)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TvBackground.copy(alpha = 0.75f))
            .border(0.75.dp, TvDivider, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rsiList.forEach { rsi ->
            val dotColor = when {
                rsi == null -> TvTrendFlat
                rsi >= 60.0 -> TvTrendBullGreen
                rsi > 52.0 -> TvBullTeal.copy(alpha = 0.8f)
                rsi <= 40.0 -> TvTrendBearRed
                rsi < 48.0 -> TvBearCoral.copy(alpha = 0.8f)
                else -> TvTrendFlat
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

@Composable
fun PositionActionPill(
    action: com.example.domain.model.PositionAction,
) {
    val (bg, fg) = when (action) {
        com.example.domain.model.PositionAction.STRONG_HOLD -> TvBullContainer to TvBullTeal
        com.example.domain.model.PositionAction.HOLD -> TvBullContainer.copy(alpha = 0.7f) to TvBullEmerald
        com.example.domain.model.PositionAction.TRAIL_PARTIAL -> TvAmberContainer to TvWarningAmber
        com.example.domain.model.PositionAction.SELL_EXIT -> TvBearContainer to TvBearCoral
        com.example.domain.model.PositionAction.WATCH -> TvSurfaceElevated to TvTextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(0.75.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = action.badgeLabel,
            style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun MetricMiniBadge(
    label: String,
    passed: Boolean,
    isCe: Boolean,
    isCrossedExtreme: Boolean = false,
) {
    val activeColor = if (isCe) TvBullTeal else TvBearCoral
    val bgColor = when {
        isCrossedExtreme -> activeColor.copy(alpha = 0.28f)
        passed -> activeColor.copy(alpha = 0.14f)
        else -> TvBackground.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isCrossedExtreme -> activeColor
        passed -> activeColor.copy(alpha = 0.55f)
        else -> TvDivider
    }
    val textColor = if (passed || isCrossedExtreme) activeColor else TvTextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(if (isCrossedExtreme) 1.dp else 0.75.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (isCrossedExtreme) "⚡$label" else label,
            style = if (isCrossedExtreme) TextStyleMiniMono.copy(fontWeight = FontWeight.Bold) else TextStyleMiniMono,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun BottomCrossingWatchBar(
    alerts: List<RsiCrossAlert>,
    stocks: List<StockScanResult> = emptyList(),
    soundAlertsEnabled: Boolean = true,
    lastSoundAlertBanner: String? = null,
    onToggleSoundAlerts: () -> Unit = {},
    onAlertClick: (RsiCrossAlert) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val activeCe = alerts.count { it.isActiveExtreme && it.side == OptionSide.CE }
    val activePe = alerts.count { it.isActiveExtreme && it.side == OptionSide.PE }

    val pulseTransition = rememberInfiniteTransition(label = "watch_dot")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_alpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_crossing_watch_bar"),
        color = TvSurface,
        border = BorderStroke(1.dp, TvDivider),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = if (alerts.isNotEmpty()) TvWarningAmber.copy(alpha = dotAlpha) else TvTextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "CROSSING JUST NOW (1m/5m/15m/30m/1h)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = TvTextBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!lastSoundAlertBanner.isNullOrBlank()) {
                            Text(
                                text = lastSoundAlertBanner,
                                style = TextStyleMiniMono.copy(fontSize = 8.5.sp),
                                color = if (soundAlertsEnabled) TvWarningAmber else TvTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = TvBullContainer,
                    ) {
                        Text(
                            text = "CE>70: $activeCe",
                            style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold),
                            color = TvTrendBullGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = TvBearContainer,
                    ) {
                        Text(
                            text = "PE<30: $activePe",
                            style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold),
                            color = TvTrendBearRed,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    IconButton(
                        onClick = onToggleSoundAlerts,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_sound_button"),
                    ) {
                        Icon(
                            imageVector = if (soundAlertsEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                            contentDescription = stringResource(R.string.cd_toggle_sound),
                            tint = if (soundAlertsEnabled) TvTrendBullGreen else TvTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Toggle crossing watch bar",
                        tint = TvTextSecondary,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                if (alerts.isEmpty()) {
                    Text(
                        text = if (soundAlertsEnabled) {
                            "Sound alerts ON • Monitoring 1m, 5m, 15m, 30m & 1h RSI for >70 (CE) or <30 (PE) crossings every 10s..."
                        } else {
                            "Sound muted • Monitoring 1m, 5m, 15m, 30m & 1h RSI for >70 (CE) or <30 (PE) crossings every 10s..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    )
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = alerts,
                            key = { it.id },
                        ) { alert ->
                            val isCe = alert.side == OptionSide.CE
                            val accent = if (isCe) TvBullTeal else TvBearCoral
                            val bg = if (isCe) TvBullContainer else TvBearContainer

                            Surface(
                                onClick = { onAlertClick(alert) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (alert.isActiveExtreme) bg else TvBackground,
                                border = BorderStroke(
                                    width = if (alert.isActiveExtreme) 1.dp else 0.75.dp,
                                    color = if (alert.isActiveExtreme) accent else TvDivider,
                                ),
                                modifier = Modifier.testTag("cross_alert_${alert.id}"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = if (isCe) "CE >70" else "PE <30",
                                            style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold),
                                            color = accent,
                                        )
                                        Text(
                                            text = alert.symbol,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = TvTextBright,
                                        )
                                        Text(
                                            text = alert.crossedAtTimeIst,
                                            style = TextStyleMiniMono,
                                            color = TvWarningAmber,
                                        )
                                    }
                                    val matchedStock = stocks.firstOrNull { it.symbol == alert.symbol }
                                    val atmQuote = matchedStock?.atmOptionFor(alert.side)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "${alert.timeframe.label} RSI ${String.format(java.util.Locale.US, "%.1f", alert.rsiValue)}",
                                            style = TextStyleMiniMono,
                                            color = accent,
                                        )
                                        if (atmQuote != null && atmQuote.runningPrice > 0.0) {
                                            Text(
                                                text = "${atmQuote.contractLabel} ₹${TechnicalIndicators.formatPrice(atmQuote.runningPrice)}",
                                                style = TextStyleMiniMono.copy(fontWeight = FontWeight.Bold),
                                                color = TvTextBright,
                                            )
                                        } else {
                                            Text(
                                                text = TechnicalIndicators.formatPercent(alert.changePercent),
                                                style = TextStyleMiniMono,
                                                color = TvTextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TextStyleMiniMono = androidx.compose.ui.text.TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 9.5.sp,
    fontFeatureSettings = "tnum",
)

private fun formatRsi(value: Double?): String {
    return if (value == null || value.isNaN()) "--" else String.format(java.util.Locale.US, "%.1f", value)
}
