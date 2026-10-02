package com.example.othello.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.othello.bot.Bot
import com.example.othello.config.AppConfig
import com.example.othello.game.GameState
import com.example.othello.game.Move
import com.example.othello.game.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who plays each colour. */
enum class Participant { HUMAN, BOT }

data class Players(val black: Participant, val white: Participant) {
    fun of(p: Player) = if (p == Player.BLACK) black else white
}

data class FlipAnimations(
    /** key = cell index (0..63); value = ms-start of the current animation */
    val running: Map<Int, Long> = emptyMap(),
    val nowMs: Long = 0L,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _flips = MutableStateFlow(FlipAnimations())
    val flips: StateFlow<FlipAnimations> = _flips.asStateFlow()

    private val _players = MutableStateFlow(Players(Participant.HUMAN, Participant.HUMAN))
    val players: StateFlow<Players> = _players.asStateFlow()

    private val _botThinking = MutableStateFlow(false)
    val botThinking: StateFlow<Boolean> = _botThinking.asStateFlow()

    private var botJob: Job? = null
    private var bot: Bot? = null

    fun configure(players: Players) {
        _players.value = players
        resetGame()
    }

    fun resetGame() {
        botJob?.cancel()
        _state.value = GameState()
        _flips.value = FlipAnimations()
        _botThinking.value = false
        maybeRunBot()
    }

    fun onTapCell(x: Int, y: Int) {
        if (_botThinking.value) return
        val cur = _state.value
        if (_players.value.of(cur.toMove) != Participant.HUMAN) return
        val mv = Move(x, y)
        if (!cur.board.isLegal(mv, cur.toMove)) return
        applyMove(mv)
    }

    fun passTurn() {
        val cur = _state.value
        if (cur.legalMoves.isNotEmpty()) return
        _state.value = cur.play(Move.PASS) ?: return
        maybeRunBot()
    }

    private fun applyMove(move: Move) {
        val cur = _state.value
        val next = cur.play(move) ?: return
        _state.value = next
        if (next.lastFlips.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _flips.update { prev ->
                val running = prev.running.toMutableMap()
                for ((fx, fy) in next.lastFlips) running[fy * 8 + fx] = now
                prev.copy(running = running, nowMs = now)
            }
            viewModelScope.launch {
                val anim = AppConfig.Animation
                delay(anim.FLIP_FRAME_MS * anim.FLIP_FRAME_COUNT + anim.FLIP_CLEANUP_PAD_MS)
                _flips.update { prev ->
                    val running = prev.running.toMutableMap()
                    for ((fx, fy) in next.lastFlips) running.remove(fy * 8 + fx)
                    prev.copy(running = running)
                }
            }
        }
        maybeRunBot()
    }

    private fun maybeRunBot() {
        val cur = _state.value
        if (cur.isTerminal) return
        if (cur.legalMoves.isEmpty()) {
            viewModelScope.launch {
                delay(AppConfig.Bot.AUTO_PASS_DELAY_MS)
                _state.value = cur.play(Move.PASS) ?: return@launch
                maybeRunBot()
            }
            return
        }
        if (_players.value.of(cur.toMove) != Participant.BOT) return
        _botThinking.value = true
        botJob = viewModelScope.launch {
            // Give the player a beat to see their move + flip animation before
            // the bot starts to think. Skipped on the opening ply.
            if (cur.lastMove != null) delay(AppConfig.Bot.RESPONSE_DELAY_MS)
            val chosen = withContext(Dispatchers.Default) {
                val b = bot ?: Bot.loadDefault(getApplication()).also { bot = it }
                b.chooseMove(cur.board, cur.toMove)
            }
            _botThinking.value = false
            delay(AppConfig.Bot.THINKING_MIN_MS)
            applyMove(chosen)
        }
    }
}
