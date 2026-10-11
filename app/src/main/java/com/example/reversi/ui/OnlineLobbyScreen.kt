package com.example.reversi.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reversi.game.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnlineLobbyScreen(
    onBack: () -> Unit,
    onGameReady: (handoffToken: String) -> Unit,
    vm: OnlineLobbyViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()

    LaunchedEffect(state.handoffToken) {
        val tok = state.handoffToken ?: return@LaunchedEffect
        vm.markHandoffConsumed()
        onGameReady(tok)
    }

    // Leaving a room someone may already have the code for destroys it, so
    // that goes through a confirmation. Only an explicit back-out lands here;
    // switching apps (e.g. via Share) leaves the room untouched.
    var confirmClose by remember { mutableStateOf(false) }
    val backToPicker = {
        haptics.tap()
        if (state.step == LobbyStep.WAITING) confirmClose = true else vm.cancel()
    }
    BackHandler(enabled = state.step != LobbyStep.PICK) { backToPicker() }

    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(
                title = "Play online",
                onBack = {
                    if (state.step == LobbyStep.PICK) {
                        haptics.tap()
                        onBack()
                    } else {
                        backToPicker()
                    }
                },
            )
            Spacer(Modifier.height(32.dp))

            when (state.step) {
                LobbyStep.PICK -> PickPanel(
                    error = state.error,
                    onCreate = { color -> haptics.confirm(); vm.createRoom(color) },
                    onJoin = { code -> haptics.confirm(); vm.joinRoom(code) },
                )
                LobbyStep.CREATING -> StatusPanel("Creating room…")
                LobbyStep.JOINING -> StatusPanel("Joining room…")
                LobbyStep.WAITING -> WaitingPanel(
                    code = state.roomCode?.value ?: "",
                    onTap = { haptics.tap() },
                    onCancel = backToPicker,
                )
            }
        }
    }

    if (confirmClose && state.step == LobbyStep.WAITING) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close this room?") },
            text = {
                Text("The room will be destroyed and the code you shared will stop working.")
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    confirmClose = false
                    vm.cancel()
                }) { Text("Close room") }
            },
            dismissButton = {
                TextButton(onClick = { haptics.tap(); confirmClose = false }) { Text("Keep waiting") }
            },
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Felt.OnDark,
            )
        }
        Text(title, color = Felt.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PickPanel(
    error: String?,
    onCreate: (Player?) -> Unit,
    onJoin: (String) -> Unit,
) {
    var hostChoice by remember { mutableStateOf<HumanColorChoice>(HumanColorChoice.RANDOM) }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Create a room", color = Felt.OnDark, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Black", hostChoice == HumanColorChoice.BLACK) { hostChoice = HumanColorChoice.BLACK }
                ChoiceChip("White", hostChoice == HumanColorChoice.WHITE) { hostChoice = HumanColorChoice.WHITE }
                ChoiceChip("Random", hostChoice == HumanColorChoice.RANDOM) { hostChoice = HumanColorChoice.RANDOM }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Create room",
                onClick = {
                    val color = when (hostChoice) {
                        HumanColorChoice.BLACK -> Player.BLACK
                        HumanColorChoice.WHITE -> Player.WHITE
                        HumanColorChoice.RANDOM -> null
                    }
                    onCreate(color)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    var code by remember { mutableStateOf("") }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Join a room", color = Felt.OnDark, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(6) },
                singleLine = true,
                placeholder = { Text("Room code") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    letterSpacing = 4.sp,
                    color = Felt.OnDark,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Felt.Highlight,
                    unfocusedBorderColor = Felt.GlassBorder,
                    focusedTextColor = Felt.OnDark,
                    unfocusedTextColor = Felt.OnDark,
                    cursorColor = Felt.Highlight,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Join room",
                onClick = { onJoin(code) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (error != null) {
        Spacer(Modifier.height(16.dp))
        Text(error, color = Felt.Highlight, fontSize = 14.sp)
    }
}

@Composable
private fun StatusPanel(message: String) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        ) {
            CircularProgressIndicator(color = Felt.Highlight)
            Spacer(Modifier.height(16.dp))
            Text(message, color = Felt.OnDark)
        }
    }
}

@Composable
private fun WaitingPanel(code: String, onTap: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Share this code", color = Felt.OnDarkDim, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = code,
                color = Felt.Highlight,
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconLabelButton(
                    text = if (copied) "Copied" else "Copy",
                    icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    onClick = {
                        onTap()
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Room code", code)))
                        }
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                )
                IconLabelButton(
                    text = "Share",
                    icon = Icons.Filled.Share,
                    onClick = {
                        onTap()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my Reversi game! Room code: $code\n\n" +
                                    "Don't have the app? Download it here: " +
                                    com.example.reversi.config.AppConfig.Online.DOWNLOAD_URL,
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Share room code"))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = Felt.Highlight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Waiting for opponent…",
                    color = Felt.OnDark,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Felt.Highlight.copy(alpha = 0.9f),
            contentColor = Felt.Deep,
        ),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.example.reversi.config.AppConfig.Palette.Glass,
            contentColor = Felt.OnDark,
        ),
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
private fun IconLabelButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.example.reversi.config.AppConfig.Palette.Glass,
            contentColor = Felt.OnDark,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Felt.Highlight.copy(alpha = 0.9f) else com.example.reversi.config.AppConfig.Palette.Glass
    val fg = if (selected) Felt.Deep else Felt.OnDark
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) { Text(label, fontSize = 14.sp) }
}
