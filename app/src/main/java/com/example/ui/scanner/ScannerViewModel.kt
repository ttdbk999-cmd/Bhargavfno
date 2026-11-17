package com.example.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AlertNotificationHelper
import com.example.audio.SoundAlertManager
import com.example.data.local.FnoDatabase
import com.example.data.local.WatchlistStockEntity
import com.example.data.preferences.ScannerPreferencesRepository
import com.example.data.remote.AndroidNseWebViewBridge
import com.example.data.remote.NetworkModule
import com.example.data.remote.NseNetworkModule
import com.example.data.repository.FnoScannerRepository
import com.example.data.repository.NseOiDeliveryRepository
import com.example.data.repository.ScanBatchOutcome
import com.example.data.repository.WatchlistRepository
import com.example.data.universe.FnoUniverse
import com.example.domain.indicator.AppDestination
import com.example.domain.indicator.PivotAlertDetector
import com.example.domain.indicator.PositionSignalEvaluator
import com.example.domain.indicator.PositionalAnalyzer
import com.example.domain.indicator.PositionalCandidate
import com.example.domain.indicator.PositionalFilter
import com.example.domain.indicator.PositionalStage
import com.example.domain.indicator.RsiCrossoverDetector
import com.example.domain.indicator.RsiFrequencyAnalyzer
import com.example.domain.indicator.RsiFrequencyStat
import com.example.domain.indicator.RsiTriggerEvent
import com.example.domain.model.AtmOptionBundle
import com.example.domain.model.BoardViewMode
import com.example.domain.model.BreakoutFilterMode
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.DailyPositionSnapshot
import com.example.domain.model.LiveScannerAlert
import com.example.domain.model.OptionSide
import com.example.domain.model.RsiCrossAlert
import com.example.domain.model.ScanProgress
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.SortColumn
import com.example.domain.model.StockScanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class ScannerUiState(
    val isLoadingInitial: Boolean = true,
    val errorMessage: String? = null,
    val allStocks: List<StockScanResult> = emptyList(),
    val crossingAlertsFeed: List<RsiCrossAlert> = emptyList(),
    val pivotAlertsFeed: List<LiveScannerAlert> = emptyList(),
    val positionalBuzzAlertsFeed: List<LiveScannerAlert> = emptyList(),
    val unifiedAlertsFeed: List<LiveScannerAlert> = emptyList(),
    val rsiFrequencyStats: List<RsiFrequencyStat> = emptyList(),
    val unreadAlertCount: Int = 0,
    val thresholds: ScannerThresholds = ScannerThresholds(),
    val boardViewMode: BoardViewMode = BoardViewMode.DUAL_SPLIT,
    val matchedOnly: Boolean = false,
    val breakoutFilter: BreakoutFilterMode = BreakoutFilterMode.ALL,
    val newlyCrossedR3Symbols: Set<String> = emptySet(),
    val newlyCrossedPriorityPivotSymbols: Set<String> = emptySet(),
    val r3NotificationDismissed: Boolean = false,
    val searchQuery: String = "",
    val ceSortColumn: SortColumn = SortColumn.CHG_PCT,
    val ceSortDescending: Boolean = true,
    val peSortColumn: SortColumn = SortColumn.CHG_PCT,
    val peSortDescending: Boolean = false,
    val secondsUntilRefresh: Int = 10,
    val scanProgress: ScanProgress = ScanProgress(),
    val lastScanTimeIst: String = "--:--:--",
    val failedCount: Int = 0,
    val nseSyncedCount: Int = 0,
    val isSyncingDetailOiDelivery: Boolean = false,
    val detailNseSyncStatus: String? = null,
    val watchlistSymbols: List<String> = FnoUniverse.defaultSymbols,
    val savedWatchlistEntries: List<WatchlistStockEntity> = emptyList(),
    val savedWatchlistSymbols: Set<String> = emptySet(),
    val watchlistOnly: Boolean = false,
    val selectedStock: StockScanResult? = null,
    val selectedSide: OptionSide = OptionSide.CE,
    val selectedTimeframe: ChartTimeframe = ChartTimeframe.M5,
    val showFiltersSheet: Boolean = false,
    val showWatchlistSheet: Boolean = false,
    val showAlertCenterSheet: Boolean = false,
    val systemNotificationsEnabled: Boolean = false,
    val addSymbolLoading: Boolean = false,
    val addSymbolError: String? = null,
    val lastSoundAlertBanner: String? = null,
    val currentPage: AppDestination = AppDestination.GAPBOARD,
    val positionalFilter: PositionalFilter = PositionalFilter.ALL,
    val isSyncingPositionalBatch: Boolean = false,
    val isLiveRefreshingPositional: Boolean = false,
) {
    val savedWatchlistCount: Int
        get() = savedWatchlistSymbols.size

    val allPositionalCandidates: List<PositionalCandidate>
        get() = PositionalAnalyzer.analyze(allStocks, thresholds)

    val earlyPositionalCount: Int
        get() = allPositionalCandidates.count { it.stage == PositionalStage.EARLY_IGNITION }

    val confirmedPositionalCount: Int
        get() = allPositionalCandidates.count { it.stage == PositionalStage.CONFIRMED_HOLD }

    val positionalOiSyncedCount: Int
        get() = allPositionalCandidates.count { it.hasOiData }

    val fiveDayBackedUpCount: Int
        get() = allPositionalCandidates.count { it.hasFiveDayBackup }

    val rsiEntryReadyCount: Int
        get() = allPositionalCandidates.count { it.isRsiEntryReady }

    val ema20CrossCount: Int
        get() = allPositionalCandidates.count { it.hasFreshEma20Cross }

    val ema20CrossOrAlignedCount: Int
        get() = allPositionalCandidates.count { it.isEma20CrossOrAligned }

    val ema20CeAboveCount: Int
        get() = allPositionalCandidates.count { it.bias == OptionSide.CE && it.isEma20CrossOrAligned }

    val ema20PeBelowCount: Int
        get() = allPositionalCandidates.count { it.bias == OptionSide.PE && it.isEma20CrossOrAligned }

    val volumeSurgeCount: Int
        get() = allPositionalCandidates.count { it.hasVolumeSurge2x }

    val positionalVolRsiBuzzCount: Int
        get() = allPositionalCandidates.count { it.hasVol2xAndRsi65 }

    val filteredPositionalCandidates: List<PositionalCandidate>
        get() {
            val q = searchQuery.trim().uppercase()
            return allPositionalCandidates.filter { candidate ->
                val matchesQuery = q.isEmpty() ||
                    candidate.stock.symbol.contains(q) ||
                    candidate.stock.companyName.uppercase().contains(q) ||
                    candidate.stock.sector.uppercase().contains(q)
                if (!matchesQuery) return@filter false
                when (positionalFilter) {
                    PositionalFilter.ALL -> true
                    PositionalFilter.VOL2X_RSI65 -> candidate.hasVol2xAndRsi65
                    PositionalFilter.VOLUME_SURGE -> candidate.hasVolumeSurge2x
                    PositionalFilter.EMA20_CROSS -> candidate.hasFreshEma20Cross
                    PositionalFilter.EMA20_CROSS_OR_ALIGNED -> candidate.isEma20CrossOrAligned
                    PositionalFilter.EMA20_CE_ABOVE -> candidate.bias == OptionSide.CE && candidate.isEma20CrossOrAligned
                    PositionalFilter.EMA20_PE_BELOW -> candidate.bias == OptionSide.PE && candidate.isEma20CrossOrAligned
                    PositionalFilter.RSI_ENTRY -> candidate.isRsiEntryReady
                    PositionalFilter.EARLY_IGNITION -> candidate.stage == PositionalStage.EARLY_IGNITION
                    PositionalFilter.CONFIRMED_HOLD -> candidate.stage == PositionalStage.CONFIRMED_HOLD
                    PositionalFilter.CE_LONGS -> candidate.bias == OptionSide.CE
                    PositionalFilter.PE_SHORTS -> candidate.bias == OptionSide.PE
                    PositionalFilter.WATCHLIST -> candidate.stock.symbol in savedWatchlistSymbols
                }
            }
        }
    val ceMatchedCount: Int
        get() = allStocks.count { it.evaluateCe(thresholds).allPassed }

    val peMatchedCount: Int
        get() = allStocks.count { it.evaluatePe(thresholds).allPassed }

    val ceAboveR1Count: Int
        get() = allStocks.count { it.isAboveR1 }

    val ceAboveR2Count: Int
        get() = allStocks.count { it.isAboveR2 }

    val ceAboveR3Count: Int
        get() = allStocks.count { it.isAboveR3 }

    val peBelowS1Count: Int
        get() = allStocks.count { it.isBelowS1 }

    val peBelowS2Count: Int
        get() = allStocks.count { it.isBelowS2 }

    val peBelowS3Count: Int
        get() = allStocks.count { it.isBelowS3 }

    val aboveR3Stocks: List<StockScanResult>
        get() = allStocks
            .filter { it.isAboveR3 }
            .sortedWith(
                compareByDescending<StockScanResult> { it.symbol in newlyCrossedR3Symbols }
                    .thenByDescending { it.percentAboveR3() ?: 0.0 }
                    .thenByDescending { it.changePercent }
            )

    val priorityPivotWatchStocks: List<StockScanResult>
        get() = allStocks
            .filter { it.isAboveR2 || it.isBelowS2 }
            .sortedWith(
                compareByDescending<StockScanResult> { it.symbol in newlyCrossedPriorityPivotSymbols }
                    .thenByDescending { it.isAboveR3 || it.isBelowS3 }
                    .thenByDescending { kotlin.math.abs(it.changePercent) }
            )

    val ceHighPriorityCount: Int
        get() = allStocks.count { it.isHighPriority(OptionSide.CE) }

    val peHighPriorityCount: Int
        get() = allStocks.count { it.isHighPriority(OptionSide.PE) }

    val ceBoardStocks: List<StockScanResult>
        get() {
            val q = searchQuery.trim().uppercase()
            val filtered = allStocks.filter { stock ->
                if (watchlistOnly && stock.symbol !in savedWatchlistSymbols) return@filter false
                val matchesSearch = q.isEmpty() ||
                    stock.symbol.contains(q) ||
                    stock.companyName.uppercase().contains(q)
                if (!matchesSearch) return@filter false
                when (breakoutFilter) {
                    BreakoutFilterMode.ABOVE_R3 -> return@filter stock.isAboveR3
                    BreakoutFilterMode.R2_R3 -> return@filter stock.isAboveR2
                    else -> Unit
                }

                val eval = stock.evaluateCe(thresholds)
                val highPrio = stock.isHighPriority(OptionSide.CE)
                if (matchedOnly) {
                    eval.allPassed || highPrio
                } else if (watchlistOnly) {
                    true
                } else {
                    stock.changePercent >= 0.0 || eval.allPassed || highPrio || stock.isAboveR2
                }
            }
            return sortStocks(filtered, OptionSide.CE, ceSortColumn, ceSortDescending)
        }

    val peBoardStocks: List<StockScanResult>
        get() {
            val q = searchQuery.trim().uppercase()
            val filtered = allStocks.filter { stock ->
                if (watchlistOnly && stock.symbol !in savedWatchlistSymbols) return@filter false
                val matchesSearch = q.isEmpty() ||
                    stock.symbol.contains(q) ||
                    stock.companyName.uppercase().contains(q)
                if (!matchesSearch) return@filter false
                when (breakoutFilter) {
                    BreakoutFilterMode.BELOW_S3 -> return@filter stock.isBelowS3
                    BreakoutFilterMode.S2_S3 -> return@filter stock.isBelowS2
                    else -> Unit
                }

                val eval = stock.evaluatePe(thresholds)
                val highPrio = stock.isHighPriority(OptionSide.PE)
                if (matchedOnly) {
                    eval.allPassed || highPrio
                } else if (watchlistOnly) {
                    true
                } else {
                    stock.changePercent < 0.0 || eval.allPassed || highPrio || stock.isBelowS2
                }
            }
            return sortStocks(filtered, OptionSide.PE, peSortColumn, peSortDescending)
        }

    private fun sortStocks(
        list: List<StockScanResult>,
        side: OptionSide,
        column: SortColumn,
        descending: Boolean,
    ): List<StockScanResult> {
        val baseComparator = Comparator<StockScanResult> { a, b ->
            when (column) {
                SortColumn.SYMBOL -> a.symbol.compareTo(b.symbol)
                SortColumn.LAST -> a.lastPrice.compareTo(b.lastPrice)
                SortColumn.CHG -> a.change.compareTo(b.change)
                SortColumn.CHG_PCT -> a.changePercent.compareTo(b.changePercent)
                SortColumn.VOL -> a.sharesVolume.compareTo(b.sharesVolume)
                SortColumn.RVOL -> a.rvol.compareTo(b.rvol)
                SortColumn.RSI_5M -> (a.rsi5m ?: -1.0).compareTo(b.rsi5m ?: -1.0)
            }
        }
        val directional = if (descending) baseComparator.reversed() else baseComparator
        // Boost High-Priority RSI crossings (incl. 1m >70/<30), newly crossed R2/R3 & S2/S3, 1m+5m aligned, >R3/<S3, and >R2/<S2 breakouts
        val priorityComparator = compareByDescending<StockScanResult> { it.isHighPriority(side) }
            .thenByDescending {
                it.symbol in newlyCrossedPriorityPivotSymbols ||
                    (side == OptionSide.CE && it.symbol in newlyCrossedR3Symbols)
            }
            .thenByDescending { it.is1mAnd5mAligned(side, thresholds) }
            .thenByDescending { if (side == OptionSide.CE) it.isAboveR3 else it.isBelowS3 }
            .thenByDescending { if (side == OptionSide.CE) it.isAboveR2 else it.isBelowS2 }
            .then(directional)
        return list.sortedWith(priorityComparator)
    }
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = ScannerPreferencesRepository(application.applicationContext)
    private val scannerRepo = FnoScannerRepository(NetworkModule.yahooFinanceApi)
    private val browserClient = AndroidNseWebViewBridge(application.applicationContext)
    private val db = FnoDatabase.getInstance(application.applicationContext)
    private val watchlistRepo = WatchlistRepository(db.watchlistDao())
    private val nseRepo = NseOiDeliveryRepository(
        nseApi = NseNetworkModule.nseApi,
        dao = db.dailyFnoMetricDao(),
        browserClient = browserClient,
    )
    private val soundAlertManager = SoundAlertManager(application.applicationContext)
    private val alertNotificationHelper = AlertNotificationHelper(application.applicationContext)

    private val istTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("Asia/Kolkata"))

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val previousRsiByKey = ConcurrentHashMap<String, Double>()
    private val activeAlertsById = ConcurrentHashMap<String, RsiCrossAlert>()
    private val recordedLiveRsiTriggers = java.util.Collections.synchronizedList(mutableListOf<RsiTriggerEvent>())
    private val previousCePivotRankBySymbol = ConcurrentHashMap<String, Int>()
    private val previousPePivotRankBySymbol = ConcurrentHashMap<String, Int>()
    private val activePivotAlertsById = ConcurrentHashMap<String, LiveScannerAlert>()
    private val previousVolumeSurgeSymbols: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    private val activeVolumeAlertsById = ConcurrentHashMap<String, LiveScannerAlert>()
    private val previousPositionalBuzzSymbols: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    private val activePositionalBuzzAlertsById = ConcurrentHashMap<String, LiveScannerAlert>()
    private var previousCe5of5Symbols: Set<String> = emptySet()
    private var previousPe5of5Symbols: Set<String> = emptySet()
    private var previousAboveR3Symbols: Set<String> = emptySet()
    private var hasCompletedInitialBaselineScan: Boolean = false

    private var latestHistoryMap: Map<String, List<DailyPositionSnapshot>> = emptyMap()
    private var refreshLoopJob: Job? = null
    private var nseBatchJob: Job? = null
    private var positionalLiveJob: Job? = null
    private var positionalRotationOffset: Int = 0
    private var lastNseBatchSyncMs: Long = 0L

    init {
        viewModelScope.launch {
            nseRepo.observeAllHistoryBySymbol().collect { historyBySymbol ->
                latestHistoryMap = historyBySymbol
                _uiState.update { state ->
                    val enriched = state.allStocks.map { enrichWithOiDelivery(it, historyBySymbol) }
                    val updatedSelected = state.selectedStock?.let { sel ->
                        enriched.firstOrNull { it.symbol == sel.symbol }
                            ?: enrichWithOiDelivery(sel, historyBySymbol)
                    }
                    state.copy(
                        allStocks = enriched,
                        selectedStock = updatedSelected,
                    )
                }
            }
        }

        viewModelScope.launch {
            val initialConfig = prefsRepo.configFlow.first()
            _uiState.update {
                it.copy(
                    thresholds = initialConfig.thresholds,
                    boardViewMode = initialConfig.boardViewMode,
                    matchedOnly = initialConfig.matchedOnly,
                    watchlistSymbols = initialConfig.symbols,
                    secondsUntilRefresh = initialConfig.thresholds.refreshIntervalSec,
                )
            }
            startRefreshLoop(immediateScan = true)

            prefsRepo.configFlow.collect { config ->
                _uiState.update { state ->
                    state.copy(
                        thresholds = config.thresholds,
                        boardViewMode = config.boardViewMode,
                        matchedOnly = config.matchedOnly,
                        watchlistSymbols = config.symbols,
                    )
                }
            }
        }

        viewModelScope.launch {
            watchlistRepo.watchlistFlow.collect { entries ->
                val symbolSet = entries.map { it.symbol.uppercase() }.toSet()
                _uiState.update { state ->
                    state.copy(
                        savedWatchlistEntries = entries,
                        savedWatchlistSymbols = symbolSet,
                        watchlistOnly = if (symbolSet.isEmpty()) false else state.watchlistOnly,
                    )
                }
            }
        }
    }

    fun refreshNow() {
        lastNseBatchSyncMs = 0L
        startRefreshLoop(immediateScan = true)
    }

    fun toggleAutoRefresh() {
        val next = !_uiState.value.thresholds.autoRefreshEnabled
        viewModelScope.launch {
            prefsRepo.setAutoRefresh(next)
            _uiState.update {
                it.copy(
                    thresholds = it.thresholds.copy(autoRefreshEnabled = next),
                    secondsUntilRefresh = it.thresholds.refreshIntervalSec,
                )
            }
            if (next) {
                startRefreshLoop(immediateScan = false)
            }
        }
    }

    fun toggleSoundAlerts() {
        val next = !_uiState.value.thresholds.soundAlertsEnabled
        viewModelScope.launch {
            prefsRepo.setSoundAlertsEnabled(next)
            _uiState.update {
                it.copy(
                    thresholds = it.thresholds.copy(soundAlertsEnabled = next),
                    lastSoundAlertBanner = if (next) "Sound alerts enabled" else "Sound alerts muted",
                )
            }
            if (next) {
                soundAlertManager.playSignalMatchChime(OptionSide.CE)
            }
        }
    }

    fun testSoundAlert(side: OptionSide) {
        soundAlertManager.playHighPriorityCross(side, forceTest = true)
        val label = if (side == OptionSide.CE) "CE (>70) bullish chime tested" else "PE (<30) bearish alert tested"
        _uiState.update { it.copy(lastSoundAlertBanner = label) }
    }

    fun testPositionalBuzzAlert() {
        soundAlertManager.playPositionalVolRsiBuzz(forceTest = true)
        _uiState.update {
            it.copy(lastSoundAlertBanner = "🔊 Positional Buzz tested (Vol >2x + RSI >65 triple-chime & vibration)")
        }
    }

    fun setBoardViewMode(mode: BoardViewMode) {
        viewModelScope.launch {
            prefsRepo.setBoardViewMode(mode)
        }
        _uiState.update { it.copy(boardViewMode = mode) }
    }

    fun selectPage(page: AppDestination) {
        _uiState.update { it.copy(currentPage = page) }
        if (page == AppDestination.POSITIONAL && _uiState.value.allStocks.isNotEmpty()) {
            triggerPositional10sLiveNseSync()
            val backedUpCount = _uiState.value.fiveDayBackedUpCount
            if (backedUpCount < 5 && !_uiState.value.isSyncingPositionalBatch) {
                syncPositionalNseBatch()
            }
        }
    }

    fun setPositionalFilter(filter: PositionalFilter) {
        _uiState.update { it.copy(positionalFilter = filter) }
        if (_uiState.value.currentPage == AppDestination.POSITIONAL && _uiState.value.allStocks.isNotEmpty()) {
            triggerPositional10sLiveNseSync()
        }
    }

    fun selectPositionalCandidate(candidate: PositionalCandidate) {
        selectStock(candidate.stock, candidate.bias, ChartTimeframe.M15)
    }

    fun syncPositionalNseBatch() {
        val stocks = _uiState.value.allStocks
        if (stocks.isEmpty() || _uiState.value.isSyncingPositionalBatch) return
        nseBatchJob?.cancel()
        nseBatchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSyncingPositionalBatch = true) }
            try {
                val savedSet = _uiState.value.savedWatchlistSymbols
                val prioritizedCandidates = _uiState.value.allPositionalCandidates
                    .sortedWith(
                        compareByDescending<PositionalCandidate> { it.stock.symbol in savedSet }
                            .thenBy { it.hasFiveDayBackup }
                            .thenBy { it.hasOiData }
                            .thenBy { it.stage.ordinal }
                            .thenByDescending { it.convictionScore }
                    )
                    .map { it.stock }
                    .ifEmpty { stocks }
                    .take(20)
                val payload = prioritizedCandidates.map {
                    Triple(it.symbol, it.lastPrice, it.changePercent to it.sharesVolume)
                }
                val synced = nseRepo.syncBatch(payload)
                _uiState.update { state ->
                    val updatedAll = state.allStocks.map { enrichWithOiDelivery(it, latestHistoryMap) }
                    val updatedSelected = state.selectedStock?.let { sel ->
                        updatedAll.firstOrNull { it.symbol == sel.symbol }
                            ?: enrichWithOiDelivery(sel, latestHistoryMap)
                    }
                    state.copy(
                        allStocks = updatedAll,
                        selectedStock = updatedSelected,
                        nseSyncedCount = maxOf(state.nseSyncedCount, synced),
                        isSyncingPositionalBatch = false,
                        lastSoundAlertBanner = if (synced > 0) {
                            "Positional NSE OI & 5D Delivery synced ($synced setups)"
                        } else {
                            "NSE India blocked/timed out — retry Sync 5D Backup"
                        },
                    )
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isSyncingPositionalBatch = false) }
            }
        }
    }

    fun setMatchedOnly(matchedOnly: Boolean) {
        viewModelScope.launch {
            prefsRepo.setMatchedOnly(matchedOnly)
        }
        _uiState.update { it.copy(matchedOnly = matchedOnly) }
    }

    fun toggleWatchlistOnly() {
        _uiState.update { state ->
            val next = !state.watchlistOnly
            state.copy(watchlistOnly = next)
        }
    }

    fun setWatchlistOnly(enabled: Boolean) {
        _uiState.update { it.copy(watchlistOnly = enabled) }
    }

    fun toggleWatchlistStock(stock: StockScanResult, preferredSide: OptionSide) {
        viewModelScope.launch {
            val saved = watchlistRepo.toggleStock(stock, preferredSide)
            _uiState.update { state ->
                state.copy(
                    lastSoundAlertBanner = if (saved) {
                        "★ ${stock.symbol} saved to Watchlist"
                    } else {
                        "${stock.symbol} removed from Watchlist"
                    },
                )
            }
        }
    }

    fun saveSymbolToWatchlist(symbol: String, preferredSide: OptionSide = OptionSide.CE) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val existing = _uiState.value.allStocks.firstOrNull { it.symbol == clean }
            if (existing != null) {
                watchlistRepo.toggleStock(existing, preferredSide).let { saved ->
                    if (!saved) {
                        // Already saved — keep saved when explicitly calling save
                        watchlistRepo.saveSymbol(clean, preferredSide)
                    }
                }
            } else {
                watchlistRepo.saveSymbol(clean, preferredSide)
            }
            if (!_uiState.value.watchlistSymbols.contains(clean)) {
                addWatchlistSymbol(clean)
            }
            _uiState.update {
                it.copy(lastSoundAlertBanner = "★ $clean saved to Watchlist")
            }
        }
    }

    fun removeSavedWatchlistSymbol(symbol: String) {
        viewModelScope.launch {
            watchlistRepo.removeSymbol(symbol)
        }
    }

    fun clearSavedWatchlist() {
        viewModelScope.launch {
            watchlistRepo.clearAll()
            _uiState.update { it.copy(watchlistOnly = false) }
        }
    }

    fun selectWatchlistEntry(entry: WatchlistStockEntity) {
        val side = OptionSide.entries.firstOrNull { it.name == entry.preferredSide } ?: OptionSide.CE
        val match = _uiState.value.allStocks.firstOrNull { it.symbol == entry.symbol }
        _uiState.update { it.copy(showWatchlistSheet = false) }
        if (match != null) {
            selectStock(match, side)
        } else {
            viewModelScope.launch {
                val fetched = scannerRepo.validateAndFetchSymbol(entry.symbol)
                fetched.onSuccess { stock ->
                    val detection = RsiCrossoverDetector.evaluateBatch(
                        stocks = listOf(stock),
                        previousRsiByKey = previousRsiByKey,
                        activeAlertsById = activeAlertsById,
                    )
                    val enriched = enrichWithOiDelivery(detection.enrichedStocks.first(), latestHistoryMap)
                    _uiState.update { state ->
                        state.copy(
                            allStocks = listOf(enriched) + state.allStocks.filterNot { it.symbol == enriched.symbol },
                        )
                    }
                    selectStock(enriched, side)
                }.onFailure {
                    _uiState.update {
                        it.copy(lastSoundAlertBanner = "Unable to load live quote for ${entry.symbol}")
                    }
                }
            }
        }
    }

    fun setBreakoutFilter(mode: BreakoutFilterMode) {
        _uiState.update { it.copy(breakoutFilter = mode) }
    }

    fun toggleR2R3Filter() {
        _uiState.update { state ->
            val next = if (state.breakoutFilter == BreakoutFilterMode.R2_R3) {
                BreakoutFilterMode.ALL
            } else {
                BreakoutFilterMode.R2_R3
            }
            state.copy(breakoutFilter = next)
        }
    }

    fun toggleS2S3Filter() {
        _uiState.update { state ->
            val next = if (state.breakoutFilter == BreakoutFilterMode.S2_S3) {
                BreakoutFilterMode.ALL
            } else {
                BreakoutFilterMode.S2_S3
            }
            state.copy(breakoutFilter = next)
        }
    }

    fun toggleAboveR3Filter() {
        _uiState.update { state ->
            val next = if (state.breakoutFilter == BreakoutFilterMode.ABOVE_R3) {
                BreakoutFilterMode.ALL
            } else {
                BreakoutFilterMode.ABOVE_R3
            }
            state.copy(breakoutFilter = next)
        }
    }

    fun toggleBelowS3Filter() {
        _uiState.update { state ->
            val next = if (state.breakoutFilter == BreakoutFilterMode.BELOW_S3) {
                BreakoutFilterMode.ALL
            } else {
                BreakoutFilterMode.BELOW_S3
            }
            state.copy(breakoutFilter = next)
        }
    }

    fun dismissR3NotificationBanner() {
        _uiState.update { it.copy(r3NotificationDismissed = true) }
    }

    fun openAlertCenterSheet(open: Boolean) {
        _uiState.update { state ->
            state.copy(
                showAlertCenterSheet = open,
                unreadAlertCount = if (open) 0 else state.unreadAlertCount,
            )
        }
    }

    fun toggleSystemNotifications(enabled: Boolean) {
        val actuallyEnabled = enabled && alertNotificationHelper.hasNotificationPermission()
        _uiState.update {
            it.copy(
                systemNotificationsEnabled = actuallyEnabled,
                lastSoundAlertBanner = if (actuallyEnabled) {
                    "Push & Sound Alert System ACTIVE (R2/R3, S2/S3 & RSI)"
                } else {
                    "In-app Alert System ACTIVE"
                },
            )
        }
    }

    fun clearAlertHistory() {
        activeAlertsById.clear()
        activePivotAlertsById.clear()
        activeVolumeAlertsById.clear()
        activePositionalBuzzAlertsById.clear()
        _uiState.update {
            it.copy(
                crossingAlertsFeed = emptyList(),
                pivotAlertsFeed = emptyList(),
                positionalBuzzAlertsFeed = emptyList(),
                unifiedAlertsFeed = emptyList(),
                unreadAlertCount = 0,
            )
        }
    }

    fun selectLiveAlert(alert: LiveScannerAlert) {
        val match = _uiState.value.allStocks.firstOrNull { it.symbol == alert.symbol } ?: return
        _uiState.update { it.copy(showAlertCenterSheet = false) }
        selectStock(match, alert.side, alert.timeframe)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSortColumn(side: OptionSide, column: SortColumn) {
        _uiState.update { state ->
            if (side == OptionSide.CE) {
                val nextDesc = if (state.ceSortColumn == column) !state.ceSortDescending else true
                state.copy(ceSortColumn = column, ceSortDescending = nextDesc)
            } else {
                val defaultAsc = column == SortColumn.CHG_PCT || column == SortColumn.CHG || column == SortColumn.SYMBOL
                val nextDesc = if (state.peSortColumn == column) !state.peSortDescending else !defaultAsc
                state.copy(peSortColumn = column, peSortDescending = nextDesc)
            }
        }
    }

    fun openFiltersSheet(open: Boolean) {
        _uiState.update { it.copy(showFiltersSheet = open) }
    }

    fun openWatchlistSheet(open: Boolean) {
        _uiState.update { it.copy(showWatchlistSheet = open, addSymbolError = null) }
    }

    fun updateThresholds(newThresholds: ScannerThresholds) {
        val sanitized = newThresholds.copy(
            minRvol = newThresholds.minRvol.coerceAtLeast(0.0),
            minShares = newThresholds.minShares.coerceAtLeast(0L),
            minRsi1h = newThresholds.minRsi1h.coerceIn(0.0, 100.0),
            minRsi15m = newThresholds.minRsi15m.coerceIn(0.0, 100.0),
            minRsi5m = newThresholds.minRsi5m.coerceIn(0.0, 100.0),
            minRsi1m = newThresholds.minRsi1m.coerceIn(0.0, 100.0),
            maxPeRsi1h = newThresholds.maxPeRsi1h.coerceIn(0.0, 100.0),
            maxPeRsi15m = newThresholds.maxPeRsi15m.coerceIn(0.0, 100.0),
            maxPeRsi5m = newThresholds.maxPeRsi5m.coerceIn(0.0, 100.0),
            maxPeRsi1m = newThresholds.maxPeRsi1m.coerceIn(0.0, 100.0),
            refreshIntervalSec = newThresholds.refreshIntervalSec.coerceIn(5, 120),
        )
        viewModelScope.launch {
            prefsRepo.saveThresholds(sanitized)
        }
        _uiState.update {
            it.copy(
                thresholds = sanitized,
                secondsUntilRefresh = sanitized.refreshIntervalSec,
                showFiltersSheet = false,
            )
        }
        startRefreshLoop(immediateScan = false)
    }

    fun resetDefaultThresholds() {
        updateThresholds(ScannerThresholds())
    }

    fun selectStock(stock: StockScanResult, side: OptionSide, preferredTimeframe: ChartTimeframe? = null) {
        val enriched = enrichWithOiDelivery(stock, latestHistoryMap)
        _uiState.update { state ->
            state.copy(
                selectedStock = enriched,
                selectedSide = side,
                selectedTimeframe = preferredTimeframe ?: state.selectedTimeframe,
                detailNseSyncStatus = nseRepo.getSyncStatusMessage(enriched.symbol),
            )
        }
        syncSingleStockOiDelivery(enriched, forceHistory = true)
    }

    fun selectAlert(alert: RsiCrossAlert) {
        val match = _uiState.value.allStocks.firstOrNull { it.symbol == alert.symbol } ?: return
        selectStock(match, alert.side, alert.timeframe)
    }

    fun selectStockBySymbol(symbol: String, side: OptionSide) {
        val match = _uiState.value.allStocks.firstOrNull { it.symbol == symbol } ?: return
        selectStock(match, side)
    }

    fun refreshSelectedStockOiDelivery() {
        val selected = _uiState.value.selectedStock ?: return
        syncSingleStockOiDelivery(selected, forceHistory = true)
    }

    fun closeStockDetail() {
        _uiState.update {
            it.copy(
                selectedStock = null,
                isSyncingDetailOiDelivery = false,
                detailNseSyncStatus = null,
            )
        }
    }

    fun setChartTimeframe(timeframe: ChartTimeframe) {
        _uiState.update { it.copy(selectedTimeframe = timeframe) }
    }

    fun addWatchlistSymbol(rawSymbol: String) {
        val clean = rawSymbol.trim().uppercase().removeSuffix(".NS").removeSuffix(".BO")
        if (clean.isEmpty()) {
            _uiState.update { it.copy(addSymbolError = "Enter a valid NSE F&O symbol (e.g. TATAMOTORS)") }
            return
        }
        if (_uiState.value.watchlistSymbols.contains(clean)) {
            _uiState.update { it.copy(addSymbolError = "$clean is already in your F&O watchlist") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(addSymbolLoading = true, addSymbolError = null) }
            val result = scannerRepo.validateAndFetchSymbol(clean)
            result.onSuccess { fetched ->
                val updatedSymbols = listOf(clean) + _uiState.value.watchlistSymbols
                prefsRepo.saveSymbols(updatedSymbols)
                val detection = RsiCrossoverDetector.evaluateBatch(
                    stocks = listOf(fetched),
                    previousRsiByKey = previousRsiByKey,
                    activeAlertsById = activeAlertsById,
                )
                val enrichedFetched = enrichWithOiDelivery(detection.enrichedStocks.first(), latestHistoryMap)
                _uiState.update { state ->
                    state.copy(
                        watchlistSymbols = updatedSymbols,
                        allStocks = listOf(enrichedFetched) + state.allStocks.filterNot { it.symbol == fetched.symbol },
                        crossingAlertsFeed = detection.watchAlertsFeed,
                        addSymbolLoading = false,
                        addSymbolError = null,
                    )
                }
                syncSingleStockOiDelivery(enrichedFetched, forceHistory = true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        addSymbolLoading = false,
                        addSymbolError = err.message ?: "Symbol $clean not found on NSE",
                    )
                }
            }
        }
    }

    fun removeWatchlistSymbol(symbol: String) {
        val updated = _uiState.value.watchlistSymbols.filterNot { it == symbol }
        if (updated.isEmpty()) return
        viewModelScope.launch {
            prefsRepo.saveSymbols(updated)
        }
        _uiState.update { state ->
            state.copy(
                watchlistSymbols = updated,
                allStocks = state.allStocks.filterNot { it.symbol == symbol },
            )
        }
    }

    fun resetWatchlistDefaults() {
        val defaults = FnoUniverse.defaultSymbols
        viewModelScope.launch {
            prefsRepo.saveSymbols(defaults)
        }
        _uiState.update { it.copy(watchlistSymbols = defaults, addSymbolError = null) }
        startRefreshLoop(immediateScan = true)
    }

    private fun syncSingleStockOiDelivery(stock: StockScanResult, forceHistory: Boolean) {
        nseBatchJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncingDetailOiDelivery = true,
                    detailNseSyncStatus = "Syncing live NSE option chain...",
                )
            }
            nseRepo.syncSymbol(
                symbol = stock.symbol,
                lastPrice = stock.lastPrice,
                priceChangePct = stock.changePercent,
                tradedQty = stock.sharesVolume,
                forceHistory = forceHistory,
            )
            _uiState.update { state ->
                val updatedAll = state.allStocks.map { enrichWithOiDelivery(it, latestHistoryMap) }
                val updatedSelected = state.selectedStock?.let { sel ->
                    updatedAll.firstOrNull { it.symbol == sel.symbol }
                        ?: enrichWithOiDelivery(sel, latestHistoryMap)
                }
                state.copy(
                    allStocks = updatedAll,
                    selectedStock = updatedSelected,
                    isSyncingDetailOiDelivery = false,
                    detailNseSyncStatus = updatedSelected?.let { nseRepo.getSyncStatusMessage(it.symbol) },
                )
            }
        }
    }

    private fun triggerPositional10sLiveNseSync(
        latestStocks: List<StockScanResult> = _uiState.value.allStocks,
    ) {
        val stateSnapshot = _uiState.value
        if (stateSnapshot.selectedStock != null || stateSnapshot.currentPage != AppDestination.POSITIONAL) return
        if (latestStocks.isEmpty() || positionalLiveJob?.isActive == true || stateSnapshot.isSyncingPositionalBatch) return

        val visibleCandidates = stateSnapshot.filteredPositionalCandidates
            .ifEmpty { PositionalAnalyzer.analyze(latestStocks, stateSnapshot.thresholds) }
        if (visibleCandidates.isEmpty()) return

        val distinctStocks = visibleCandidates
            .map { it.stock }
            .distinctBy { it.symbol }

        // Always refresh top 4 visible setups every 10s, plus rotate 4 more down the list
        val pinnedTop = distinctStocks.take(4)
        val remainder = distinctStocks.drop(4)
        val rotatingSlice = if (remainder.isNotEmpty()) {
            val startIdx = positionalRotationOffset % remainder.size
            val picked = ArrayList<StockScanResult>(minOf(4, remainder.size))
            for (i in 0 until minOf(4, remainder.size)) {
                picked.add(remainder[(startIdx + i) % remainder.size])
            }
            positionalRotationOffset = (startIdx + picked.size) % remainder.size
            picked
        } else {
            emptyList()
        }
        val batchStocks = (pinnedTop + rotatingSlice).distinctBy { it.symbol }
        val payload = batchStocks.map { Triple(it.symbol, it.lastPrice, it.changePercent to it.sharesVolume) }

        positionalLiveJob = viewModelScope.launch {
            _uiState.update { it.copy(isLiveRefreshingPositional = true) }
            try {
                val synced = nseRepo.syncLiveOnlyBatch(payload)
                _uiState.update { state ->
                    val updatedAll = state.allStocks.map { enrichWithOiDelivery(it, latestHistoryMap) }
                    val updatedSelected = state.selectedStock?.let { sel ->
                        updatedAll.firstOrNull { it.symbol == sel.symbol }
                            ?: enrichWithOiDelivery(sel, latestHistoryMap)
                    }
                    state.copy(
                        allStocks = updatedAll,
                        selectedStock = updatedSelected,
                        nseSyncedCount = maxOf(state.nseSyncedCount, synced),
                        isLiveRefreshingPositional = false,
                    )
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLiveRefreshingPositional = false) }
            }
        }
    }

    private fun triggerBackgroundNseSyncIfDue(results: List<StockScanResult>) {
        if (_uiState.value.selectedStock != null) return
        val now = System.currentTimeMillis()
        if ((now - lastNseBatchSyncMs) < 60_000L || nseBatchJob?.isActive == true) {
            return
        }
        lastNseBatchSyncMs = now
        nseBatchJob = viewModelScope.launch {
            val savedSet = _uiState.value.savedWatchlistSymbols
            val prioritized = results.sortedWith(
                compareByDescending<StockScanResult> { it.symbol in savedSet }
                    .thenByDescending {
                        it.isHighPriority(OptionSide.CE) || it.isHighPriority(OptionSide.PE)
                    }
                    .thenByDescending { it.isAboveR2 || it.isBelowS2 }
                    .thenByDescending { kotlin.math.abs(it.changePercent) }
            ).take(10)
            val batchPayload = prioritized.map {
                Triple(it.symbol, it.lastPrice, it.changePercent to it.sharesVolume)
            }
            val synced = nseRepo.syncBatch(batchPayload)
            _uiState.update { state ->
                val updatedAll = state.allStocks.map { enrichWithOiDelivery(it, latestHistoryMap) }
                val updatedSelected = state.selectedStock?.let { sel ->
                    updatedAll.firstOrNull { it.symbol == sel.symbol }
                        ?: enrichWithOiDelivery(sel, latestHistoryMap)
                }
                state.copy(
                    allStocks = updatedAll,
                    selectedStock = updatedSelected,
                    nseSyncedCount = synced,
                )
            }
        }
    }

    private fun enrichWithOiDelivery(
        stock: StockScanResult,
        historyBySymbol: Map<String, List<DailyPositionSnapshot>>,
    ): StockScanResult {
        val live = nseRepo.getCachedLiveSnapshot(stock.symbol)
        val history = historyBySymbol[stock.symbol].orEmpty()
        val summary = PositionSignalEvaluator.buildSummary(
            priceChangePct = stock.changePercent,
            liveOi = live?.openInterest,
            liveOiChange = live?.oiChange,
            liveOiChangePct = live?.oiChangePct,
            livePcr = live?.pcr,
            liveDeliveryQty = live?.deliveryQty,
            liveDeliveryPct = live?.deliveryPct,
            rawHistoryDesc = history,
            liveCallToPutRatio = live?.callToPutRatio,
            livePcrOiChangePct = live?.pcrOiChangePct,
            livePutOiChange = live?.putOiChange,
            liveCallOiChange = live?.callOiChange,
            liveIntradayDeltaPcr = live?.intradayDeltaPcr,
        )
        return stock.copy(
            oiDelivery = summary,
            atmOptions = live?.atmOptions ?: AtmOptionBundle(),
        )
    }

    private fun startRefreshLoop(immediateScan: Boolean) {
        refreshLoopJob?.cancel()
        refreshLoopJob = viewModelScope.launch {
            var shouldScanNow = immediateScan
            while (isActive) {
                if (shouldScanNow) {
                    runSingleScan()
                }
                shouldScanNow = true

                val interval = _uiState.value.thresholds.refreshIntervalSec.coerceIn(5, 120)
                _uiState.update { it.copy(secondsUntilRefresh = interval) }

                for (remaining in interval downTo 1) {
                    _uiState.update { it.copy(secondsUntilRefresh = remaining) }
                    delay(1_000L)
                    while (!_uiState.value.thresholds.autoRefreshEnabled && isActive) {
                        delay(400L)
                    }
                }
            }
        }
    }

    private suspend fun runSingleScan() {
        val savedSet = _uiState.value.savedWatchlistSymbols
        val combinedUniverse = (_uiState.value.savedWatchlistEntries.map { it.symbol.uppercase() } +
            _uiState.value.watchlistSymbols).distinct()
        // Prioritize Room Watchlist stocks first so starred stocks receive fastest updates
        val symbols = combinedUniverse.sortedByDescending { it in savedSet }
        _uiState.update {
            it.copy(
                scanProgress = ScanProgress(completed = 0, total = symbols.size, isScanning = true),
                errorMessage = null,
            )
        }

        val outcome = scannerRepo.scanUniverse(symbols) { completed, total ->
            _uiState.update { state ->
                state.copy(scanProgress = ScanProgress(completed, total, isScanning = true))
            }
        }

        when (outcome) {
            is ScanBatchOutcome.Success -> {
                val formattedTime = istTimeFormatter.format(Instant.ofEpochMilli(outcome.fetchedAtEpochMs)) + " IST"
                val detection = RsiCrossoverDetector.evaluateBatch(
                    stocks = outcome.results,
                    previousRsiByKey = previousRsiByKey,
                    activeAlertsById = activeAlertsById,
                    nowMs = outcome.fetchedAtEpochMs,
                )
                val enrichedResults = detection.enrichedStocks.map { enrichWithOiDelivery(it, latestHistoryMap) }

                val pivotBatch = PivotAlertDetector.evaluateBatch(
                    stocks = enrichedResults,
                    previousCeRankBySymbol = previousCePivotRankBySymbol,
                    previousPeRankBySymbol = previousPePivotRankBySymbol,
                    activeAlertsById = activePivotAlertsById,
                    hasBaseline = hasCompletedInitialBaselineScan,
                    nowMs = outcome.fetchedAtEpochMs,
                )
                val volumeBatch = PivotAlertDetector.evaluateVolumeSurgeBatch(
                    stocks = enrichedResults,
                    previousSurgeSymbols = previousVolumeSurgeSymbols,
                    activeVolumeAlertsById = activeVolumeAlertsById,
                    hasBaseline = hasCompletedInitialBaselineScan,
                    nowMs = outcome.fetchedAtEpochMs,
                )
                val positionalBuzzBatch = PivotAlertDetector.evaluatePositionalVolRsiBuzzBatch(
                    stocks = enrichedResults,
                    previousBuzzSymbols = previousPositionalBuzzSymbols,
                    activeBuzzAlertsById = activePositionalBuzzAlertsById,
                    nowMs = outcome.fetchedAtEpochMs,
                )
                val unifiedAlerts = PivotAlertDetector.buildUnifiedAlerts(
                    pivotAlerts = pivotBatch.activePivotAlerts,
                    rsiAlerts = detection.watchAlertsFeed,
                    volumeSurgeAlerts = volumeBatch.activeVolumeAlerts,
                    positionalBuzzAlerts = positionalBuzzBatch.activeBuzzAlerts,
                )

                // Evaluate sound, vibration buzz & system push alert triggers for Positional Vol>2x+RSI>65, R2/R3, S2/S3 & RSI
                val currentThresholds = _uiState.value.thresholds
                val currentCe5of5 = enrichedResults.filter { it.evaluateCe(currentThresholds).allPassed }.map { it.symbol }.toSet()
                val currentPe5of5 = enrichedResults.filter { it.evaluatePe(currentThresholds).allPassed }.map { it.symbol }.toSet()
                val currentAboveR3 = enrichedResults.filter { it.isAboveR3 }.map { it.symbol }.toSet()
                val freshlyCrossedR3 = if (hasCompletedInitialBaselineScan) {
                    currentAboveR3 - previousAboveR3Symbols
                } else {
                    emptySet()
                }
                val freshPositionalBuzz = positionalBuzzBatch.newlyTriggeredBuzzAlerts.firstOrNull()
                val freshPriorityPivot = pivotBatch.newlyTriggeredAlerts.firstOrNull { it.isPriorityWatch }
                val freshVolumeSurge = volumeBatch.newlyTriggeredVolumeAlerts.firstOrNull()

                var soundBanner: String? = _uiState.value.lastSoundAlertBanner
                var soundPlayedThisTick = false
                if (currentThresholds.soundAlertsEnabled) {
                    val freshCeCross = detection.newlyTriggeredAlerts.firstOrNull { it.side == OptionSide.CE }
                    val freshPeCross = detection.newlyTriggeredAlerts.firstOrNull { it.side == OptionSide.PE }
                    if (freshPositionalBuzz != null) {
                        soundAlertManager.playPositionalVolRsiBuzz()
                        soundBanner = "🔊 POSITIONAL BUZZ: ${freshPositionalBuzz.symbol} • ${freshPositionalBuzz.title}"
                        soundPlayedThisTick = true
                    } else if (freshCeCross != null) {
                        soundAlertManager.playHighPriorityCross(OptionSide.CE)
                        soundBanner = "CE >70 Sound Alert: ${freshCeCross.symbol} (${freshCeCross.timeframe.label} RSI ${String.format(java.util.Locale.US, "%.1f", freshCeCross.rsiValue)})"
                        soundPlayedThisTick = true
                    } else if (freshPeCross != null) {
                        soundAlertManager.playHighPriorityCross(OptionSide.PE)
                        soundBanner = "PE <30 Sound Alert: ${freshPeCross.symbol} (${freshPeCross.timeframe.label} RSI ${String.format(java.util.Locale.US, "%.1f", freshPeCross.rsiValue)})"
                        soundPlayedThisTick = true
                    } else if (freshVolumeSurge != null) {
                        soundAlertManager.playSignalMatchChime(freshVolumeSurge.side)
                        soundBanner = "🔊 ${freshVolumeSurge.symbol} ${freshVolumeSurge.title}"
                        soundPlayedThisTick = true
                    } else if (freshPriorityPivot != null) {
                        val isExtreme3 = freshPriorityPivot.level?.rank == 3
                        if (isExtreme3) {
                            soundAlertManager.playHighPriorityCross(freshPriorityPivot.side)
                        } else {
                            soundAlertManager.playSignalMatchChime(freshPriorityPivot.side)
                        }
                        soundBanner = "⚡ ${freshPriorityPivot.symbol} ${freshPriorityPivot.title}"
                        soundPlayedThisTick = true
                    } else if (hasCompletedInitialBaselineScan && currentThresholds.soundOn5of5Match) {
                        val newlyMatchedCe = (currentCe5of5 - previousCe5of5Symbols).firstOrNull()
                        val newlyMatchedPe = (currentPe5of5 - previousPe5of5Symbols).firstOrNull()
                        if (newlyMatchedCe != null) {
                            soundAlertManager.playSignalMatchChime(OptionSide.CE)
                            soundBanner = "CE 5/5 Match Chime: $newlyMatchedCe"
                            soundPlayedThisTick = true
                        } else if (newlyMatchedPe != null) {
                            soundAlertManager.playSignalMatchChime(OptionSide.PE)
                            soundBanner = "PE 5/5 Match Chime: $newlyMatchedPe"
                            soundPlayedThisTick = true
                        }
                    }
                } else if (freshPositionalBuzz != null) {
                    soundBanner = "🔊 POSITIONAL BUZZ: ${freshPositionalBuzz.symbol} • ${freshPositionalBuzz.title}"
                } else if (freshVolumeSurge != null) {
                    soundBanner = "🔊 ${freshVolumeSurge.symbol} ${freshVolumeSurge.title}"
                } else if (freshPriorityPivot != null && !soundPlayedThisTick) {
                    soundBanner = "⚡ ${freshPriorityPivot.symbol} ${freshPriorityPivot.title}"
                }

                // Post Android push notifications for Positional Vol>2x+RSI>65 Buzz (immediate), plus R2/R3, S2/S3, Vol 2x & RSI
                if (_uiState.value.systemNotificationsEnabled) {
                    positionalBuzzBatch.newlyTriggeredBuzzAlerts
                        .take(3)
                        .forEach { alertNotificationHelper.postBreakoutNotification(it) }
                    if (hasCompletedInitialBaselineScan) {
                        val freshRsiAsLive = PivotAlertDetector.buildUnifiedAlerts(
                            pivotAlerts = emptyList(),
                            rsiAlerts = detection.newlyTriggeredAlerts,
                        )
                        (volumeBatch.newlyTriggeredVolumeAlerts + pivotBatch.newlyTriggeredAlerts.filter { it.isPriorityWatch } + freshRsiAsLive)
                            .take(4)
                            .forEach { alertNotificationHelper.postBreakoutNotification(it) }
                    }
                }

                val newUnreadAdds = if (!_uiState.value.showAlertCenterSheet) {
                    positionalBuzzBatch.newlyTriggeredBuzzAlerts.size +
                        if (hasCompletedInitialBaselineScan) {
                            volumeBatch.newlyTriggeredVolumeAlerts.size +
                                pivotBatch.newlyTriggeredAlerts.count { it.isPriorityWatch } +
                                detection.newlyTriggeredAlerts.size
                        } else {
                            0
                        }
                } else {
                    0
                }

                val nowMs = outcome.fetchedAtEpochMs
                val liveEventsSnapshot = synchronized(recordedLiveRsiTriggers) {
                    detection.newlyTriggeredAlerts.forEach {
                        recordedLiveRsiTriggers.add(RsiFrequencyAnalyzer.fromAlert(it))
                    }
                    recordedLiveRsiTriggers.removeAll { (nowMs - it.timestampEpochMs) > RsiFrequencyAnalyzer.WINDOW_MS }
                    recordedLiveRsiTriggers.toList()
                }
                val freqStats = RsiFrequencyAnalyzer.analyze(
                    stocks = enrichedResults,
                    liveEvents = liveEventsSnapshot,
                    nowEpochMs = nowMs,
                )

                previousCe5of5Symbols = currentCe5of5
                previousPe5of5Symbols = currentPe5of5
                previousAboveR3Symbols = currentAboveR3
                hasCompletedInitialBaselineScan = true

                _uiState.update { state ->
                    val updatedSelected = state.selectedStock?.let { currentSel ->
                        enrichedResults.firstOrNull { it.symbol == currentSel.symbol } ?: currentSel
                    }
                    val retainedNewR3 = if (freshlyCrossedR3.isNotEmpty()) {
                        freshlyCrossedR3
                    } else {
                        state.newlyCrossedR3Symbols.intersect(currentAboveR3)
                    }
                    val activePrioritySymbols = enrichedResults
                        .filter { it.isAboveR2 || it.isBelowS2 }
                        .map { it.symbol }
                        .toSet()
                    val retainedPrioritySymbols = if (pivotBatch.newlyCrossedPrioritySymbols.isNotEmpty()) {
                        pivotBatch.newlyCrossedPrioritySymbols
                    } else {
                        state.newlyCrossedPriorityPivotSymbols.intersect(activePrioritySymbols)
                    }
                    val shouldReopenBanner = pivotBatch.newlyCrossedPrioritySymbols.isNotEmpty()
                    state.copy(
                        isLoadingInitial = false,
                        errorMessage = null,
                        allStocks = enrichedResults,
                        crossingAlertsFeed = detection.watchAlertsFeed,
                        pivotAlertsFeed = pivotBatch.activePivotAlerts,
                        positionalBuzzAlertsFeed = positionalBuzzBatch.activeBuzzAlerts,
                        unifiedAlertsFeed = unifiedAlerts,
                        rsiFrequencyStats = freqStats,
                        unreadAlertCount = (state.unreadAlertCount + newUnreadAdds).coerceAtMost(99),
                        scanProgress = ScanProgress(symbols.size, symbols.size, isScanning = false),
                        lastScanTimeIst = formattedTime,
                        failedCount = outcome.failedSymbolsCount,
                        selectedStock = updatedSelected,
                        lastSoundAlertBanner = soundBanner,
                        newlyCrossedR3Symbols = retainedNewR3,
                        newlyCrossedPriorityPivotSymbols = retainedPrioritySymbols,
                        r3NotificationDismissed = if (shouldReopenBanner) false else state.r3NotificationDismissed,
                    )
                }

                // Refresh focused stock's live ATM Option running price on every 10s scan tick
                _uiState.value.selectedStock?.let { focused ->
                    viewModelScope.launch {
                        nseRepo.refreshLiveDerivativeOnly(focused.symbol, focused.lastPrice)
                        _uiState.update { state ->
                            val refreshedAll = state.allStocks.map { enrichWithOiDelivery(it, latestHistoryMap) }
                            val refreshedSel = state.selectedStock?.let { sel ->
                                refreshedAll.firstOrNull { it.symbol == sel.symbol }
                                    ?: enrichWithOiDelivery(sel, latestHistoryMap)
                            }
                            state.copy(
                                allStocks = refreshedAll,
                                selectedStock = refreshedSel,
                                detailNseSyncStatus = refreshedSel?.let { nseRepo.getSyncStatusMessage(it.symbol) },
                            )
                        }
                    }
                }

                if (_uiState.value.currentPage == AppDestination.POSITIONAL) {
                    triggerPositional10sLiveNseSync(enrichedResults)
                } else {
                    triggerBackgroundNseSyncIfDue(enrichedResults)
                }
            }
            is ScanBatchOutcome.Failure -> {
                _uiState.update { state ->
                    state.copy(
                        isLoadingInitial = false,
                        errorMessage = outcome.message,
                        scanProgress = ScanProgress(0, symbols.size, isScanning = false),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundAlertManager.release()
        browserClient.release()
    }
}
