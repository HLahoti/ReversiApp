package com.example.othello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.othello.data.GameStore
import com.example.othello.data.SavedMode
import com.example.othello.game.Player
import com.example.othello.ui.GameScreen
import com.example.othello.ui.GameViewModel
import com.example.othello.ui.HomeScreen
import com.example.othello.ui.HumanColorChoice
import com.example.othello.ui.OthelloTheme
import com.example.othello.ui.Participant
import com.example.othello.ui.Players
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            onStartVsBot = { choice, resume ->
                                // On resume we honour the human colour from the save.
                                // On a fresh start we honour the player's chip pick,
                                // resolving RANDOM here.
                                val human = when (choice) {
                                    HumanColorChoice.BLACK -> Player.BLACK
                                    HumanColorChoice.WHITE -> Player.WHITE
                                    HumanColorChoice.RANDOM ->
                                        if (Random.nextBoolean()) Player.BLACK else Player.WHITE
                                }
                                nav.navigate("game/bot/${human.name}?resume=$resume")
                            },
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
                        route = "game/bot/{human}?resume={resume}",
                        arguments = listOf(
                            navArgument("human") { type = NavType.StringType },
                            navArgument("resume") {
                                type = NavType.BoolType; defaultValue = false
                            },
                        ),
                    ) { entry ->
                        val humanArg = entry.arguments?.getString("human") ?: "BLACK"
                        val resume = entry.arguments?.getBoolean("resume") == true
                        val vm: GameViewModel = viewModel()
                        val ctx = LocalContext.current
                        LaunchedEffect(Unit) {
                            val saved = if (resume) GameStore(ctx.applicationContext)
                                .load(SavedMode.VS_BOT) else null
                            val human = Player.valueOf(saved?.humanColor ?: humanArg)
                            val players = Players(
                                black = if (human == Player.BLACK) Participant.HUMAN else Participant.BOT,
                                white = if (human == Player.WHITE) Participant.HUMAN else Participant.BOT,
                            )
                            if (saved != null) vm.resume(saved, players)
                            else vm.configure(players)
                        }
                        val humanName = Player.valueOf(humanArg).name.lowercase()
                        GameScreen(
                            vm,
                            title = "vs Bot · you are $humanName",
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
