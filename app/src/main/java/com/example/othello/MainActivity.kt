package com.example.othello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.othello.bot.Difficulty
import com.example.othello.data.GameStore
import com.example.othello.data.SavedMode
import com.example.othello.game.Player
import com.example.othello.net.OnlineRegistry
import com.example.othello.net.SessionHandoff
import com.example.othello.net.firebase.FirebaseOnlineClient
import com.example.othello.ui.GameScreen
import com.example.othello.ui.GameViewModel
import com.example.othello.ui.HomeScreen
import com.example.othello.ui.HumanColorChoice
import com.example.othello.ui.OnlineLobbyScreen
import com.example.othello.ui.OthelloTheme
import com.example.othello.ui.Participant
import com.example.othello.ui.Players
import kotlin.random.Random
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register the online backend. Swap this one line (and the import
        // above) when moving off Firebase to a custom WebSocket server.
        val onlineClient = FirebaseOnlineClient(applicationContext)
        OnlineRegistry.install(onlineClient)

        // Best-effort sweep of any rooms this device still has in its local
        // tracker from a previous session (host or guest). Each is deleted if
        // still around, dropped if the other player already cleaned up.
        lifecycleScope.launch { runCatching { onlineClient.sweepTrackedRooms() } }

        enableEdgeToEdge()
        setContent {
            OthelloTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onStartTwoPlayer = { resume ->
                                nav.navigate("game/2p?resume=$resume")
                            },
                            onStartVsBot = { choice, difficulty, resume ->
                                // On resume we honour the human colour and
                                // difficulty from the save. On a fresh start we
                                // honour the player's chip picks, resolving
                                // RANDOM here.
                                val human = when (choice) {
                                    HumanColorChoice.BLACK -> Player.BLACK
                                    HumanColorChoice.WHITE -> Player.WHITE
                                    HumanColorChoice.RANDOM ->
                                        if (Random.nextBoolean()) Player.BLACK else Player.WHITE
                                }
                                nav.navigate(
                                    "game/bot/${human.name}/${difficulty.name}?resume=$resume"
                                )
                            },
                            onStartOnline = { nav.navigate("online/lobby") },
                        )
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
                        route = "game/bot/{human}/{difficulty}?resume={resume}",
                        arguments = listOf(
                            navArgument("human") { type = NavType.StringType },
                            navArgument("difficulty") { type = NavType.StringType },
                            navArgument("resume") {
                                type = NavType.BoolType; defaultValue = false
                            },
                        ),
                    ) { entry ->
                        val humanArg = entry.arguments?.getString("human") ?: "BLACK"
                        val difficultyArg = entry.arguments?.getString("difficulty")
                            ?: Difficulty.MEDIUM.name
                        val resume = entry.arguments?.getBoolean("resume") == true
                        val vm: GameViewModel = viewModel()
                        val ctx = LocalContext.current
                        LaunchedEffect(Unit) {
                            val saved = if (resume) GameStore(ctx.applicationContext)
                                .load(SavedMode.VS_BOT) else null
                            val human = Player.valueOf(saved?.humanColor ?: humanArg)
                            val difficulty = runCatching {
                                Difficulty.valueOf(difficultyArg)
                            }.getOrDefault(Difficulty.MEDIUM)
                            val players = Players(
                                black = if (human == Player.BLACK) Participant.HUMAN else Participant.BOT,
                                white = if (human == Player.WHITE) Participant.HUMAN else Participant.BOT,
                            )
                            if (saved != null) vm.resume(saved, players)
                            else vm.configure(players, difficulty)
                        }
                        val humanName = Player.valueOf(humanArg).name.lowercase()
                        GameScreen(
                            vm,
                            title = "vs Bot · you are $humanName",
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
                        GameScreen(
                            vm,
                            title = "Online",
                            onBack = {
                                // Deletes the room on the backend before
                                // navigating away so neither side's device
                                // is left with a stale room doc. The sweep
                                // on next launch is still a backstop for
                                // the "activity killed mid-game" case.
                                vm.leaveOnline()
                                nav.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}
