package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.universe.FnoUniverse
import com.example.domain.model.BoardViewMode
import com.example.domain.model.ScannerThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scannerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fno_gapboard_prefs",
)

data class PersistedScannerConfig(
    val thresholds: ScannerThresholds = ScannerThresholds(),
    val boardViewMode: BoardViewMode = BoardViewMode.DUAL_SPLIT,
    val matchedOnly: Boolean = false,
    val symbols: List<String> = FnoUniverse.defaultSymbols,
)

class ScannerPreferencesRepository(private val context: Context) {

    private object Keys {
        val MIN_RVOL = doublePreferencesKey("min_rvol")
        val MIN_SHARES = longPreferencesKey("min_shares")
        val MIN_RSI_1H = doublePreferencesKey("min_rsi_1h")
        val MIN_RSI_15M = doublePreferencesKey("min_rsi_15m")
        val MIN_RSI_5M = doublePreferencesKey("min_rsi_5m")
        val MIN_RSI_1M = doublePreferencesKey("min_rsi_1m")
        val REQUIRE_1M_ALIGNMENT = booleanPreferencesKey("require_1m_alignment")
        val MIRROR_PE_RSI = booleanPreferencesKey("mirror_pe_rsi")
        val MAX_PE_RSI_1H = doublePreferencesKey("max_pe_rsi_1h")
        val MAX_PE_RSI_15M = doublePreferencesKey("max_pe_rsi_15m")
        val MAX_PE_RSI_5M = doublePreferencesKey("max_pe_rsi_5m")
        val MAX_PE_RSI_1M = doublePreferencesKey("max_pe_rsi_1m")
        val REFRESH_SEC = intPreferencesKey("refresh_interval_sec")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh_enabled")
        val SOUND_ALERTS_ENABLED = booleanPreferencesKey("sound_alerts_enabled")
        val SOUND_ON_5OF5_MATCH = booleanPreferencesKey("sound_on_5of5_match")
        val BOARD_VIEW_MODE = stringPreferencesKey("board_view_mode")
        val MATCHED_ONLY = booleanPreferencesKey("matched_only")
        val WATCHLIST_SYMBOLS = stringPreferencesKey("watchlist_symbols")
    }

    val configFlow: Flow<PersistedScannerConfig> = context.scannerDataStore.data.map { prefs ->
        val thresholds = ScannerThresholds(
            minRvol = prefs[Keys.MIN_RVOL] ?: 1.5,
            minShares = prefs[Keys.MIN_SHARES] ?: 50_000L,
            minRsi1h = prefs[Keys.MIN_RSI_1H] ?: 60.0,
            minRsi15m = prefs[Keys.MIN_RSI_15M] ?: 60.0,
            minRsi5m = prefs[Keys.MIN_RSI_5M] ?: 65.0,
            minRsi1m = prefs[Keys.MIN_RSI_1M] ?: 70.0,
            require1mAlignment = prefs[Keys.REQUIRE_1M_ALIGNMENT] ?: true,
            mirrorPeRsi = prefs[Keys.MIRROR_PE_RSI] ?: true,
            maxPeRsi1h = prefs[Keys.MAX_PE_RSI_1H] ?: 40.0,
            maxPeRsi15m = prefs[Keys.MAX_PE_RSI_15M] ?: 40.0,
            maxPeRsi5m = prefs[Keys.MAX_PE_RSI_5M] ?: 35.0,
            maxPeRsi1m = prefs[Keys.MAX_PE_RSI_1M] ?: 30.0,
            refreshIntervalSec = (prefs[Keys.REFRESH_SEC] ?: 10).coerceIn(5, 120),
            autoRefreshEnabled = prefs[Keys.AUTO_REFRESH] ?: true,
            soundAlertsEnabled = prefs[Keys.SOUND_ALERTS_ENABLED] ?: true,
            soundOn5of5Match = prefs[Keys.SOUND_ON_5OF5_MATCH] ?: true,
        )

        val modeName = prefs[Keys.BOARD_VIEW_MODE]
        val mode = BoardViewMode.entries.firstOrNull { it.name == modeName } ?: BoardViewMode.DUAL_SPLIT

        val rawSymbols = prefs[Keys.WATCHLIST_SYMBOLS]
        val symbols = if (rawSymbols.isNullOrBlank()) {
            FnoUniverse.defaultSymbols
        } else {
            rawSymbols.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { FnoUniverse.defaultSymbols }
        }

        PersistedScannerConfig(
            thresholds = thresholds,
            boardViewMode = mode,
            matchedOnly = prefs[Keys.MATCHED_ONLY] ?: false,
            symbols = symbols,
        )
    }

    suspend fun saveThresholds(thresholds: ScannerThresholds) {
        context.scannerDataStore.edit { prefs ->
            prefs[Keys.MIN_RVOL] = thresholds.minRvol
            prefs[Keys.MIN_SHARES] = thresholds.minShares
            prefs[Keys.MIN_RSI_1H] = thresholds.minRsi1h
            prefs[Keys.MIN_RSI_15M] = thresholds.minRsi15m
            prefs[Keys.MIN_RSI_5M] = thresholds.minRsi5m
            prefs[Keys.MIN_RSI_1M] = thresholds.minRsi1m
            prefs[Keys.REQUIRE_1M_ALIGNMENT] = thresholds.require1mAlignment
            prefs[Keys.MIRROR_PE_RSI] = thresholds.mirrorPeRsi
            prefs[Keys.MAX_PE_RSI_1H] = thresholds.maxPeRsi1h
            prefs[Keys.MAX_PE_RSI_15M] = thresholds.maxPeRsi15m
            prefs[Keys.MAX_PE_RSI_5M] = thresholds.maxPeRsi5m
            prefs[Keys.MAX_PE_RSI_1M] = thresholds.maxPeRsi1m
            prefs[Keys.REFRESH_SEC] = thresholds.refreshIntervalSec.coerceIn(5, 120)
            prefs[Keys.AUTO_REFRESH] = thresholds.autoRefreshEnabled
            prefs[Keys.SOUND_ALERTS_ENABLED] = thresholds.soundAlertsEnabled
            prefs[Keys.SOUND_ON_5OF5_MATCH] = thresholds.soundOn5of5Match
        }
    }

    suspend fun setAutoRefresh(enabled: Boolean) {
        context.scannerDataStore.edit { prefs ->
            prefs[Keys.AUTO_REFRESH] = enabled
        }
    }

    suspend fun setSoundAlertsEnabled(enabled: Boolean) {
        context.scannerDataStore.edit { prefs ->
            prefs[Keys.SOUND_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setBoardViewMode(mode: BoardViewMode) {
        context.scannerDataStore.edit { prefs ->
            prefs[Keys.BOARD_VIEW_MODE] = mode.name
        }
    }

    suspend fun setMatchedOnly(matchedOnly: Boolean) {
        context.scannerDataStore.edit { prefs ->
            prefs[Keys.MATCHED_ONLY] = matchedOnly
        }
    }

    suspend fun saveSymbols(symbols: List<String>) {
        context.scannerDataStore.edit { prefs ->
            prefs[Keys.WATCHLIST_SYMBOLS] = symbols.joinToString(",")
        }
    }
}
