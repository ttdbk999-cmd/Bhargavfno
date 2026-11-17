package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.indicator.PositionalCandidate
import com.example.domain.indicator.PositionalEma20Summary
import com.example.domain.indicator.PositionalFilter
import com.example.domain.indicator.PositionalRsiSummary
import com.example.domain.indicator.PositionalStage
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.DailyPositionSnapshot
import com.example.domain.model.FnoBuildupQuadrant
import com.example.domain.model.LiveScannerAlert
import com.example.domain.model.OptionSide
import com.example.domain.model.StockScanResult
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAccentBlueContainer
import com.example.ui.theme.TvAmberContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearContainer
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvBullContainer
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextBright
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.theme.TvWarningAmber
import java.util.Locale

@Composable
fun PositionalPageContent(
    candidates: List<PositionalCandidate>,
    allCandidateCount: Int,
    earlyIgnitionCount: Int,
    confirmedHoldCount: Int,
    nseSyncedCount: Int,
    fiveDayBackedUpCount: Int,
    rsiEntryReadyCount: Int,
    ema20CrossCount: Int,
    ema20CrossOrAlignedCount: Int,
    ema20CeAboveCount: Int,
    ema20PeBelowCount: Int,
    volumeSurgeCount: Int,
    positionalVolRsiBuzzCount: Int = 0,
    positionalBuzzAlerts: List<LiveScannerAlert> = emptyList(),
    activeFilter: PositionalFilter,
    searchQuery: String,
    savedWatchlistSymbols: Set<String>,
    isLoadingInitial: Boolean,
    isSyncingBatch: Boolean,
    isLiveRefreshingPositional: Boolean = false,
    refreshIntervalSec: Int = 10,
    secondsUntilRefresh: Int = 10,
    autoRefreshEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onBackToGapboard: () -> Unit,
    onSelectFilter: (PositionalFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onSyncPositionalNse: () -> Unit,
    onCandidateClick: (PositionalCandidate) -> Unit,
    onSelectBuzzAlert: (LiveScannerAlert) -> Unit = {},
    onToggleWatchlist: (StockScanResult, OptionSide) -> Unit,
) {
    BackHandler {
        onBackToGapboard()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        PositionalSummaryAndControls(
            earlyIgnitionCount = earlyIgnitionCount,
            confirmedHoldCount = confirmedHoldCount,
            nseSyncedCount = nseSyncedCount,
            fiveDayBackedUpCount = fiveDayBackedUpCount,
            rsiEntryReadyCount = rsiEntryReadyCount,
            ema20CrossCount = ema20CrossCount,
            ema20CrossOrAlignedCount = ema20CrossOrAlignedCount,
            ema20CeAboveCount = ema20CeAboveCount,
            ema20PeBelowCount = ema20PeBelowCount,
            volumeSurgeCount = volumeSurgeCount,
            positionalVolRsiBuzzCount = positionalVolRsiBuzzCount,
            totalSetups = allCandidateCount,
            activeFilter = activeFilter,
            searchQuery = searchQuery,
            savedWatchlistCount = savedWatchlistSymbols.size,
            isSyncingBatch = isSyncingBatch,
            isLiveRefreshingPositional = isLiveRefreshingPositional,
            refreshIntervalSec = refreshIntervalSec,
            secondsUntilRefresh = secondsUntilRefresh,
            autoRefreshEnabled = autoRefreshEnabled,
            onSelectFilter = onSelectFilter,
            onSearchChange = onSearchChange,
            onSyncPositionalNse = onSyncPositionalNse,
        )

        if (positionalBuzzAlerts.isNotEmpty()) {
            PositionalVolRsiBuzzStrip(
                buzzAlerts = positionalBuzzAlerts,
                onFilterBuzz = { onSelectFilter(PositionalFilter.VOL2X_RSI65) },
                onSelectAlert = onSelectBuzzAlert,
            )
        }

        when {
            isLoadingInitial && candidates.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TvAccentBlue, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Analyzing F&O Positional & Delivery Setups...",
                            style = MaterialTheme.typography.titleSmall,
                            color = TvTextBright,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Evaluating pivots (PP→R2 / PP→S2), 1h/15m RSI, OI & 5-day delivery",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextSecondary,
                        )
                    }
                }
            }

            candidates.isEmpty() -> {
                PositionalEmptyState(
                    activeFilter = activeFilter,
                    searchQuery = searchQuery,
                    onResetFilters = {
                        onSelectFilter(PositionalFilter.ALL)
                        onSearchChange("")
                    },
                    onSyncPositionalNse = onSyncPositionalNse,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("positional_list"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = candidates,
                        key = { "${it.stock.symbol}_${it.bias.name}" },
                    ) { candidate ->
                        PositionalCandidateCard(
                            candidate = candidate,
                            isWatchlisted = candidate.stock.symbol in savedWatchlistSymbols,
                            onClick = { onCandidateClick(candidate) },
                            onToggleWatchlist = {
                                onToggleWatchlist(candidate.stock, candidate.bias)
                            },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionalSummaryAndControls(
    earlyIgnitionCount: Int,
    confirmedHoldCount: Int,
    nseSyncedCount: Int,
    fiveDayBackedUpCount: Int,
    rsiEntryReadyCount: Int,
    ema20CrossCount: Int,
    ema20CrossOrAlignedCount: Int,
    ema20CeAboveCount: Int,
    ema20PeBelowCount: Int,
    volumeSurgeCount: Int,
    positionalVolRsiBuzzCount: Int,
    totalSetups: Int,
    activeFilter: PositionalFilter,
    searchQuery: String,
    savedWatchlistCount: Int,
    isSyncingBatch: Boolean,
    isLiveRefreshingPositional: Boolean,
    refreshIntervalSec: Int,
    secondsUntilRefresh: Int,
    autoRefreshEnabled: Boolean,
    onSelectFilter: (PositionalFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onSyncPositionalNse: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TvSurface,
        border = BorderStroke(0.75.dp, TvDivider),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(TvAccentBlueContainer, TvBullContainer),
                                )
                            )
                            .border(1.dp, TvBorderSubtle, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = TvAccentBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Positional & Delivery Desk",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TvTextBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (autoRefreshEnabled) TvBullContainer else TvSurfaceElevated,
                                border = BorderStroke(
                                    0.75.dp,
                                    if (isLiveRefreshingPositional) TvAccentBlue else if (autoRefreshEnabled) TvBullTeal.copy(alpha = 0.55f) else TvBorderSubtle,
                                ),
                                modifier = Modifier.testTag("positional_live_cadence_badge"),
                            ) {
                                Text(
                                    text = when {
                                        isLiveRefreshingPositional -> "LIVE ${refreshIntervalSec}s • OI↻"
                                        autoRefreshEnabled -> "LIVE ${refreshIntervalSec}s • ${secondsUntilRefresh}s"
                                        else -> "PAUSED"
                                    },
                                    style = PositionalMiniMono.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (isLiveRefreshingPositional) TvAccentBlue else if (autoRefreshEnabled) TvBullTeal else TvTextMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = "$earlyIgnitionCount Early • $volumeSurgeCount Vol≥2x • $ema20CrossCount Cross ⚡ • $fiveDayBackedUpCount 5D",
                                style = PositionalMiniMono,
                                color = TvTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onSyncPositionalNse,
                    enabled = !isSyncingBatch,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, TvAccentBlue.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sync_positional_nse_button"),
                ) {
                    if (isSyncingBatch) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = TvAccentBlue,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Syncing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvTextBright,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = stringResource(R.string.cd_sync_positional_nse),
                            tint = TvAccentBlue,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sync 5D Backup",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TvTextBright,
                        )
                    }
                }
            }

            // Compact Search Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvBackground)
                    .border(1.dp, TvBorderSubtle, RoundedCornerShape(8.dp))
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
                            text = "Filter symbol for positional OI & 5D delivery...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TvTextBright),
                        cursorBrush = SolidColor(TvAccentBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("positional_search_input"),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = TvTextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // Stage & Bias Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PositionalFilter.entries.forEach { filter ->
                    val selected = activeFilter == filter
                    val countSuffix = when (filter) {
                        PositionalFilter.ALL -> if (totalSetups > 0) " ($totalSetups)" else ""
                        PositionalFilter.VOL2X_RSI65 -> " ($positionalVolRsiBuzzCount)"
                        PositionalFilter.VOLUME_SURGE -> " ($volumeSurgeCount)"
                        PositionalFilter.EMA20_CROSS -> " ($ema20CrossCount)"
                        PositionalFilter.EMA20_CROSS_OR_ALIGNED -> " ($ema20CrossOrAlignedCount)"
                        PositionalFilter.EMA20_CE_ABOVE -> " ($ema20CeAboveCount)"
                        PositionalFilter.EMA20_PE_BELOW -> " ($ema20PeBelowCount)"
                        PositionalFilter.RSI_ENTRY -> " ($rsiEntryReadyCount)"
                        PositionalFilter.EARLY_IGNITION -> " ($earlyIgnitionCount)"
                        PositionalFilter.CONFIRMED_HOLD -> " ($confirmedHoldCount)"
                        PositionalFilter.WATCHLIST -> " ($savedWatchlistCount)"
                        else -> ""
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                text = "${filter.label}$countSuffix",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TvAccentBlueContainer,
                            selectedLabelColor = TvTextBright,
                            containerColor = TvBackground,
                            labelColor = TvTextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = TvBorderSubtle,
                            selectedBorderColor = TvAccentBlue,
                        ),
                        modifier = Modifier.testTag("positional_filter_${filter.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionalCandidateCard(
    candidate: PositionalCandidate,
    isWatchlisted: Boolean,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val stock = candidate.stock
    val isCe = candidate.bias == OptionSide.CE
    val biasColor = if (isCe) TvBullTeal else TvBearCoral
    val biasContainer = if (isCe) TvBullContainer else TvBearContainer
    val (stageColor, stageBg) = when (candidate.stage) {
        PositionalStage.EARLY_IGNITION -> TvBullTeal to TvBullContainer
        PositionalStage.CONFIRMED_HOLD -> TvAccentBlue to TvAccentBlueContainer
        PositionalStage.EXTENDED_TRAIL -> TvWarningAmber to TvAmberContainer
    }
    val chgColor = if (stock.changePercent >= 0.0) TvBullTeal else TvBearCoral

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = TvSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = when {
                candidate.hasVol2xAndRsi65 -> 1.5.dp
                candidate.stage == PositionalStage.EARLY_IGNITION -> 1.25.dp
                else -> 0.75.dp
            },
            color = when {
                candidate.hasVol2xAndRsi65 -> TvWarningAmber.copy(alpha = 0.85f)
                candidate.stage == PositionalStage.EARLY_IGNITION -> biasColor.copy(alpha = 0.65f)
                else -> TvDivider
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("positional_card_${stock.symbol}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (candidate.hasVol2xAndRsi65) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = TvAmberContainer.copy(alpha = 0.85f),
                    border = BorderStroke(0.75.dp, TvWarningAmber.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("positional_buzz_badge_${stock.symbol}"),
                ) {
                    val ratioTxt = stock.volumeSurgeRatio?.let { String.format(Locale.US, "%.1fx", it) } ?: "2x+"
                    Text(
                        text = "🔊 POSITIONAL BUZZ • VOL $ratioTxt (>2x 20D) + ${candidate.rsiAbove65Count}/5 RSI >65",
                        style = PositionalMiniMono.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = TvWarningAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            // Row 1: Symbol + Stage Badge + Conviction + Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(stock.badgeColorHex).copy(alpha = 0.22f))
                            .border(1.dp, Color(stock.badgeColorHex).copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stock.symbol.take(2),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TvTextBright,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = stock.symbol,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TvTextBright,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = biasContainer,
                                border = BorderStroke(0.75.dp, biasColor.copy(alpha = 0.6f)),
                            ) {
                                Text(
                                    text = if (isCe) "CE LONG" else "PE SHORT",
                                    style = PositionalMiniMono.copy(fontWeight = FontWeight.Bold),
                                    color = biasColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = stageBg,
                            ) {
                                Text(
                                    text = candidate.stage.badgeLabel,
                                    style = PositionalMiniMono.copy(fontWeight = FontWeight.Bold),
                                    color = stageColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "${stock.sector} • Conviction ${candidate.convictionScore}/100",
                            style = PositionalMiniMono,
                            color = TvTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹${TechnicalIndicators.formatPrice(stock.lastPrice)}",
                            style = TabularNumberStyle.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = TvTextBright,
                        )
                        Text(
                            text = TechnicalIndicators.formatPercent(stock.changePercent),
                            style = PositionalMiniMono.copy(fontWeight = FontWeight.Bold),
                            color = chgColor,
                        )
                    }
                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("positional_star_${stock.symbol}"),
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(
                                if (isWatchlisted) R.string.cd_remove_from_watchlist else R.string.cd_add_to_watchlist,
                            ),
                            tint = if (isWatchlisted) TvWarningAmber else TvTextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Row 2: Institutional Headline & OI Quadrant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isCe) {
                            Icons.AutoMirrored.Rounded.TrendingUp
                        } else {
                            Icons.AutoMirrored.Rounded.TrendingDown
                        },
                        contentDescription = null,
                        tint = biasColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stock.oiDelivery.positionHeadline,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TvTextBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                QuadrantBadge(quadrant = stock.oiDelivery.buildupQuadrant)
            }

            // Row 3: 3-Column Metrics (Futures OI | Cash Delivery | PCR & HTF RSI)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PositionalMetricBox(
                    label = "Futures OI",
                    value = stock.oiDelivery.openInterest?.let {
                        val chg = stock.oiDelivery.oiChangePct?.let { pct ->
                            String.format(Locale.US, " %+.1f%%", pct)
                        }.orEmpty()
                        "${TechnicalIndicators.formatVolumeCompact(it)}$chg"
                    } ?: "--",
                    valueColor = when {
                        (stock.oiDelivery.oiChangePct ?: 0.0) > 0.0 -> TvBullTeal
                        (stock.oiDelivery.oiChangePct ?: 0.0) < 0.0 -> TvBearCoral
                        else -> TvTextSecondary
                    },
                    modifier = Modifier.weight(1f),
                )
                PositionalMetricBox(
                    label = "Delivery %",
                    value = stock.oiDelivery.deliveryPct?.let { pct ->
                        val pp = stock.oiDelivery.deliveryPctChangePp?.let { d ->
                            String.format(Locale.US, " (%+.1fpp)", d)
                        }.orEmpty()
                        "${String.format(Locale.US, "%.1f%%", pct)}$pp"
                    } ?: "--",
                    valueColor = if (candidate.deliveryRising) TvBullTeal else TvTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                val volRatioText = when {
                    stock.volumeSurgeRatio != null -> {
                        val base = String.format(Locale.US, "%.1fx", stock.volumeSurgeRatio)
                        if (stock.isVolumeSurge2x) {
                            "$base SURGE⚡"
                        } else if (stock.isEarlyVolumePace2x && stock.volumePaceRatio != null) {
                            "$base (P:${String.format(Locale.US, "%.1fx", stock.volumePaceRatio)})"
                        } else {
                            base
                        }
                    }
                    else -> "${String.format(Locale.US, "%.1fx", stock.rvol)} RVOL"
                }
                PositionalMetricBox(
                    label = if (stock.volumeSurgeRatio != null) "20D Vol" else "RVOL",
                    value = volRatioText,
                    valueColor = when {
                        stock.isVolumeSurge2x -> TvBullTeal
                        stock.isEarlyVolumePace2x -> TvWarningAmber
                        else -> TvTextBright
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Row 3B: Positional Entry RSI Matrix (1h, 30m, 15m, 5m, 1m) targeting >65 / <35
            PositionalEntryRsiStrip(
                rsiSummary = candidate.rsiEntrySummary,
                bias = candidate.bias,
                symbol = stock.symbol,
            )

            // Row 3C: Multi-TF EMA 20 Position & Fresh Crossover Strip
            PositionalEma20Strip(
                emaSummary = candidate.ema20Summary,
                bias = candidate.bias,
                symbol = stock.symbol,
            )

            // Row 4: Pivot Entry Zone + Target Spot + Projected ATM Option Price
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TvBackground,
                border = BorderStroke(0.75.dp, TvBorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zone: ${candidate.pivotZoneLabel} • ${candidate.nextTargetLabel}",
                            style = PositionalMiniMono.copy(fontWeight = FontWeight.Bold),
                            color = biasColor,
                        )
                        val targetSpot = candidate.nextTargetSpotPrice
                        val atmOption = stock.atmOptionFor(candidate.bias)
                        val targetDetail = buildString {
                            if (targetSpot != null) {
                                append("Spot Target ₹${TechnicalIndicators.formatPrice(targetSpot)}")
                            } else {
                                append("RVOL ${String.format(Locale.US, "%.1fx", stock.rvol)}")
                            }
                            if (atmOption != null && atmOption.runningPrice > 0.0) {
                                append(" • ${atmOption.contractLabel} ₹${TechnicalIndicators.formatPrice(atmOption.runningPrice)}")
                            }
                        }
                        Text(
                            text = targetDetail,
                            style = PositionalMiniMono,
                            color = TvTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    candidate.nextTargetAtmOptionPrice?.let { estOpt ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "OPT TARGET",
                                style = PositionalMiniMono,
                                color = TvTextSecondary,
                            )
                            Text(
                                text = "₹${TechnicalIndicators.formatPrice(estOpt)}",
                                style = TabularNumberStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = TvBullTeal,
                            )
                        }
                    }
                }
            }

            // Row 5: 5-Day Delivery Change & OI Change % Local Backup Table
            if (stock.oiDelivery.history5Days.isNotEmpty()) {
                FiveDayDeliveryAndOiBackupTable(
                    snapshotsDesc = stock.oiDelivery.history5Days.take(5),
                    backedUpDaysCount = candidate.backedUpDaysCount,
                    hasFiveDayBackup = candidate.hasFiveDayBackup,
                    avg5dDeliv = candidate.deliveryAvg5dPct,
                )
            }

            // Warning or Confirmation Reasons & Trend Hold Rule Footer
            if (candidate.trendHoldRule.isNotBlank()) {
                Text(
                    text = candidate.trendHoldRule,
                    style = PositionalMiniMono.copy(fontSize = 8.5.sp),
                    color = if (candidate.stage == PositionalStage.EXTENDED_TRAIL) TvWarningAmber else TvAccentBlue,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (candidate.warningNote != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvAmberContainer.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = TvWarningAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = candidate.warningNote,
                        style = PositionalMiniMono,
                        color = TvWarningAmber,
                    )
                }
            } else if (candidate.reasons.isNotEmpty()) {
                Text(
                    text = candidate.reasons.joinToString("  •  "),
                    style = PositionalMiniMono,
                    color = TvTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuadrantBadge(quadrant: FnoBuildupQuadrant) {
    val (color, bg) = when (quadrant) {
        FnoBuildupQuadrant.LONG_BUILDUP -> TvBullTeal to TvBullContainer
        FnoBuildupQuadrant.SHORT_BUILDUP -> TvBearCoral to TvBearContainer
        FnoBuildupQuadrant.SHORT_COVERING -> TvWarningAmber to TvAmberContainer
        FnoBuildupQuadrant.LONG_UNWINDING -> TvWarningAmber to TvAmberContainer
        FnoBuildupQuadrant.NEUTRAL -> TvTextSecondary to TvBackground
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text = quadrant.label,
            style = PositionalMiniMono.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PositionalMetricBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TvBackground)
            .border(0.5.dp, TvDivider, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = PositionalMiniMono,
            color = TvTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularNumberStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PositionalEntryRsiStrip(
    rsiSummary: PositionalRsiSummary,
    bias: OptionSide,
    symbol: String,
) {
    val isCe = bias == OptionSide.CE
    val passAccent = if (isCe) TvBullTeal else TvBearCoral
    val passContainer = if (isCe) TvBullContainer else TvBearContainer
    val badgeBg = when {
        rsiSummary.isEntryReady -> passContainer
        rsiSummary.alignedCount >= 3 -> TvAmberContainer
        else -> TvSurfaceElevated
    }
    val badgeColor = when {
        rsiSummary.isEntryReady -> passAccent
        rsiSummary.alignedCount >= 3 -> TvWarningAmber
        else -> TvTextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TvBackground)
            .border(
                width = if (rsiSummary.isEntryReady) 1.dp else 0.75.dp,
                color = if (rsiSummary.isEntryReady) passAccent.copy(alpha = 0.75f) else TvDivider,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Entry RSI (1h • 30m • 15m • 5m • 1m)",
                style = PositionalMiniMono.copy(fontWeight = FontWeight.SemiBold),
                color = TvTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeBg,
                border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.65f)),
                modifier = Modifier.testTag("positional_rsi_entry_badge_$symbol"),
            ) {
                Text(
                    text = rsiSummary.entrySignalLabel,
                    style = PositionalMiniMono.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rsiSummary.slots.forEach { slot ->
                val v = slot.value
                val tileBg = if (slot.passed) passContainer.copy(alpha = 0.85f) else TvSurface
                val tileBorder = if (slot.passed) passAccent.copy(alpha = 0.75f) else TvDivider
                val textColor = when {
                    slot.passed -> passAccent
                    v != null && v > 65.0 -> TvBullTeal
                    v != null && v < 35.0 -> TvBearCoral
                    v != null -> TvTextBright
                    else -> TvTextMuted
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tileBg)
                        .border(0.75.dp, tileBorder, RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = slot.label,
                        style = PositionalMiniMono.copy(fontSize = 8.5.sp),
                        color = TvTextSecondary,
                    )
                    Text(
                        text = if (v != null && !v.isNaN()) {
                            String.format(Locale.US, "%.1f", v)
                        } else {
                            "--"
                        },
                        style = TabularNumberStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionalEma20Strip(
    emaSummary: PositionalEma20Summary,
    bias: OptionSide,
    symbol: String,
) {
    val isCe = bias == OptionSide.CE
    val passAccent = if (isCe) TvBullTeal else TvBearCoral
    val passContainer = if (isCe) TvBullContainer else TvBearContainer
    val hasCross = emaSummary.hasFreshCross
    val badgeBg = when {
        hasCross -> passContainer
        emaSummary.totalCount > 0 && emaSummary.alignedCount == emaSummary.totalCount -> TvAccentBlueContainer
        else -> TvSurfaceElevated
    }
    val badgeColor = when {
        hasCross -> passAccent
        emaSummary.totalCount > 0 && emaSummary.alignedCount == emaSummary.totalCount -> TvAccentBlue
        else -> TvTextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TvBackground)
            .border(
                width = if (hasCross) 1.15.dp else 0.75.dp,
                color = if (hasCross) passAccent.copy(alpha = 0.85f) else TvDivider,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "EMA 20 Cross & Trend (1h • 30m • 15m • 5m • 1m)",
                style = PositionalMiniMono.copy(fontWeight = FontWeight.SemiBold),
                color = TvTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeBg,
                border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.7f)),
                modifier = Modifier.testTag("positional_ema20_badge_$symbol"),
            ) {
                Text(
                    text = emaSummary.signalLabel,
                    style = PositionalMiniMono.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            emaSummary.slots.forEach { slot ->
                val dist = slot.distancePct
                val ema = slot.emaValue
                val tileBg = when {
                    slot.freshCrossForBias -> passContainer
                    slot.alignedWithBias -> passContainer.copy(alpha = 0.4f)
                    else -> TvSurface
                }
                val tileBorder = when {
                    slot.freshCrossForBias -> passAccent
                    slot.alignedWithBias -> passAccent.copy(alpha = 0.45f)
                    else -> TvDivider
                }
                val valueColor = when {
                    slot.freshCrossForBias -> passAccent
                    slot.alignedWithBias -> TvTextBright
                    dist != null -> TvTextSecondary
                    else -> TvTextMuted
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tileBg)
                        .border(if (slot.freshCrossForBias) 1.dp else 0.75.dp, tileBorder, RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (slot.freshCrossForBias) "${slot.label}⚡" else slot.label,
                        style = PositionalMiniMono.copy(
                            fontSize = 8.5.sp,
                            fontWeight = if (slot.freshCrossForBias) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (slot.freshCrossForBias) passAccent else TvTextSecondary,
                        maxLines = 1,
                    )
                    Text(
                        text = if (dist != null) {
                            String.format(Locale.US, "%+.1f%%", dist)
                        } else {
                            "--"
                        },
                        style = TabularNumberStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = valueColor,
                        maxLines = 1,
                    )
                    if (ema != null && ema > 0.0) {
                        Text(
                            text = TechnicalIndicators.formatPrice(ema),
                            style = PositionalMiniMono.copy(fontSize = 8.sp),
                            color = TvTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiveDayDeliveryAndOiBackupTable(
    snapshotsDesc: List<DailyPositionSnapshot>,
    backedUpDaysCount: Int,
    hasFiveDayBackup: Boolean,
    avg5dDeliv: Double?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TvBackground)
            .border(0.75.dp, TvDivider, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (avg5dDeliv != null) {
                    "5D Backup • Deliv Avg ${String.format(Locale.US, "%.1f%%", avg5dDeliv)}"
                } else {
                    "5D Backup • Deliv Chg & OI Chg%"
                },
                style = PositionalMiniMono.copy(fontWeight = FontWeight.SemiBold),
                color = TvTextSecondary,
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (hasFiveDayBackup) TvBullContainer else TvAmberContainer,
            ) {
                Text(
                    text = if (hasFiveDayBackup) "${backedUpDaysCount}D SAVED" else "$backedUpDaysCount/5D BACKUP",
                    style = PositionalMiniMono.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                    color = if (hasFiveDayBackup) TvBullTeal else TvWarningAmber,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Date", style = PositionalMiniMono.copy(fontSize = 8.5.sp), color = TvTextMuted, modifier = Modifier.weight(0.85f))
            Text("Deliv % (Δpp)", style = PositionalMiniMono.copy(fontSize = 8.5.sp), color = TvTextMuted, modifier = Modifier.weight(1.2f))
            Text("Deliv Qty Δ%", style = PositionalMiniMono.copy(fontSize = 8.5.sp), color = TvTextMuted, modifier = Modifier.weight(1.05f))
            Text("OI (OI Chg%)", style = PositionalMiniMono.copy(fontSize = 8.5.sp), color = TvTextMuted, modifier = Modifier.weight(1.25f))
        }

        HorizontalDivider(thickness = 0.5.dp, color = TvDivider)

        snapshotsDesc.forEachIndexed { idx, row ->
            val shortDate = if (row.tradeDate.length >= 10) row.tradeDate.substring(5) else row.tradeDate
            val delivColor = when {
                (row.deliveryPctChangeVsPrev ?: 0.0) > 0.0 -> TvBullTeal
                (row.deliveryPctChangeVsPrev ?: 0.0) < 0.0 -> TvBearCoral
                else -> TvTextPrimary
            }
            val delivQtyColor = when {
                (row.deliveryQtyChangePctVsPrev ?: 0.0) > 0.0 -> TvBullTeal
                (row.deliveryQtyChangePctVsPrev ?: 0.0) < 0.0 -> TvBearCoral
                else -> TvTextSecondary
            }
            val oiColor = when {
                row.oiChangePct > 0.0 -> TvBullTeal
                row.oiChangePct < 0.0 -> TvBearCoral
                else -> TvTextSecondary
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (idx == 0) "$shortDate*" else shortDate,
                    style = PositionalMiniMono.copy(
                        fontSize = 9.sp,
                        fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (idx == 0) TvTextBright else TvTextSecondary,
                    modifier = Modifier.weight(0.85f),
                    maxLines = 1,
                )
                val delivText = if (row.deliveryPct > 0.0) {
                    val pp = row.deliveryPctChangeVsPrev?.let {
                        String.format(Locale.US, " (%+.1f)", it)
                    }.orEmpty()
                    "${String.format(Locale.US, "%.1f%%", row.deliveryPct)}$pp"
                } else {
                    "--"
                }
                Text(
                    text = delivText,
                    style = PositionalMiniMono.copy(fontSize = 9.sp),
                    color = delivColor,
                    modifier = Modifier.weight(1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val delivQtyText = if (row.deliveryQty > 0L) {
                    val qChg = row.deliveryQtyChangePctVsPrev?.let {
                        String.format(Locale.US, " %+.0f%%", it)
                    }.orEmpty()
                    "${TechnicalIndicators.formatVolumeCompact(row.deliveryQty)}$qChg"
                } else {
                    "--"
                }
                Text(
                    text = delivQtyText,
                    style = PositionalMiniMono.copy(fontSize = 9.sp),
                    color = delivQtyColor,
                    modifier = Modifier.weight(1.05f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val oiText = if (row.openInterest > 0L) {
                    String.format(
                        Locale.US,
                        "%s (%+.1f%%)",
                        TechnicalIndicators.formatVolumeCompact(row.openInterest),
                        row.oiChangePct,
                    )
                } else {
                    "--"
                }
                Text(
                    text = oiText,
                    style = PositionalMiniMono.copy(fontSize = 9.sp),
                    color = oiColor,
                    modifier = Modifier.weight(1.25f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PositionalEmptyState(
    activeFilter: PositionalFilter,
    searchQuery: String,
    onResetFilters: () -> Unit,
    onSyncPositionalNse: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = TvSurface,
            border = BorderStroke(1.dp, TvDivider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = TvAccentBlue,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        "No Positional Setups Match \"$searchQuery\""
                    } else {
                        "No ${activeFilter.label} Found Right Now"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = TvTextBright,
                )
                Text(
                    text = "Sync NSE OI & Delivery or switch back to All Setups to inspect developing swings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextSecondary,
                )
                HorizontalDivider(color = TvDivider)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeFilter != PositionalFilter.ALL || searchQuery.isNotBlank()) {
                        OutlinedButton(onClick = onResetFilters) {
                            Text("Show All Setups")
                        }
                    }
                    Button(
                        onClick = onSyncPositionalNse,
                        colors = ButtonDefaults.buttonColors(containerColor = TvAccentBlue),
                    ) {
                        Text("Sync OI/Deliv", color = TvTextBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionalVolRsiBuzzStrip(
    buzzAlerts: List<LiveScannerAlert>,
    onFilterBuzz: () -> Unit,
    onSelectAlert: (LiveScannerAlert) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("positional_vol_rsi_buzz_strip"),
        shape = RoundedCornerShape(10.dp),
        color = TvAmberContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, TvWarningAmber.copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "🔊 POSITIONAL BUZZ • VOL > 2x + RSI > 65 (${buzzAlerts.size})",
                    style = PositionalMiniMono.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    color = TvWarningAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    onClick = onFilterBuzz,
                    shape = RoundedCornerShape(5.dp),
                    color = TvSurface,
                    border = BorderStroke(0.75.dp, TvWarningAmber.copy(alpha = 0.7f)),
                    modifier = Modifier.testTag("positional_buzz_filter_shortcut"),
                ) {
                    Text(
                        text = "FILTER BUZZ",
                        style = PositionalMiniMono.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                        color = TvTextBright,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buzzAlerts.take(10).forEach { alert ->
                    Surface(
                        onClick = { onSelectAlert(alert) },
                        shape = RoundedCornerShape(6.dp),
                        color = TvSurface,
                        border = BorderStroke(0.75.dp, TvBullTeal.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("positional_buzz_chip_${alert.symbol}"),
                    ) {
                        Text(
                            text = "${alert.symbol} • ${alert.title.substringBefore(" BUZZ")}",
                            style = PositionalMiniMono.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = TvBullTeal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val PositionalMiniMono = androidx.compose.ui.text.TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 9.5.sp,
    fontFeatureSettings = "tnum",
)
