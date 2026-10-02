package com.example.othello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                            onStartTwoPlayer = { nav.navigate("game/2p") },
                            onStartVsBot = { choice ->
                                val human = when (choice) {
                                    HumanColorChoice.BLACK -> Player.BLACK
                                    HumanColorChoice.WHITE -> Player.WHITE
                                    HumanColorChoice.RANDOM ->
                                        if (Random.nextBoolean()) Player.BLACK else Player.WHITE
                                }
                                nav.navigate("game/bot/${human.name}")
                            },
                        )
                    }
                    composable("game/2p") {
                        val vm: GameViewModel = viewModel()
                        LaunchedEffect(Unit) {
                            vm.configure(Players(Participant.HUMAN, Participant.HUMAN))
                        }
                        GameScreen(vm, title = "2-player", onBack = { nav.popBackStack() })
                    }
                    composable("game/bot/{human}") { entry ->
                        val human = Player.valueOf(entry.arguments?.getString("human") ?: "BLACK")
                        val vm: GameViewModel = viewModel()
                        LaunchedEffect(Unit) {
                            vm.configure(
                                Players(
                                    black = if (human == Player.BLACK) Participant.HUMAN else Participant.BOT,
                                    white = if (human == Player.WHITE) Participant.HUMAN else Participant.BOT,
                                )
                            )
                        }
                        GameScreen(
                            vm,
                            title = "vs Bot · you are ${human.name.lowercase()}",
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
