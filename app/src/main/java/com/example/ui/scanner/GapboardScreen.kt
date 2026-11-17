package com.example.ui.scanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.domain.indicator.AppDestination
import com.example.domain.model.BoardViewMode
import com.example.domain.model.BreakoutFilterMode
import com.example.domain.model.OptionSide
import com.example.ui.components.ActiveRulesStrip
import com.example.ui.components.AlertSystemBottomSheet
import com.example.ui.components.BottomCrossingWatchBar
import com.example.ui.components.GapboardModeAndSearchBar
import com.example.ui.components.GapboardTablePanel
import com.example.ui.components.GapboardTopHeader
import com.example.ui.components.PivotWatchAlertBanner
import com.example.ui.components.PositionalPageContent
import com.example.ui.components.RsiTriggerFrequencyWidget
import com.example.ui.components.ScannerFiltersBottomSheet
import com.example.ui.components.StockDetailChartSheet
import com.example.ui.components.WatchlistManagerBottomSheet
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAccentBlueContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearContainer
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextBright
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@Composable
fun GapboardScreen(
    viewModel: ScannerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        val isWideScreen = maxWidth >= 720.dp

        Scaffold(
            containerColor = TvBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    Column {
                        HorizontalDivider(color = TvDivider, thickness = 0.75.dp)
                        NavigationBar(
                            containerColor = TvSurface,
                            contentColor = TvTextPrimary,
                            tonalElevation = 0.dp,
                        ) {
                            AppDestination.entries.forEach { dest ->
                                val selected = state.currentPage == dest
                                val cdRes = if (dest == AppDestination.GAPBOARD) {
                                    R.string.cd_nav_gapboard
                                } else {
                                    R.string.cd_nav_positional
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectPage(dest) },
                                    icon = {
                                        DestinationIcon(
                                            destination = dest,
                                            selected = selected,
                                            earlyPositionalCount = state.earlyPositionalCount,
                                            contentDescription = stringResource(cdRes),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TvBullTeal,
                                        selectedTextColor = TvTextBright,
                                        indicatorColor = TvAccentBlueContainer,
                                        unselectedIconColor = TvTextSecondary,
                                        unselectedTextColor = TvTextSecondary,
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${dest.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = TvSurface,
                        contentColor = TvTextPrimary,
                        windowInsets = WindowInsets(0),
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppDestination.entries.forEach { dest ->
                            val selected = state.currentPage == dest
                            val cdRes = if (dest == AppDestination.GAPBOARD) {
                                R.string.cd_nav_gapboard
                            } else {
                                R.string.cd_nav_positional
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectPage(dest) },
                                icon = {
                                    DestinationIcon(
                                        destination = dest,
                                        selected = selected,
                                        earlyPositionalCount = state.earlyPositionalCount,
                                        contentDescription = stringResource(cdRes),
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = TvBullTeal,
                                    selectedTextColor = TvTextBright,
                                    indicatorColor = TvAccentBlueContainer,
                                    unselectedIconColor = TvTextSecondary,
                                    unselectedTextColor = TvTextSecondary,
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.name.lowercase()}"),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(TvBackground),
                ) {
                    GapboardTopHeader(
                        secondsUntilRefresh = state.secondsUntilRefresh,
                        refreshIntervalSec = state.thresholds.refreshIntervalSec,
                        autoRefreshEnabled = state.thresholds.autoRefreshEnabled,
                        scanProgress = state.scanProgress,
                        lastScanTimeIst = state.lastScanTimeIst,
                        unreadAlertCount = state.unreadAlertCount,
                        totalAlertCount = state.unifiedAlertsFeed.size,
                        savedWatchlistCount = state.savedWatchlistCount,
                        onToggleAutoRefresh = viewModel::toggleAutoRefresh,
                        onRefreshNow = viewModel::refreshNow,
                        onOpenAlertCenter = { viewModel.openAlertCenterSheet(true) },
                        onOpenFilters = { viewModel.openFiltersSheet(true) },
                        onOpenWatchlist = { viewModel.openWatchlistSheet(true) },
                    )

                    if (state.currentPage == AppDestination.POSITIONAL) {
                        PositionalPageContent(
                            candidates = state.filteredPositionalCandidates,
                            allCandidateCount = state.allPositionalCandidates.size,
                            earlyIgnitionCount = state.earlyPositionalCount,
                            confirmedHoldCount = state.confirmedPositionalCount,
                            nseSyncedCount = state.positionalOiSyncedCount,
                            fiveDayBackedUpCount = state.fiveDayBackedUpCount,
                            rsiEntryReadyCount = state.rsiEntryReadyCount,
                            ema20CrossCount = state.ema20CrossCount,
                            ema20CrossOrAlignedCount = state.ema20CrossOrAlignedCount,
                            ema20CeAboveCount = state.ema20CeAboveCount,
                            ema20PeBelowCount = state.ema20PeBelowCount,
                            volumeSurgeCount = state.volumeSurgeCount,
                            positionalVolRsiBuzzCount = state.positionalVolRsiBuzzCount,
                            positionalBuzzAlerts = state.positionalBuzzAlertsFeed,
                            activeFilter = state.positionalFilter,
                            searchQuery = state.searchQuery,
                            savedWatchlistSymbols = state.savedWatchlistSymbols,
                            isLoadingInitial = state.isLoadingInitial,
                            isSyncingBatch = state.isSyncingPositionalBatch,
                            isLiveRefreshingPositional = state.isLiveRefreshingPositional,
                            refreshIntervalSec = state.thresholds.refreshIntervalSec,
                            secondsUntilRefresh = state.secondsUntilRefresh,
                            autoRefreshEnabled = state.thresholds.autoRefreshEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onBackToGapboard = { viewModel.selectPage(AppDestination.GAPBOARD) },
                            onSelectFilter = viewModel::setPositionalFilter,
                            onSearchChange = viewModel::updateSearchQuery,
                            onSyncPositionalNse = viewModel::syncPositionalNseBatch,
                            onCandidateClick = viewModel::selectPositionalCandidate,
                            onSelectBuzzAlert = viewModel::selectLiveAlert,
                            onToggleWatchlist = viewModel::toggleWatchlistStock,
                        )
                    } else {
                        ActiveRulesStrip(
                            thresholds = state.thresholds,
                            ceMatchedCount = state.ceMatchedCount,
                            peMatchedCount = state.peMatchedCount,
                            matchedOnly = state.matchedOnly,
                            watchlistOnly = state.watchlistOnly,
                            savedWatchlistCount = state.savedWatchlistCount,
                            breakoutFilter = state.breakoutFilter,
                            ceAboveR1Count = state.ceAboveR1Count,
                            ceAboveR2Count = state.ceAboveR2Count,
                            ceAboveR3Count = state.ceAboveR3Count,
                            peBelowS1Count = state.peBelowS1Count,
                            peBelowS2Count = state.peBelowS2Count,
                            peBelowS3Count = state.peBelowS3Count,
                            onToggleMatchedOnly = { viewModel.setMatchedOnly(!state.matchedOnly) },
                            onToggleWatchlistOnly = viewModel::toggleWatchlistOnly,
                            onToggleR2R3 = viewModel::toggleR2R3Filter,
                            onToggleAboveR3 = viewModel::toggleAboveR3Filter,
                            onToggleS2S3 = viewModel::toggleS2S3Filter,
                            onToggleBelowS3 = viewModel::toggleBelowS3Filter,
                            onToggleSoundAlerts = viewModel::toggleSoundAlerts,
                            onOpenFilters = { viewModel.openFiltersSheet(true) },
                        )

                        GapboardModeAndSearchBar(
                            boardViewMode = state.boardViewMode,
                            searchQuery = state.searchQuery,
                            onSelectMode = viewModel::setBoardViewMode,
                            onSearchChange = viewModel::updateSearchQuery,
                        )

                        PivotWatchAlertBanner(
                            priorityStocks = state.priorityPivotWatchStocks,
                            newlyCrossedSymbols = state.newlyCrossedPriorityPivotSymbols,
                            breakoutFilter = state.breakoutFilter,
                            totalAlertsCount = state.unifiedAlertsFeed.size,
                            dismissed = state.r3NotificationDismissed,
                            onToggleR2R3Filter = viewModel::toggleR2R3Filter,
                            onToggleS2S3Filter = viewModel::toggleS2S3Filter,
                            onOpenAlertCenter = { viewModel.openAlertCenterSheet(true) },
                            onDismiss = viewModel::dismissR3NotificationBanner,
                            onStockClick = { stock, side -> viewModel.selectStock(stock, side) },
                        )

                        if (!state.isLoadingInitial && state.allStocks.isNotEmpty()) {
                            RsiTriggerFrequencyWidget(
                                stats = state.rsiFrequencyStats,
                                savedWatchlistSymbols = state.savedWatchlistSymbols,
                                onStockClick = viewModel::selectStockBySymbol,
                            )
                        }

                        // Partial/Full Network Error Banner
                        if (!state.errorMessage.isNullOrBlank() && state.allStocks.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = TvBearContainer,
                                border = BorderStroke(1.dp, TvBearCoral.copy(alpha = 0.5f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = state.errorMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TvBearCoral,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = viewModel::refreshNow,
                                        colors = ButtonDefaults.buttonColors(containerColor = TvBearCoral),
                                    ) {
                                        Text("Retry", color = TvTextBright)
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 10.dp),
                        ) {
                            when {
                                state.isLoadingInitial && state.allStocks.isEmpty() -> {
                                    InitialScanningOverlay(
                                        completed = state.scanProgress.completed,
                                        total = state.scanProgress.total,
                                    )
                                }

                                !state.errorMessage.isNullOrBlank() && state.allStocks.isEmpty() -> {
                                    OfflineErrorState(
                                        message = state.errorMessage ?: "Network unavailable",
                                        onRetry = viewModel::refreshNow,
                                    )
                                }

                                state.breakoutFilter == BreakoutFilterMode.R2_R3 ||
                                    state.breakoutFilter == BreakoutFilterMode.ABOVE_R3 ||
                                    state.boardViewMode == BoardViewMode.CE_ONLY -> {
                                    GapboardTablePanel(
                                        side = OptionSide.CE,
                                        stocks = state.ceBoardStocks,
                                        thresholds = state.thresholds,
                                        sortColumn = state.ceSortColumn,
                                        sortDescending = state.ceSortDescending,
                                        matchedOnly = state.matchedOnly,
                                        watchlistOnly = state.watchlistOnly,
                                        savedWatchlistSymbols = state.savedWatchlistSymbols,
                                        breakoutFilter = state.breakoutFilter,
                                        newlyCrossedPrioritySymbols = state.newlyCrossedPriorityPivotSymbols,
                                        modifier = Modifier.fillMaxSize(),
                                        onClearBreakoutFilter = { viewModel.setBreakoutFilter(BreakoutFilterMode.ALL) },
                                        onClearWatchlistFilter = { viewModel.setWatchlistOnly(false) },
                                        onSortClick = { col -> viewModel.toggleSortColumn(OptionSide.CE, col) },
                                        onStockClick = { stock -> viewModel.selectStock(stock, OptionSide.CE) },
                                        onToggleWatchlistStock = { stock -> viewModel.toggleWatchlistStock(stock, OptionSide.CE) },
                                    )
                                }

                                state.breakoutFilter == BreakoutFilterMode.S2_S3 ||
                                    state.breakoutFilter == BreakoutFilterMode.BELOW_S3 ||
                                    state.boardViewMode == BoardViewMode.PE_ONLY -> {
                                    GapboardTablePanel(
                                        side = OptionSide.PE,
                                        stocks = state.peBoardStocks,
                                        thresholds = state.thresholds,
                                        sortColumn = state.peSortColumn,
                                        sortDescending = state.peSortDescending,
                                        matchedOnly = state.matchedOnly,
                                        watchlistOnly = state.watchlistOnly,
                                        savedWatchlistSymbols = state.savedWatchlistSymbols,
                                        breakoutFilter = state.breakoutFilter,
                                        newlyCrossedPrioritySymbols = state.newlyCrossedPriorityPivotSymbols,
                                        modifier = Modifier.fillMaxSize(),
                                        onClearBreakoutFilter = { viewModel.setBreakoutFilter(BreakoutFilterMode.ALL) },
                                        onClearWatchlistFilter = { viewModel.setWatchlistOnly(false) },
                                        onSortClick = { col -> viewModel.toggleSortColumn(OptionSide.PE, col) },
                                        onStockClick = { stock -> viewModel.selectStock(stock, OptionSide.PE) },
                                        onToggleWatchlistStock = { stock -> viewModel.toggleWatchlistStock(stock, OptionSide.PE) },
                                    )
                                }

                                isWideScreen -> {
                                    // Tablet / Foldable / Landscape: Side-by-side TradingView Gapboard CE | PE
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        GapboardTablePanel(
                                            side = OptionSide.CE,
                                            stocks = state.ceBoardStocks,
                                            thresholds = state.thresholds,
                                            sortColumn = state.ceSortColumn,
                                            sortDescending = state.ceSortDescending,
                                            matchedOnly = state.matchedOnly,
                                            watchlistOnly = state.watchlistOnly,
                                            savedWatchlistSymbols = state.savedWatchlistSymbols,
                                            newlyCrossedPrioritySymbols = state.newlyCrossedPriorityPivotSymbols,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            onClearWatchlistFilter = { viewModel.setWatchlistOnly(false) },
                                            onSortClick = { col -> viewModel.toggleSortColumn(OptionSide.CE, col) },
                                            onStockClick = { stock -> viewModel.selectStock(stock, OptionSide.CE) },
                                            onToggleWatchlistStock = { stock -> viewModel.toggleWatchlistStock(stock, OptionSide.CE) },
                                        )
                                        GapboardTablePanel(
                                            side = OptionSide.PE,
                                            stocks = state.peBoardStocks,
                                            thresholds = state.thresholds,
                                            sortColumn = state.peSortColumn,
                                            sortDescending = state.peSortDescending,
                                            matchedOnly = state.matchedOnly,
                                            watchlistOnly = state.watchlistOnly,
                                            savedWatchlistSymbols = state.savedWatchlistSymbols,
                                            newlyCrossedPrioritySymbols = state.newlyCrossedPriorityPivotSymbols,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            onClearWatchlistFilter = { viewModel.setWatchlistOnly(false) },
                                            onSortClick = { col -> viewModel.toggleSortColumn(OptionSide.PE, col) },
                                            onStockClick = { stock -> viewModel.selectStock(stock, OptionSide.PE) },
                                            onToggleWatchlistStock = { stock -> viewModel.toggleWatchlistStock(stock, OptionSide.PE) },
                                        )
                                    }
                                }

                                else -> {
                                    // Phone Portrait: Stacked Dual Gapboard (CE top, PE bottom)
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        GapboardTablePanel(
                                            side = OptionSide.CE,
                                            stocks = state.ceBoardStocks,
                                            thresholds = state.thresholds,
                                            sortColumn = state.ceSortColumn,
                                            sortDescending = state.ceSortDescending,
                                            matchedOnly = state.matchedOnly,
                                            watchlistOnly = state.watchlistOnly,
                                            savedWatchlistSymbols = state.savedWatchlistSymbols,
                                            newlyCrossedPrioritySymbols = state.newlyCrossedPriorityPivotSymbols,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            onClearWatchlistFilter = { viewModel.setWatchlistOnly(false) },
                                            onSortClick = { col -> viewModel.toggleSortColumn(OptionSide.CE, col) },
                                            onStockClick = { stock -> viewModel.selectStock(stock, OptionSide.CE) },
                                            onToggleWatchlistStock = { stock -> viewModel.toggleWatchlistStock(stock, OptionSide.CE) },
                                        )
                                        GapboardTablePanel(
                                            side = OptionSide.PE,
                                            stocks = state.peBoardStocks,
                                            thresholds = state.thresholds,
                                            sortColumn = state.peSortColumn,
                                            sortDescending = state.peSortDescending,
                                            matchedOnly = state.matchedOnly,
                                            watchlistOnly = state.watchlistOnly,
                                            savedWatchlistSymbols = state.savedWatchlistSymbols,
                                            newlyCrossedPrioritySymbols = state.newlyCrossedPriorityPivotSymbols,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            onClearWatchlistFilter = { viewModel.setWatchlistOnly(false) },
                                            onSortClick = { col -> viewModel.toggleSortColumn(OptionSide.PE, col) },
                                            onStockClick = { stock -> viewModel.selectStock(stock, OptionSide.PE) },
                                            onToggleWatchlistStock = { stock -> viewModel.toggleWatchlistStock(stock, OptionSide.PE) },
                                        )
                                    }
                                }
                            }
                        }

                        BottomCrossingWatchBar(
                            alerts = state.crossingAlertsFeed,
                            stocks = state.allStocks,
                            soundAlertsEnabled = state.thresholds.soundAlertsEnabled,
                            lastSoundAlertBanner = state.lastSoundAlertBanner,
                            onToggleSoundAlerts = viewModel::toggleSoundAlerts,
                            onAlertClick = viewModel::selectAlert,
                        )
                    }
                }
            }
        }
    }

    // Detail Candlestick + RSI(14) + OI/Delivery 5D Bottom Sheet
    state.selectedStock?.let { selected ->
        StockDetailChartSheet(
            stock = selected,
            side = state.selectedSide,
            timeframe = state.selectedTimeframe,
            thresholds = state.thresholds,
            isSyncingOiDelivery = state.isSyncingDetailOiDelivery,
            nseSyncStatus = state.detailNseSyncStatus,
            isInWatchlist = selected.symbol in state.savedWatchlistSymbols,
            onSelectTimeframe = viewModel::setChartTimeframe,
            onSyncOiDelivery = viewModel::refreshSelectedStockOiDelivery,
            onToggleWatchlist = { viewModel.toggleWatchlistStock(selected, state.selectedSide) },
            onDismiss = viewModel::closeStockDetail,
        )
    }

    // Scanner Criteria & 10s Refresh Bottom Sheet
    if (state.showFiltersSheet) {
        ScannerFiltersBottomSheet(
            current = state.thresholds,
            onApply = viewModel::updateThresholds,
            onResetDefaults = viewModel::resetDefaultThresholds,
            onTestSound = viewModel::testSoundAlert,
            onDismiss = { viewModel.openFiltersSheet(false) },
        )
    }

    // F&O Watchlist Manager Bottom Sheet
    if (state.showWatchlistSheet) {
        WatchlistManagerBottomSheet(
            symbols = state.watchlistSymbols,
            savedWatchlistEntries = state.savedWatchlistEntries,
            allStocks = state.allStocks,
            isAdding = state.addSymbolLoading,
            addError = state.addSymbolError,
            onAddSymbol = viewModel::addWatchlistSymbol,
            onRemoveSymbol = viewModel::removeWatchlistSymbol,
            onResetDefaults = viewModel::resetWatchlistDefaults,
            onToggleSaveSymbol = { sym -> viewModel.saveSymbolToWatchlist(sym) },
            onSelectWatchlistEntry = viewModel::selectWatchlistEntry,
            onRemoveSavedWatchlist = viewModel::removeSavedWatchlistSymbol,
            onClearSavedWatchlist = viewModel::clearSavedWatchlist,
            onDismiss = { viewModel.openWatchlistSheet(false) },
        )
    }

    // Live Pivot (R1-R3 / S1-S3) & RSI Alert System Center Bottom Sheet
    if (state.showAlertCenterSheet) {
        AlertSystemBottomSheet(
            alerts = state.unifiedAlertsFeed,
            ceAboveR1Count = state.ceAboveR1Count,
            ceAboveR2Count = state.ceAboveR2Count,
            ceAboveR3Count = state.ceAboveR3Count,
            peBelowS1Count = state.peBelowS1Count,
            peBelowS2Count = state.peBelowS2Count,
            peBelowS3Count = state.peBelowS3Count,
            soundAlertsEnabled = state.thresholds.soundAlertsEnabled,
            systemNotificationsEnabled = state.systemNotificationsEnabled,
            onToggleSoundAlerts = viewModel::toggleSoundAlerts,
            onToggleSystemNotifications = viewModel::toggleSystemNotifications,
            onTestSound = viewModel::testSoundAlert,
            onTestPositionalBuzz = viewModel::testPositionalBuzzAlert,
            onFilterR2R3 = { viewModel.setBreakoutFilter(BreakoutFilterMode.R2_R3) },
            onFilterS2S3 = { viewModel.setBreakoutFilter(BreakoutFilterMode.S2_S3) },
            onClearAlerts = viewModel::clearAlertHistory,
            onSelectAlert = viewModel::selectLiveAlert,
            onDismiss = { viewModel.openAlertCenterSheet(false) },
        )
    }
}

@Composable
private fun DestinationIcon(
    destination: AppDestination,
    selected: Boolean,
    earlyPositionalCount: Int,
    contentDescription: String,
) {
    val icon = when (destination) {
        AppDestination.GAPBOARD -> if (selected) Icons.Rounded.Bolt else Icons.Outlined.Bolt
        AppDestination.POSITIONAL -> if (selected) Icons.Rounded.Layers else Icons.Outlined.Layers
    }
    if (destination == AppDestination.POSITIONAL && earlyPositionalCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = TvBullTeal,
                    contentColor = TvBackground,
                ) {
                    Text(text = earlyPositionalCount.coerceAtMost(99).toString())
                }
            },
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun InitialScanningOverlay(
    completed: Int,
    total: Int,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(10.dp),
        color = TvSurface,
        border = BorderStroke(1.dp, TvDivider),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                CircularProgressIndicator(
                    color = TvBullTeal,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scanning NSE F&O Universe...",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextBright,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Fetching 1m, 5m, 15m & 1h intraday candles ($completed / $total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun OfflineErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(10.dp),
        color = TvSurface,
        border = BorderStroke(1.dp, TvDivider),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "Market Feed Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextBright,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvTextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = TvAccentBlue),
                    modifier = Modifier.testTag("retry_scan_button"),
                ) {
                    Text("Retry Live Scan", color = TvTextBright)
                }
            }
        }
    }
}
