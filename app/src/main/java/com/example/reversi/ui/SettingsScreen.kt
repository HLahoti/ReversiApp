package com.example.reversi.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reversi.BuildConfig
import com.example.reversi.R
import com.example.reversi.config.AppConfig
import com.example.reversi.data.Changelog
import com.example.reversi.data.UserSettings
import com.example.reversi.update.Updates
import kotlinx.coroutines.delay

/** App settings, with an About section at the end. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()

    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                IconButton(onClick = { haptics.tap(); onBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Felt.OnDark,
                    )
                }
                Text(
                    "Settings",
                    color = Felt.OnDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 16.dp),
            ) {
                GameplaySection()
                Spacer(Modifier.height(24.dp))
                OnlineSection()
                Spacer(Modifier.height(24.dp))
                AboutSection()
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Felt.OnDarkDim,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun GameplaySection() {
    val haptics = rememberHaptics()
    val hintsOff by UserSettings.hintsOff.collectAsState()
    val openingAnimation by UserSettings.openingAnimation.collectAsState()
    val takebacksOff by UserSettings.takebacksOff.collectAsState()

    SectionTitle("Gameplay")
    GlassPanel(Modifier.fillMaxWidth()) {
        Column {
            SettingRow(
                title = "Turn off hints",
                description = "The hint bulb stays off in games against the bot.",
                checked = hintsOff,
                onCheckedChange = { haptics.tap(); UserSettings.setHintsOff(it) },
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = "Turn off takebacks",
                description = "In games against the bot you can still look back through the moves, " +
                    "but not play on from an earlier one.",
                checked = takebacksOff,
                onCheckedChange = { haptics.tap(); UserSettings.setTakebacksOff(it) },
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = "Game start animation",
                description = "Play the intro as each new game sets up the board.",
                checked = openingAnimation,
                onCheckedChange = { haptics.tap(); UserSettings.setOpeningAnimation(it) },
            )
        }
    }
}

@Composable
private fun OnlineSection() {
    val haptics = rememberHaptics()
    val awayAlerts by UserSettings.awayAlerts.collectAsState()
    val requestNotifications = rememberNotificationPermissionRequest()

    SectionTitle("Online play")
    GlassPanel(Modifier.fillMaxWidth()) {
        SettingRow(
            title = "Notify me while I'm away",
            description = "If you switch apps during an online game, get a notification when " +
                "your opponent joins, plays, asks for a rematch, or the game ends.",
            checked = awayAlerts,
            onCheckedChange = {
                haptics.tap()
                UserSettings.setAwayAlerts(it)
                if (it) requestNotifications()
            },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Felt.OnDark, fontSize = 16.sp)
            Text(description, color = Felt.OnDarkDim, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        PieceToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = title,
        )
    }
}

/**
 * A switch drawn as a board piece: black when off, white when on. Each
 * change plays the board's flip animation towards the new colour.
 */
@Composable
private fun PieceToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    size: Dp = 44.dp,
) {
    // Bumped on every tap, so a tap mid-flip restarts the flip from the
    // other side instead of waiting for the current one to finish.
    var flipCount by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    LaunchedEffect(flipCount) {
        if (flipCount == 0) return@LaunchedEffect
        delay(AppConfig.Animation.FLIP_TOTAL_MS)
        flipping = false
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Switch) {
                // Set here rather than in the effect, which runs a frame
                // late and would flash the new colour before the flip.
                flipping = true
                flipCount++
                onCheckedChange(it)
            },
    ) {
        if (flipping) {
            key(flipCount) {
                FlippingPiece(
                    dir = if (checked) FlipDir.B_TO_W else FlipDir.W_TO_B,
                    startDelayMs = 0,
                    sizeDp = size,
                )
            }
        } else {
            Image(
                painter = painterResource(
                    if (checked) R.drawable.piece_white else R.drawable.piece_black
                ),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun AboutSection() {
    val haptics = rememberHaptics()
    val activity = LocalContext.current as? Activity
    val update by Updates.available.collectAsState()

    SectionTitle("About")
    GlassPanel(Modifier.fillMaxWidth()) {
        Column {
            Text("Reversi", color = Felt.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", color = Felt.OnDarkDim, fontSize = 14.sp)
            Text("Created by Harsh Lahoti", color = Felt.OnDarkDim, fontSize = 14.sp)
            Changelog.forVersion(BuildConfig.VERSION_NAME)?.let { entry ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "What's new in ${entry.version}",
                    color = Felt.OnDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(entry.title, color = Felt.OnDarkDim, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                for (change in entry.changes) {
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("•", color = Felt.OnDark, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(change, color = Felt.OnDark, fontSize = 14.sp)
                    }
                }
            }
            update?.let { u ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "Version ${u.versionName} is available",
                    color = Felt.OnDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                u.title?.let { Text(it, color = Felt.OnDarkDim, fontSize = 14.sp) }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { haptics.confirm(); activity?.let(Updates::startUpdate) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Felt.Highlight,
                        contentColor = Felt.Deep,
                    ),
                ) { Text("Update") }
            }
        }
    }
}
