package com.example.othello.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.othello.config.AppConfig
import kotlin.math.roundToInt

/**
 * Short vibrations driven through [Vibrator] directly.
 *
 * View.performHapticFeedback (and Compose's LocalHapticFeedback) is silenced when the
 * system "touch feedback" switch is off, which would make the chip taps on the home
 * screen feel dead on such devices. Going through the vibrator keeps every buzz working
 * either way.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }

    /** Light tick for taps that don't commit anything — chip selects, back, pause, illegal move. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun tap() {
        if (!AppConfig.Haptics.ENABLED) return
        buzz(AppConfig.Haptics.TAP_INTENSITY, tick = true)
    }

    /** Firmer click for commits — legal move, start, reset, play again, pass. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun confirm() {
        if (!AppConfig.Haptics.ENABLED) return
        buzz(AppConfig.Haptics.CONFIRM_INTENSITY, tick = false)
    }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun buzz(intensity: Float, tick: Boolean) {
        val vibrator = vibrator ?: return
        val level = intensity.coerceIn(0f, 1f)
        if (level <= 0f) return
        // Both paths use PRIMITIVE_CLICK — the A56's PRIMITIVE_TICK is physically too
        // subtle to feel. tap() rides on a scaled single click; confirm() layers a second
        // click on top for a double-pulse that reads as firmer than the chip tap.
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(click) -> {
                val comp = VibrationEffect.startComposition().addPrimitive(click, level)
                if (!tick) comp.addPrimitive(click, level, 40)
                vibrator.vibrate(comp.compose())
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        if (tick) VibrationEffect.EFFECT_CLICK
                        else VibrationEffect.EFFECT_HEAVY_CLICK,
                    ),
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        if (tick) AppConfig.Haptics.FALLBACK_TAP_MS
                        else AppConfig.Haptics.FALLBACK_CONFIRM_MS,
                        if (tick) amplitudeFor(level)
                        else AppConfig.Haptics.FALLBACK_CONFIRM_AMPLITUDE,
                    ),
                )
            else ->
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    if (tick) AppConfig.Haptics.FALLBACK_TAP_MS
                    else AppConfig.Haptics.FALLBACK_CONFIRM_MS,
                )
        }
    }

    private fun amplitudeFor(level: Float): Int = (level * 255).roundToInt().coerceIn(1, 255)
}

@Composable
fun rememberHaptics(): Haptics {
    val ctx = LocalContext.current
    return remember(ctx) { Haptics(ctx) }
}
