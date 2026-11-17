package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.domain.model.OptionSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Plays distinct non-blocking acoustic + haptic buzz alerts for Positional (Vol > 2x + RSI > 65),
 * CE (bullish) and PE (bearish) RSI crossovers, and 5/5 scanner matches.
 */
class SoundAlertManager(
    private val context: Context? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val playMutex = Mutex()

    @Volatile
    private var toneGenerator: ToneGenerator? = null

    @Volatile
    private var lastAutoAlertMs: Long = 0L

    private fun getOrCreateGenerator(): ToneGenerator? {
        toneGenerator?.let { return it }
        return synchronized(this) {
            toneGenerator ?: runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
            }.getOrElse {
                runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
            }.also { created ->
                toneGenerator = created
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticBuzz(pattern: LongArray = longArrayOf(0L, 180L, 90L, 260L)) {
        val ctx = context ?: return
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator.vibrate(pattern, -1)
                }
            }
        }
    }

    /**
     * Unmistakable triple-chime + double-buzz haptic pattern dedicated to Positional Volume > 2x + RSI > 65.
     */
    fun playPositionalVolRsiBuzz(forceTest: Boolean = false) {
        scope.launch {
            playMutex.withLock {
                val now = System.currentTimeMillis()
                if (!forceTest && (now - lastAutoAlertMs) < MIN_AUTO_ALERT_GAP_MS) {
                    return@withLock
                }
                lastAutoAlertMs = now
                triggerHapticBuzz(longArrayOf(0L, 200L, 90L, 280L, 90L, 200L))
                val gen = getOrCreateGenerator() ?: return@withLock
                runCatching {
                    gen.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
                    delay(190L)
                    gen.startTone(ToneGenerator.TONE_PROP_ACK, 210)
                    delay(240L)
                    gen.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                    delay(240L)
                }
            }
        }
    }

    fun playHighPriorityCross(side: OptionSide, forceTest: Boolean = false) {
        scope.launch {
            playMutex.withLock {
                val now = System.currentTimeMillis()
                if (!forceTest && (now - lastAutoAlertMs) < MIN_AUTO_ALERT_GAP_MS) {
                    return@withLock
                }
                lastAutoAlertMs = now
                val gen = getOrCreateGenerator() ?: return@withLock
                runCatching {
                    if (side == OptionSide.CE) {
                        // Ascending bright double-chime for CE (>70 RSI)
                        gen.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                        delay(175L)
                        gen.startTone(ToneGenerator.TONE_PROP_ACK, 210)
                        delay(230L)
                    } else {
                        // Urgent double warning tone for PE (<30 RSI)
                        gen.startTone(ToneGenerator.TONE_PROP_NACK, 180)
                        delay(210L)
                        gen.startTone(ToneGenerator.TONE_PROP_NACK, 240)
                        delay(260L)
                    }
                }
            }
        }
    }

    fun playSignalMatchChime(side: OptionSide) {
        scope.launch {
            playMutex.withLock {
                val now = System.currentTimeMillis()
                if ((now - lastAutoAlertMs) < MIN_AUTO_ALERT_GAP_MS) {
                    return@withLock
                }
                lastAutoAlertMs = now
                val gen = getOrCreateGenerator() ?: return@withLock
                runCatching {
                    val tone = if (side == OptionSide.CE) {
                        ToneGenerator.TONE_PROP_BEEP2
                    } else {
                        ToneGenerator.TONE_SUP_PIP
                    }
                    gen.startTone(tone, 170)
                    delay(190L)
                }
            }
        }
    }

    fun release() {
        scope.cancel()
        synchronized(this) {
            runCatching { toneGenerator?.release() }
            toneGenerator = null
        }
    }

    private companion object {
        const val MIN_AUTO_ALERT_GAP_MS = 1_200L
    }
}
