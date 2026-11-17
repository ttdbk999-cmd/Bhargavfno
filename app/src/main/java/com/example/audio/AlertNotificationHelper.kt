package com.example.audio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AlertCategory
import com.example.domain.model.LiveScannerAlert
import com.example.domain.model.OptionSide

class AlertNotificationHelper(private val context: Context) {

    init {
        ensureChannels()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postBreakoutNotification(alert: LiveScannerAlert) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val isPositionalBuzz = alert.category == AlertCategory.POSITIONAL_VOL_RSI
        val sideTag = if (alert.side == OptionSide.CE) "CE BULLISH ↑" else "PE BEARISH ↓"
        val channelId = if (isPositionalBuzz) CHANNEL_BUZZ_ID else CHANNEL_ID
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (isPositionalBuzz) {
                    "🔊 POSITIONAL BUZZ: ${alert.symbol} • ${alert.title}"
                } else {
                    "${alert.symbol} • ${alert.title} ($sideTag)"
                }
            )
            .setContentText("${alert.detailText} @ ${alert.triggeredAtTimeIst}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isPositionalBuzz) {
            builder
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setVibrate(BUZZ_VIBRATION_PATTERN)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), builder.build())
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Pivot, RSI & Volume Surge Alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Real-time alerts when F&O stocks cross R2/R3, S2/S3, RSI >70/<30, or 2x 20-day Volume SMA"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(CHANNEL_BUZZ_ID) == null) {
                val buzzChannel = NotificationChannel(
                    CHANNEL_BUZZ_ID,
                    "Positional Vol >2x + RSI >65 Buzz",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "High-priority sound & vibration buzz when Positional stocks hit Volume >2x 20D SMA and RSI >65"
                    enableVibration(true)
                    vibrationPattern = BUZZ_VIBRATION_PATTERN
                }
                manager.createNotificationChannel(buzzChannel)
            }
        }
    }

    private companion object {
        const val CHANNEL_ID = "fno_pivot_rsi_alerts"
        const val CHANNEL_BUZZ_ID = "fno_positional_vol_rsi_buzz"
        val BUZZ_VIBRATION_PATTERN = longArrayOf(0L, 200L, 90L, 280L, 90L, 200L)
    }
}
