package com.example.othello.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.othello.bot.Bot
import com.example.othello.config.AppConfig
import com.example.othello.data.GameStore
import com.example.othello.data.SavedGame
import com.example.othello.data.SavedMode
import com.example.othello.data.SavedMove
import com.example.othello.game.GameState
import com.example.othello.game.Move
import com.example.othello.game.Player
import kotlin.math.abs
import kotlin.math.max
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

/** A single scheduled flip: when it was queued, and how long it waits first. */
data class FlipRun(val startMs: Long, val delayMs: Long)

data class FlipAnimations(
    /** key = cell index (0..63); value = scheduling info for that cell's flip */
    val running: Map<Int, FlipRun> = emptyMap(),
    val nowMs: Long = 0L,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GameStore(app)

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _flips = MutableStateFlow(FlipAnimations())
    val flips: StateFlow<FlipAnimations> = _flips.asStateFlow()

    private val _players = MutableStateFlow(Players(Participant.HUMAN, Participant.HUMAN))
    val players: StateFlow<Players> = _players.asStateFlow()

    private val _botThinking = MutableStateFlow(false)
    val botThinking: StateFlow<Boolean> = _botThinking.asStateFlow()

    /** True while a prior move's flip animations are still playing out. */
    private val _animating = MutableStateFlow(false)
    val animating: StateFlow<Boolean> = _animating.asStateFlow()

    private var botJob: Job? = null
    private var bot: Bot? = null

    /** Every ply that has been applied, in order. Replayed on resume, written on persist. */
    private val moves = mutableListOf<Move>()

    /** Set once configure/resume has run, so persist() knows which file to write. */
    private var mode: SavedMode? = null

    fun configure(players: Players) {
        _players.value = players
        mode = modeFor(players)
        resetInternal()
        mode?.let(store::clear)
        maybeRunBot()
    }

    /**
     * Replay [saved] into the state. The caller is responsible for picking
     * `players` consistent with the save (two-human for a two-player save,
     * one-bot with [SavedGame.humanColor] as the human for a vs-bot save).
     */
    fun resume(saved: SavedGame, players: Players) {
        _players.value = players
        mode = modeFor(players)
        resetInternal()
        var s = GameState()
        for (m in saved.moves) {
            val mv = if (m.isPass) Move.PASS else Move(m.x, m.y)
            val next = s.play(mv) ?: break
            s = next
            moves.add(mv)
        }
        _state.value = s
        maybeRunBot()
    }

    fun resetGame() {
        botJob?.cancel()
        resetInternal()
        mode?.let(store::clear)
        maybeRunBot()
    }

    /**
     * Call on back / app pause. Writes the current unfinished game to the
     * matching save slot, or clears it if there is nothing worth resuming
     * (no moves yet, or the game is already over).
     */
    fun persist() {
        val m = mode ?: return
        if (moves.isEmpty() || _state.value.isTerminal) {
            store.clear(m)
            return
        }
        val saved = moves.map { SavedMove(it.x, it.y, it.isPass) }
        when (m) {
            SavedMode.TWO_PLAYER -> store.writeTwoPlayer(saved)
            SavedMode.VS_BOT -> {
                val human = humanColorIn(_players.value) ?: return
                store.writeVsBot(saved, human)
            }
        }
    }

    fun onTapCell(x: Int, y: Int) {
        if (_botThinking.value) return
        if (_animating.value) return
        val cur = _state.value
        if (_players.value.of(cur.toMove) != Participant.HUMAN) return
        val mv = Move(x, y)
        if (!cur.board.isLegal(mv, cur.toMove)) return
        applyMove(mv)
    }

    fun passTurn() {
        val cur = _state.value
        if (cur.legalMoves.isNotEmpty()) return
        val next = cur.play(Move.PASS) ?: return
        moves.add(Move.PASS)
        _state.value = next
        maybeRunBot()
    }

    private fun resetInternal() {
        botJob?.cancel()
        _state.value = GameState()
        _flips.value = FlipAnimations()
        _botThinking.value = false
        _animating.value = false
        moves.clear()
    }

    private fun applyMove(move: Move) {
        val cur = _state.value
        val next = cur.play(move) ?: return
        _state.value = next
        moves.add(move)
        val anim = AppConfig.Animation
        var totalMs = 0L
        if (next.lastFlips.isNotEmpty()) {
            val now = System.currentTimeMillis()
            var maxDelay = 0L
            val entries = next.lastFlips.map { (fx, fy) ->
                val dist = max(abs(fx - move.x), abs(fy - move.y))
                val d = (dist - 1).coerceAtLeast(0) * anim.FLIP_RIPPLE_PER_CELL_MS
                if (d > maxDelay) maxDelay = d
                Triple(fx, fy, d)
            }
            totalMs = maxDelay +
                anim.FLIP_FRAME_MS * anim.FLIP_FRAME_COUNT +
                anim.FLIP_CLEANUP_PAD_MS
            _flips.update { prev ->
                val running = prev.running.toMutableMap()
                for ((fx, fy, d) in entries) {
                    running[fy * 8 + fx] = FlipRun(now, d)
                }
                prev.copy(running = running, nowMs = now)
            }
            _animating.value = true
            viewModelScope.launch {
                delay(totalMs)
                _flips.update { prev ->
                    val running = prev.running.toMutableMap()
                    for ((fx, fy) in next.lastFlips) running.remove(fy * 8 + fx)
                    prev.copy(running = running)
                }
                _animating.value = false
                maybeRunBot()
            }
        } else {
            maybeRunBot()
        }
    }

    private fun maybeRunBot() {
        val cur = _state.value
        if (cur.isTerminal) return
        if (cur.legalMoves.isEmpty()) {
            viewModelScope.launch {
                delay(AppConfig.Bot.AUTO_PASS_DELAY_MS)
                val next = cur.play(Move.PASS) ?: return@launch
                moves.add(Move.PASS)
                _state.value = next
                maybeRunBot()
            }
            return
        }
        if (_players.value.of(cur.toMove) != Participant.BOT) return
        _botThinking.value = true
        botJob = viewModelScope.launch {
            val chosen = withContext(Dispatchers.Default) {
                val b = bot ?: Bot.loadDefault(getApplication()).also { bot = it }
                b.chooseMove(cur.board, cur.toMove)
            }
            _botThinking.value = false
            delay(AppConfig.Bot.THINKING_MIN_MS)
            applyMove(chosen)
        }
    }

    private fun modeFor(p: Players): SavedMode? = when {
        p.black == Participant.HUMAN && p.white == Participant.HUMAN -> SavedMode.TWO_PLAYER
        p.black == Participant.BOT || p.white == Participant.BOT -> SavedMode.VS_BOT
        else -> null
    }

    private fun humanColorIn(p: Players): Player? = when {
        p.black == Participant.HUMAN && p.white == Participant.BOT -> Player.BLACK
        p.white == Participant.HUMAN && p.black == Participant.BOT -> Player.WHITE
        else -> null
    }
}
