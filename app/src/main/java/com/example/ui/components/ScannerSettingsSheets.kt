package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.WatchlistStockEntity
import com.example.data.universe.FnoUniverse
import com.example.domain.indicator.TechnicalIndicators
import com.example.domain.model.OptionSide
import com.example.domain.model.ScannerThresholds
import com.example.domain.model.StockScanResult
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAccentBlueContainer
import com.example.ui.theme.TvAmberContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextBright
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.theme.TvTrendBearRed
import com.example.ui.theme.TvTrendBullGreen
import com.example.ui.theme.TvWarningAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerFiltersBottomSheet(
    current: ScannerThresholds,
    onApply: (ScannerThresholds) -> Unit,
    onResetDefaults: () -> Unit,
    onTestSound: (OptionSide) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rvolText by remember(current) { mutableStateOf(current.minRvol.toString()) }
    var sharesText by remember(current) { mutableStateOf(current.minShares.toString()) }
    var rsi1hText by remember(current) { mutableStateOf(current.minRsi1h.toInt().toString()) }
    var rsi15mText by remember(current) { mutableStateOf(current.minRsi15m.toInt().toString()) }
    var rsi5mText by remember(current) { mutableStateOf(current.minRsi5m.toInt().toString()) }
    var rsi1mText by remember(current) { mutableStateOf(current.minRsi1m.toInt().toString()) }
    var require1mAlignment by remember(current) { mutableStateOf(current.require1mAlignment) }
    var mirrorPe by remember(current) { mutableStateOf(current.mirrorPeRsi) }
    var peRsi1hText by remember(current) { mutableStateOf(current.maxPeRsi1h.toInt().toString()) }
    var peRsi15mText by remember(current) { mutableStateOf(current.maxPeRsi15m.toInt().toString()) }
    var peRsi5mText by remember(current) { mutableStateOf(current.maxPeRsi5m.toInt().toString()) }
    var peRsi1mText by remember(current) { mutableStateOf(current.maxPeRsi1m.toInt().toString()) }
    var refreshSec by remember(current) { mutableIntStateOf(current.refreshIntervalSec) }
    var soundEnabled by remember(current) { mutableStateOf(current.soundAlertsEnabled) }
    var sound5of5 by remember(current) { mutableStateOf(current.soundOn5of5Match) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Scanner Rules & Auto-Refresh",
                        style = MaterialTheme.typography.titleMedium,
                        color = TvTextBright,
                    )
                    Text(
                        text = "Configure CE/PE momentum thresholds & scan cadence",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextSecondary,
                    )
                }
                TextButton(onClick = onResetDefaults) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        tint = TvAccentBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Defaults", color = TvAccentBlue)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = TvDivider)

            // Volume & RVOL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThresholdField(
                    label = "RVOL >",
                    value = rvolText,
                    onValueChange = { rvolText = it },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                ThresholdField(
                    label = "Shares >",
                    value = sharesText,
                    onValueChange = { sharesText = it },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }

            // CE Bullish RSI thresholds
            Text(
                text = "CE Bullish RSI Thresholds (greater than)",
                style = MaterialTheme.typography.titleSmall,
                color = TvBullTeal,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThresholdField(
                    label = "1h RSI >",
                    value = rsi1hText,
                    onValueChange = { rsi1hText = it },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                ThresholdField(
                    label = "15m RSI >",
                    value = rsi15mText,
                    onValueChange = { rsi15mText = it },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                ThresholdField(
                    label = "5m RSI >",
                    value = rsi5mText,
                    onValueChange = { rsi5mText = it },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                ThresholdField(
                    label = "1m RSI >",
                    value = rsi1mText,
                    onValueChange = { rsi1mText = it },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }

            // 1m Alignment Toggle
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, if (require1mAlignment) TvAccentBlue.copy(alpha = 0.6f) else TvDivider),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require 1m RSI Alignment",
                            style = MaterialTheme.typography.titleSmall,
                            color = TvTextBright,
                        )
                        Text(
                            text = "Confirm 5m momentum with 1m RSI (>70 CE / <30 PE)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextSecondary,
                        )
                    }
                    Switch(
                        checked = require1mAlignment,
                        onCheckedChange = { require1mAlignment = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TvTextBright,
                            checkedTrackColor = TvAccentBlue,
                        ),
                        modifier = Modifier.testTag("require_1m_alignment_switch"),
                    )
                }
            }

            // PE Bearish Mirror
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvDivider),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PE Bearish Mirror RSI",
                                style = MaterialTheme.typography.titleSmall,
                                color = TvBearCoral,
                            )
                            Text(
                                text = if (mirrorPe) {
                                    "Auto-mirrors CE via 100 - RSI (<${100 - (rsi1hText.toIntOrNull() ?: 60)} / <${100 - (rsi15mText.toIntOrNull() ?: 60)} / <${100 - (rsi5mText.toIntOrNull() ?: 65)} / 1m <${100 - (rsi1mText.toIntOrNull() ?: 70)})"
                                } else {
                                    "Custom PE upper RSI bounds"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TvTextSecondary,
                            )
                        }
                        Switch(
                            checked = mirrorPe,
                            onCheckedChange = { mirrorPe = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TvTextBright,
                                checkedTrackColor = TvAccentBlue,
                            ),
                        )
                    }

                    if (!mirrorPe) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThresholdField(
                                label = "1h RSI <",
                                value = peRsi1hText,
                                onValueChange = { peRsi1hText = it },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                            ThresholdField(
                                label = "15m RSI <",
                                value = peRsi15mText,
                                onValueChange = { peRsi15mText = it },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                            ThresholdField(
                                label = "5m RSI <",
                                value = peRsi5mText,
                                onValueChange = { peRsi5mText = it },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                            ThresholdField(
                                label = "1m RSI <",
                                value = peRsi1mText,
                                onValueChange = { peRsi1mText = it },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Refresh Cadence
            Text(
                text = "Auto-Refresh Cadence",
                style = MaterialTheme.typography.titleSmall,
                color = TvTextBright,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 10, 15, 30, 60).forEach { sec ->
                    val selected = refreshSec == sec
                    Surface(
                        onClick = { refreshSec = sec },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) TvAccentBlueContainer else TvBackground,
                        border = BorderStroke(1.dp, if (selected) TvAccentBlue else TvDivider),
                    ) {
                        Text(
                            text = "${sec}s",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) TvTextBright else TvTextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Sound Alerts Configuration
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvDivider),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = if (soundEnabled) TvTrendBullGreen else TvTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Live Sound Alerts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TvTextBright,
                                )
                                Text(
                                    text = "Play chime when RSI crosses >70 (CE) or <30 (PE)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TvTextSecondary,
                                )
                            }
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TvTextBright,
                                checkedTrackColor = TvTrendBullGreen,
                            ),
                            modifier = Modifier.testTag("sound_alerts_switch"),
                        )
                    }

                    if (soundEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Chime on new 5/5 rule matches",
                                style = MaterialTheme.typography.bodySmall,
                                color = TvTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = sound5of5,
                                onCheckedChange = { sound5of5 = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TvTextBright,
                                    checkedTrackColor = TvAccentBlue,
                                ),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onTestSound(OptionSide.CE) },
                                border = BorderStroke(1.dp, TvTrendBullGreen.copy(alpha = 0.65f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("test_ce_sound_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = TvTrendBullGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test CE Chime", color = TvTrendBullGreen, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { onTestSound(OptionSide.PE) },
                                border = BorderStroke(1.dp, TvTrendBearRed.copy(alpha = 0.65f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("test_pe_sound_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = TvTrendBearRed,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test PE Alert", color = TvTrendBearRed, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val next = current.copy(
                        minRvol = rvolText.toDoubleOrNull() ?: current.minRvol,
                        minShares = sharesText.toLongOrNull() ?: current.minShares,
                        minRsi1h = rsi1hText.toDoubleOrNull() ?: current.minRsi1h,
                        minRsi15m = rsi15mText.toDoubleOrNull() ?: current.minRsi15m,
                        minRsi5m = rsi5mText.toDoubleOrNull() ?: current.minRsi5m,
                        minRsi1m = rsi1mText.toDoubleOrNull() ?: current.minRsi1m,
                        require1mAlignment = require1mAlignment,
                        mirrorPeRsi = mirrorPe,
                        maxPeRsi1h = peRsi1hText.toDoubleOrNull() ?: current.maxPeRsi1h,
                        maxPeRsi15m = peRsi15mText.toDoubleOrNull() ?: current.maxPeRsi15m,
                        maxPeRsi5m = peRsi5mText.toDoubleOrNull() ?: current.maxPeRsi5m,
                        maxPeRsi1m = peRsi1mText.toDoubleOrNull() ?: current.maxPeRsi1m,
                        refreshIntervalSec = refreshSec,
                        soundAlertsEnabled = soundEnabled,
                        soundOn5of5Match = sound5of5,
                    )
                    onApply(next)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_filters_button"),
                colors = ButtonDefaults.buttonColors(containerColor = TvAccentBlue),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = "Apply Scanner Rules",
                    style = MaterialTheme.typography.titleSmall,
                    color = TvTextBright,
                )
            }
        }
    }
}

@Composable
private fun ThresholdField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.labelLarge.copy(color = TvTextBright),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TvAccentBlue,
            unfocusedBorderColor = TvDivider,
            focusedContainerColor = TvBackground,
            unfocusedContainerColor = TvBackground,
            focusedLabelColor = TvAccentBlue,
            unfocusedLabelColor = TvTextSecondary,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistManagerBottomSheet(
    symbols: List<String>,
    savedWatchlistEntries: List<WatchlistStockEntity> = emptyList(),
    allStocks: List<StockScanResult> = emptyList(),
    isAdding: Boolean,
    addError: String?,
    onAddSymbol: (String) -> Unit,
    onRemoveSymbol: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onToggleSaveSymbol: (String) -> Unit = {},
    onSelectWatchlistEntry: (WatchlistStockEntity) -> Unit = {},
    onRemoveSavedWatchlist: (String) -> Unit = {},
    onClearSavedWatchlist: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputSymbol by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = My Room Watchlist, 1 = Scanned Universe
    val savedSymbolsSet = remember(savedWatchlistEntries) {
        savedWatchlistEntries.map { it.symbol.uppercase() }.toSet()
    }
    val stockBySymbol = remember(allStocks) {
        allStocks.associateBy { it.symbol.uppercase() }
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeTab == 0) {
                            "Saved F&O Watchlist (${savedWatchlistEntries.size})"
                        } else {
                            "NSE F&O Universe (${symbols.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TvTextBright,
                    )
                    Text(
                        text = if (activeTab == 0) {
                            "Stored locally via Room DB • Scanned & synced first"
                        } else {
                            "Add or star NSE F&O stocks scanned every cycle"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextSecondary,
                    )
                }
                if (activeTab == 0) {
                    if (savedWatchlistEntries.isNotEmpty()) {
                        TextButton(onClick = onClearSavedWatchlist) {
                            Text("Clear All", color = TvBearCoral)
                        }
                    }
                } else {
                    TextButton(onClick = onResetDefaults) {
                        Text("Reset", color = TvAccentBlue)
                    }
                }
            }

            // Tab Switcher: Saved Watchlist | Scanned Universe
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvBackground)
                    .border(1.dp, TvDivider, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val isWatchlistTab = activeTab == 0
                Surface(
                    onClick = { activeTab = 0 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isWatchlistTab) TvAmberContainer else TvSurface.copy(alpha = 0f),
                    border = BorderStroke(
                        0.75.dp,
                        if (isWatchlistTab) TvWarningAmber.copy(alpha = 0.7f) else TvSurface.copy(alpha = 0f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_my_watchlist"),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = if (isWatchlistTab) TvWarningAmber else TvTextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Watchlist (${savedWatchlistEntries.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isWatchlistTab) TvWarningAmber else TvTextSecondary,
                        )
                    }
                }
                Surface(
                    onClick = { activeTab = 1 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (!isWatchlistTab) TvAccentBlueContainer else TvSurface.copy(alpha = 0f),
                    border = BorderStroke(
                        0.75.dp,
                        if (!isWatchlistTab) TvAccentBlue.copy(alpha = 0.7f) else TvSurface.copy(alpha = 0f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_scanned_universe"),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "F&O Universe (${symbols.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (!isWatchlistTab) TvTextBright else TvTextSecondary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputSymbol,
                    onValueChange = { inputSymbol = it.uppercase() },
                    placeholder = {
                        Text(
                            if (activeTab == 0) "Save symbol to Watchlist (e.g. MCX)" else "Add NSE symbol (e.g. TATAMOTORS)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextSecondary,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.labelLarge.copy(color = TvTextBright),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TvAccentBlue,
                        unfocusedBorderColor = TvDivider,
                        focusedContainerColor = TvBackground,
                        unfocusedContainerColor = TvBackground,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_symbol_input"),
                )
                Button(
                    onClick = {
                        if (inputSymbol.isNotBlank()) {
                            if (activeTab == 0) {
                                onToggleSaveSymbol(inputSymbol)
                            } else {
                                onAddSymbol(inputSymbol)
                            }
                            inputSymbol = ""
                        }
                    },
                    enabled = !isAdding && inputSymbol.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == 0) TvWarningAmber else TvAccentBlue,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("add_symbol_button"),
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TvTextBright,
                        )
                    } else {
                        Icon(
                            imageVector = if (activeTab == 0) Icons.Rounded.Star else Icons.Rounded.Add,
                            contentDescription = if (activeTab == 0) stringResource(R.string.cd_add_to_watchlist) else "Add symbol",
                            tint = if (activeTab == 0) TvBackground else TvTextBright,
                        )
                    }
                }
            }

            if (!addError.isNullOrBlank()) {
                Text(
                    text = addError,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvBearCoral,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = TvDivider)

            if (activeTab == 0) {
                if (savedWatchlistEntries.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TvBackground,
                        border = BorderStroke(1.dp, TvDivider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_watchlist_placeholder"),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.StarBorder,
                                contentDescription = null,
                                tint = TvWarningAmber,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = "Your F&O Watchlist is empty",
                                style = MaterialTheme.typography.titleSmall,
                                color = TvTextBright,
                            )
                            Text(
                                text = "Tap ★ on any stock row, chart sheet, or type a symbol above to save it locally in Room for priority tracking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TvTextSecondary,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(savedWatchlistEntries, key = { it.symbol }) { entry ->
                            val live = stockBySymbol[entry.symbol.uppercase()]
                            val isCe = entry.preferredSide == OptionSide.CE.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TvBackground)
                                    .border(1.dp, TvWarningAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable { onSelectWatchlistEntry(entry) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("saved_watchlist_row_${entry.symbol}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = entry.symbol,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TvTextBright,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = TvSurfaceElevated,
                                        ) {
                                            Text(
                                                text = entry.preferredSide,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isCe) TvBullTeal else TvBearCoral,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${entry.companyName} • ${entry.sector}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvTextSecondary,
                                    )
                                }
                                if (live != null) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.padding(end = 6.dp),
                                    ) {
                                        Text(
                                            text = "₹${TechnicalIndicators.formatPrice(live.lastPrice)}",
                                            style = TabularNumberStyle.copy(fontWeight = FontWeight.SemiBold),
                                            color = TvTextBright,
                                        )
                                        Text(
                                            text = TechnicalIndicators.formatPercent(live.changePercent),
                                            style = TabularNumberStyle.copy(fontWeight = FontWeight.Bold),
                                            color = if (live.changePercent >= 0.0) TvTrendBullGreen else TvTrendBearRed,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveSavedWatchlist(entry.symbol) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("remove_saved_watchlist_${entry.symbol}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = stringResource(R.string.cd_remove_from_watchlist),
                                        tint = TvWarningAmber,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(symbols, key = { it }) { sym ->
                        val meta = FnoUniverse.resolveMeta(sym)
                        val isSaved = sym.uppercase() in savedSymbolsSet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TvBackground)
                                .border(1.dp, TvDivider, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sym,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TvTextBright,
                                )
                                Text(
                                    text = "${meta.companyName} • ${meta.sector}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TvTextSecondary,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isSaved) {
                                            onRemoveSavedWatchlist(sym)
                                        } else {
                                            onToggleSaveSymbol(sym)
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = stringResource(
                                            if (isSaved) R.string.cd_remove_from_watchlist else R.string.cd_add_to_watchlist,
                                        ),
                                        tint = if (isSaved) TvWarningAmber else TvTextSecondary,
                                    )
                                }
                                if (symbols.size > 1) {
                                    IconButton(
                                        onClick = { onRemoveSymbol(sym) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.cd_remove_symbol),
                                            tint = TvTextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = BorderStroke(1.dp, TvDivider),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = TvTextPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Done", color = TvTextPrimary)
            }
        }
    }
}
