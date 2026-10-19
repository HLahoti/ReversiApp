package com.example.reversi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.reversi.bot.Opponent
import com.example.reversi.data.GameStore
import com.example.reversi.data.SavedMode
import com.example.reversi.data.UserSettings
import com.example.reversi.game.Player
import com.example.reversi.net.OnlineRegistry
import com.example.reversi.net.SessionHandoff
import com.example.reversi.net.firebase.FirebaseOnlineClient
import com.example.reversi.history.HistoryRegistry
import com.example.reversi.history.LocalGameHistory
import com.example.reversi.profile.LocalPlayerProfile
import com.example.reversi.profile.ProfileRegistry
import com.example.reversi.ui.GameScreen
import com.example.reversi.ui.GameViewModel
import com.example.reversi.ui.HomeScreen
import com.example.reversi.ui.HowToPlayScreen
import com.example.reversi.ui.HumanColorChoice
import com.example.reversi.ui.OnlineLobbyScreen
import com.example.reversi.ui.ReversiTheme
import com.example.reversi.ui.SettingsScreen
import com.example.reversi.ui.HistoryScreen
import com.example.reversi.update.GitHubReleaseChecker
import com.example.reversi.update.Updates
import com.example.reversi.ui.Participant
import com.example.reversi.ui.Players
import kotlin.random.Random
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Who the player is online: a name typed into the lobby for now.
        // Swap this line for an account-backed profile (e.g. Google sign-in).
        val profile = LocalPlayerProfile(applicationContext)
        ProfileRegistry.install(profile)

        // Register the online backend. Swap this one line (and the import
        // above) when moving off Firebase to a custom WebSocket server.
        val onlineClient = FirebaseOnlineClient(applicationContext, profile)
        OnlineRegistry.install(onlineClient)

        // Game history, kept on the device. Once per process: an activity
        // recreated mid-game must not abandon the game it is showing. Games
        // still "in progress" from a previous process can only carry on if
        // they're a saved vs-bot game; the rest are abandoned.
        if (HistoryRegistry.installIfAbsent { LocalGameHistory(applicationContext) }) {
            HistoryRegistry.current.abandonAllExcept(GameStore(applicationContext).resumableHistoryIds())
        }

        // Best-effort sweep of any rooms this device still has in its local
        // tracker from a previous session (host or guest). Each is deleted if
        // still around, dropped if the other player already cleaned up.
        lifecycleScope.launch { runCatching { onlineClient.sweepTrackedRooms() } }

        // Update checks, while the app is sideloaded from GitHub Releases.
        // Moving to the Play Store: swap GitHubReleaseChecker for a Play
        // in-app updates checker here; see UpdateChecker for the details.
        Updates.install(
            this,
            GitHubReleaseChecker(installedVersion = BuildConfig.VERSION_NAME),
            installedVersion = BuildConfig.VERSION_NAME,
        )
        lifecycleScope.launch { Updates.checkIfDue() }

        UserSettings.install(this)

        enableEdgeToEdge()
        setContent {
            ReversiTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onStartTwoPlayer = { resume ->
                                nav.navigate("game/2p?resume=$resume")
                            },
                            onStartVsBot = { choice, opponent, resume ->
                                // On resume we honour the human colour and
                                // opponent from the save. On a fresh start we
                                // honour the player's chip picks, resolving
                                // RANDOM here.
                                val human = when (choice) {
                                    HumanColorChoice.BLACK -> Player.BLACK
                                    HumanColorChoice.WHITE -> Player.WHITE
                                    HumanColorChoice.RANDOM ->
                                        if (Random.nextBoolean()) Player.BLACK else Player.WHITE
                                }
                                nav.navigate(
                                    "game/bot/${human.name}/${opponent.name}?resume=$resume"
                                )
                            },
                            onStartOnline = { nav.navigate("online/lobby") },
                            onHistory = { nav.navigate("history") },
                            onHowToPlay = { nav.navigate("howto") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                    composable("history") {
                        HistoryScreen(onBack = { nav.popBackStack() })
                    }
                    composable("howto") {
                        HowToPlayScreen(onBack = { nav.popBackStack() })
                    }
                    composable(
                        route = "game/2p?resume={resume}",
                        arguments = listOf(
                            navArgument("resume") {
                                type = NavType.BoolType; defaultValue = false
                            },
                        ),
                    ) { entry ->
                        val resume = entry.arguments?.getBoolean("resume") == true
                        val vm: GameViewModel = viewModel()
                        val ctx = LocalContext.current
                        LaunchedEffect(Unit) {
                            val players = Players(Participant.HUMAN, Participant.HUMAN)
                            val saved = if (resume) GameStore(ctx.applicationContext)
                                .load(SavedMode.TWO_PLAYER) else null
                            if (saved != null) vm.resume(saved, players)
                            else vm.configure(players)
                        }
                        GameScreen(vm, title = "2-player", onBack = { nav.popBackStack() })
                    }
                    composable(
                        route = "game/bot/{human}/{opponent}?resume={resume}",
                        arguments = listOf(
                            navArgument("human") { type = NavType.StringType },
                            navArgument("opponent") { type = NavType.StringType },
                            navArgument("resume") {
                                type = NavType.BoolType; defaultValue = false
                            },
                        ),
                    ) { entry ->
                        val humanArg = entry.arguments?.getString("human") ?: "BLACK"
                        val opponentArg = entry.arguments?.getString("opponent")
                        val resume = entry.arguments?.getBoolean("resume") == true
                        val vm: GameViewModel = viewModel()
                        val ctx = LocalContext.current
                        val opponent = Opponent.fromSaved(opponentArg) ?: Opponent.DEFAULT
                        // Read once, outside the effect, so the title shows the
                        // colour the resumed game was saved with.
                        val saved = remember(resume, opponent) {
                            if (resume) GameStore(ctx.applicationContext)
                                .load(SavedMode.VS_BOT, opponent) else null
                        }
                        val human = Player.valueOf(saved?.humanColor ?: humanArg)
                        LaunchedEffect(Unit) {
                            val players = Players(
                                black = if (human == Player.BLACK) Participant.HUMAN else Participant.BOT,
                                white = if (human == Player.WHITE) Participant.HUMAN else Participant.BOT,
                            )
                            if (saved != null) vm.resume(saved, players, opponent)
                            else vm.configure(players, opponent)
                        }
                        // Follow the view-model, since "Play again" swaps colours.
                        val players by vm.players.collectAsState()
                        val current = when (Participant.BOT) {
                            players.white -> Player.BLACK
                            players.black -> Player.WHITE
                            else -> human // not configured yet
                        }
                        val humanName = current.name.lowercase()
                        GameScreen(
                            vm,
                            title = "vs ${opponent.displayName} · you are $humanName",
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("online/lobby") {
                        OnlineLobbyScreen(
                            onBack = { nav.popBackStack() },
                            onGameReady = { token ->
                                nav.navigate("online/game/$token") {
                                    popUpTo("online/lobby") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(
                        route = "online/game/{token}",
                        arguments = listOf(navArgument("token") { type = NavType.StringType }),
                    ) { entry ->
                        val token = entry.arguments?.getString("token") ?: return@composable
                        val vm: GameViewModel = viewModel()
                        LaunchedEffect(token) {
                            val handoff = SessionHandoff.take(token)
                            if (handoff == null) {
                                // Lost the session (process death between
                                // lobby and game); go back and let the user retry.
                                nav.popBackStack("home", inclusive = false)
                                return@LaunchedEffect
                            }
                            vm.configureOnline(handoff.session, handoff.localColor)
                        }
                        // Deletes the room on the backend before navigating
                        // away, so the opponent sees that we left instead of
                        // waiting on a move or rematch forever. The sweep on
                        // next launch is still a backstop for the "activity
                        // killed mid-game" case.
                        val leave = {
                            vm.leaveOnline()
                            nav.popBackStack()
                        }
                        val online by vm.online.collectAsState()
                        var confirmLeave by remember { mutableStateOf(false) }
                        // Once the opponent has gone there's no room left to
                        // destroy, so skip the confirmation.
                        val requestLeave = {
                            if (online?.opponentLeft == true) leave() else confirmLeave = true
                        }
                        BackHandler { requestLeave() }
                        // Follow the view-model, since a rematch swaps colours.
                        val players by vm.players.collectAsState()
                        val localColor = when (Participant.REMOTE) {
                            players.white -> Player.BLACK
                            players.black -> Player.WHITE
                            else -> null // not configured yet
                        }
                        val opponent = online?.opponentName?.let { "vs $it" } ?: "Online"
                        GameScreen(
                            vm,
                            title = if (localColor == null) opponent
                            else "$opponent · you are ${localColor.name.lowercase()}",
                            onBack = { requestLeave() },
                        )
                        if (confirmLeave) {
                            AlertDialog(
                                onDismissRequest = { confirmLeave = false },
                                title = { Text("Leave the game?") },
                                text = {
                                    Text(
                                        "The room will be destroyed and your opponent " +
                                            "won't be able to continue or rematch.",
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmLeave = false
                                        leave()
                                    }) { Text("Leave") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
