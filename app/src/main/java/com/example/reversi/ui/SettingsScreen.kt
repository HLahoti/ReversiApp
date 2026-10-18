package com.example.reversi.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reversi.BuildConfig
import com.example.reversi.data.Changelog
import com.example.reversi.update.Updates

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
                // Settings go here, above About.
                AboutSection()
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val haptics = rememberHaptics()
    val activity = LocalContext.current as? Activity
    val update by Updates.available.collectAsState()

    Text(
        "About",
        color = Felt.OnDarkDim,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
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
