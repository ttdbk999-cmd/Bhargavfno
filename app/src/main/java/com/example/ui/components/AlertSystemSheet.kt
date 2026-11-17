package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AlertCategory
import com.example.domain.model.LiveScannerAlert
import com.example.domain.model.OptionSide
import com.example.domain.model.PivotWatchLevel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TvAccentBlue
import com.example.ui.theme.TvAccentBlueContainer
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBearContainer
import com.example.ui.theme.TvBearCoral
import com.example.ui.theme.TvBullContainer
import com.example.ui.theme.TvBullTeal
import com.example.ui.theme.TvDivider
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

private enum class AlertTabFilter(val label: String) {
    ALL("All"),
    POSITIONAL_VOL_RSI("Pos Vol>2+65 🔊"),
    VOL_SURGE("Vol 2x (20D)"),
    R2_R3("R2 & R3"),
    S2_S3("S2 & S3"),
    RSI("RSI >70/<30"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSystemBottomSheet(
    alerts: List<LiveScannerAlert>,
    ceAboveR1Count: Int,
    ceAboveR2Count: Int,
    ceAboveR3Count: Int,
    peBelowS1Count: Int,
    peBelowS2Count: Int,
    peBelowS3Count: Int,
    soundAlertsEnabled: Boolean,
    systemNotificationsEnabled: Boolean,
    onToggleSoundAlerts: () -> Unit,
    onToggleSystemNotifications: (Boolean) -> Unit,
    onTestSound: (OptionSide) -> Unit,
    onTestPositionalBuzz: () -> Unit = {},
    onFilterR2R3: () -> Unit,
    onFilterS2S3: () -> Unit,
    onClearAlerts: () -> Unit,
    onSelectAlert: (LiveScannerAlert) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(AlertTabFilter.ALL) }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionDeniedNote = false
            onToggleSystemNotifications(true)
        } else {
            permissionDeniedNote = true
            onToggleSystemNotifications(false)
        }
    }

    val filteredAlerts = remember(alerts, selectedTab) {
        when (selectedTab) {
            AlertTabFilter.ALL -> alerts
            AlertTabFilter.POSITIONAL_VOL_RSI -> alerts.filter { it.category == AlertCategory.POSITIONAL_VOL_RSI }
            AlertTabFilter.VOL_SURGE -> alerts.filter { it.category == AlertCategory.VOLUME_SURGE }
            AlertTabFilter.R2_R3 -> alerts.filter {
                it.level == PivotWatchLevel.ABOVE_R2 || it.level == PivotWatchLevel.ABOVE_R3
            }
            AlertTabFilter.S2_S3 -> alerts.filter {
                it.level == PivotWatchLevel.BELOW_S2 || it.level == PivotWatchLevel.BELOW_S3
            }
            AlertTabFilter.RSI -> alerts.filter { it.category == AlertCategory.RSI_CROSS }
        }
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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = TvWarningAmber,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Pivot & Momentum Alert System",
                            style = MaterialTheme.typography.titleMedium,
                            color = TvTextBright,
                        )
                        Text(
                            text = "Watching R1-R3, S1-S3, RSI >70/<30 & 2x 20D Volume Surge",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvTextSecondary,
                        )
                    }
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

            // Pivot Level Watch Summary Grid (R1..R3 vs S1..S3)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvDivider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PivotLevelStatTile("R1", ceAboveR1Count, isPriority = false, isCe = true, Modifier.weight(1f)) {
                            onFilterR2R3()
                            onDismiss()
                        }
                        PivotLevelStatTile("R2 ★", ceAboveR2Count, isPriority = true, isCe = true, Modifier.weight(1f)) {
                            onFilterR2R3()
                            onDismiss()
                        }
                        PivotLevelStatTile("R3 ★", ceAboveR3Count, isPriority = true, isCe = true, Modifier.weight(1f)) {
                            onFilterR2R3()
                            onDismiss()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PivotLevelStatTile("S1", peBelowS1Count, isPriority = false, isCe = false, Modifier.weight(1f)) {
                            onFilterS2S3()
                            onDismiss()
                        }
                        PivotLevelStatTile("S2 ★", peBelowS2Count, isPriority = true, isCe = false, Modifier.weight(1f)) {
                            onFilterS2S3()
                            onDismiss()
                        }
                        PivotLevelStatTile("S3 ★", peBelowS3Count, isPriority = true, isCe = false, Modifier.weight(1f)) {
                            onFilterS2S3()
                            onDismiss()
                        }
                    }
                }
            }

            // Delivery Channels (Sound + Android Push Notification)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvDivider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (soundAlertsEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                contentDescription = null,
                                tint = if (soundAlertsEnabled) TvTrendBullGreen else TvTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Audio Chimes (R2/R3, S2/S3 & RSI)", style = MaterialTheme.typography.labelLarge, color = TvTextBright)
                                Text("Plays CE ascending / PE warning tone on crosses", style = MaterialTheme.typography.labelSmall, color = TvTextSecondary)
                            }
                        }
                        Switch(
                            checked = soundAlertsEnabled,
                            onCheckedChange = { onToggleSoundAlerts() },
                            colors = SwitchDefaults.colors(checkedThumbColor = TvTextBright, checkedTrackColor = TvTrendBullGreen),
                            modifier = Modifier.testTag("alert_sheet_sound_switch"),
                        )
                    }

                    HorizontalDivider(thickness = 0.75.dp, color = TvDivider)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Android Push Notifications", style = MaterialTheme.typography.labelLarge, color = TvTextBright)
                            Text(
                                text = if (permissionDeniedNote) {
                                    "Notification permission denied; using in-app banner & audio"
                                } else {
                                    "System alerts on 2x 20D Vol Surge, R2/R3, S2/S3 & RSI"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (permissionDeniedNote) TvBearCoral else TvTextSecondary,
                            )
                        }
                        Switch(
                            checked = systemNotificationsEnabled,
                            onCheckedChange = { wantEnable ->
                                if (!wantEnable) {
                                    onToggleSystemNotifications(false)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleSystemNotifications(true)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = TvTextBright, checkedTrackColor = TvAccentBlue),
                            modifier = Modifier.testTag("alert_sheet_push_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onTestSound(OptionSide.CE) },
                            border = BorderStroke(1.dp, TvTrendBullGreen.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                        ) {
                            Text("Test R2/R3 CE", color = TvTrendBullGreen, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onTestSound(OptionSide.PE) },
                            border = BorderStroke(1.dp, TvTrendBearRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                        ) {
                            Text("Test S2/S3 PE", color = TvTrendBearRed, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = onTestPositionalBuzz,
                        border = BorderStroke(1.dp, TvWarningAmber.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("test_positional_buzz_button"),
                    ) {
                        Text(
                            text = "🔊 Test Positional Buzz (Vol >2x + RSI >65)",
                            color = TvWarningAmber,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }

            // Tab Filter Bar + Clear Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AlertTabFilter.entries.forEach { tab ->
                        val selected = tab == selectedTab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) TvAccentBlueContainer else TvBackground,
                            border = BorderStroke(1.dp, if (selected) TvAccentBlue else TvDivider),
                            modifier = Modifier.testTag("alert_tab_${tab.name.lowercase()}"),
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) TvTextBright else TvTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                if (alerts.isNotEmpty()) {
                    TextButton(onClick = onClearAlerts) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            tint = TvTextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Clear", color = TvTextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Live Alert Feed
            if (filteredAlerts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TvBackground,
                    border = BorderStroke(1.dp, TvDivider),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No alerts in this category yet. Monitoring R1/R2/R3, S1/S2/S3, and RSI every 10 seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextSecondary,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = filteredAlerts,
                        key = { it.id },
                    ) { alert ->
                        LiveAlertRowCard(
                            alert = alert,
                            onClick = { onSelectAlert(alert) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PivotLevelStatTile(
    label: String,
    count: Int,
    isPriority: Boolean,
    isCe: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (isCe) {
        if (isPriority) TvTrendBullGreen else TvBullTeal
    } else {
        if (isPriority) TvTrendBearRed else TvBearCoral
    }
    val bg = if (isCe) TvTrendBullBg else TvTrendBearBg
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = if (count > 0) bg.copy(alpha = if (isPriority) 0.85f else 0.45f) else TvBackground,
        border = BorderStroke(
            width = if (isPriority) 1.dp else 0.75.dp,
            color = if (count > 0 || isPriority) accent.copy(alpha = if (count > 0) 0.85f else 0.4f) else TvDivider,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isPriority) accent else TvTextSecondary,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (count > 0) TvTextBright else TvTextSecondary,
            )
        }
    }
}

@Composable
private fun LiveAlertRowCard(
    alert: LiveScannerAlert,
    onClick: () -> Unit,
) {
    val isCe = alert.side == OptionSide.CE
    val accent = if (isCe) TvTrendBullGreen else TvTrendBearRed
    val bg = if (isCe) TvBullContainer.copy(alpha = 0.35f) else TvBearContainer.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (alert.isPriorityWatch) bg else TvBackground,
        border = BorderStroke(
            width = if (alert.isNew) 1.dp else 0.75.dp,
            color = if (alert.isNew) TvWarningAmber else accent.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("alert_feed_row_${alert.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (alert.isNew) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        val badgeLabel = alert.level?.shortLabel ?: alert.timeframe?.label ?: alert.category.label
                        Text(
                            text = "${alert.side.name} $badgeLabel",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = accent,
                        )
                    }
                    Text(
                        text = alert.symbol,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TvTextBright,
                    )
                    if (alert.isPriorityWatch) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TvTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = alert.detailText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp),
                    color = TvTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = alert.triggeredAtTimeIst,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TvWarningAmber,
            )
        }
    }
}
