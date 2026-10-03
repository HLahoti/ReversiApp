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

/** One row in the move log strip. */
data class MoveLogEntry(
    val label: String,
    val mover: Player,
    val black: Int,
    val white: Int,
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

    private val _log = MutableStateFlow<List<MoveLogEntry>>(emptyList())
    val log: StateFlow<List<MoveLogEntry>> = _log.asStateFlow()

    /** Which log entry is currently shown on the board (-1 = starting position). */
    private val _viewingIndex = MutableStateFlow(-1)
    val viewingIndex: StateFlow<Int> = _viewingIndex.asStateFlow()

    /** Current frame of the game-start intro, 1..FRAME_COUNT, or null when not playing. */
    private val _openingFrame = MutableStateFlow<Int?>(null)
    val openingFrame: StateFlow<Int?> = _openingFrame.asStateFlow()

    private var botJob: Job? = null
    private var openingJob: Job? = null
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
        playOpening()
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
        val entries = mutableListOf<MoveLogEntry>()
        for (m in saved.moves) {
            val mover = s.toMove
            val mv = if (m.isPass) Move.PASS else Move(m.x, m.y)
            val next = s.play(mv) ?: break
            moves.add(mv)
            entries += MoveLogEntry(labelFor(mv), mover, next.blackCount, next.whiteCount)
            s = next
        }
        _state.value = s
        _log.value = entries
        _viewingIndex.value = moves.size - 1
        maybeRunBot()
    }

    /**
     * Rewind the board to the state right after move [index] in the log,
     * without touching the stored move history. If the player then plays a
     * new move, [applyMove]/[passTurn] truncate everything past [index].
     */
    fun jumpTo(index: Int) {
        if (index < 0 || index >= moves.size) return
        if (index == _viewingIndex.value) return
        botJob?.cancel()
        _botThinking.value = false
        _flips.value = FlipAnimations()
        _animating.value = false
        var s = GameState()
        for (i in 0..index) s = s.play(moves[i]) ?: return
        _state.value = s
        _viewingIndex.value = index
        // No maybeRunBot(): the player drives from here. A new tap/pass
        // commits the branch; otherwise they are free to keep browsing.
    }

    fun resetGame() {
        botJob?.cancel()
        resetInternal()
        mode?.let(store::clear)
        playOpening()
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
        val mover = cur.toMove
        val next = cur.play(Move.PASS) ?: return
        truncateToViewing()
        moves.add(Move.PASS)
        _state.value = next
        appendLog(Move.PASS, mover, next)
        maybeRunBot()
    }

    private fun resetInternal() {
        botJob?.cancel()
        openingJob?.cancel()
        _openingFrame.value = null
        _state.value = GameState()
        _flips.value = FlipAnimations()
        _botThinking.value = false
        _animating.value = false
        moves.clear()
        _log.value = emptyList()
        _viewingIndex.value = -1
    }

    /**
     * Play the game-start intro frames, then hand off to normal play.
     * The opening animation reuses [_animating] to gate taps and the bot
     * until the board has settled into its starting position.
     */
    private fun playOpening() {
        openingJob?.cancel()
        val cfg = AppConfig.OpeningAnimation
        _animating.value = true
        _openingFrame.value = 1
        openingJob = viewModelScope.launch {
            for (i in 1..cfg.FRAME_COUNT) {
                _openingFrame.value = i
                delay(cfg.FRAME_MS)
            }
            _openingFrame.value = null
            _animating.value = false
            maybeRunBot()
        }
    }

    /** If the player is viewing a historical state, drop every ply after it. */
    private fun truncateToViewing() {
        val idx = _viewingIndex.value
        if (idx >= moves.size - 1) return
        while (moves.size > idx + 1) moves.removeAt(moves.size - 1)
        _log.update { it.take(idx + 1) }
    }

    private fun appendLog(move: Move, mover: Player, next: GameState) {
        _log.update { it + MoveLogEntry(labelFor(move), mover, next.blackCount, next.whiteCount) }
        _viewingIndex.value = moves.size - 1
    }

    private fun labelFor(move: Move): String =
        if (move.isPass) "—" else "${'A' + move.x}${move.y + 1}"

    private fun applyMove(move: Move) {
        val cur = _state.value
        val mover = cur.toMove
        val next = cur.play(move) ?: return
        truncateToViewing()
        _state.value = next
        moves.add(move)
        appendLog(move, mover, next)
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
                val mover = cur.toMove
                val next = cur.play(Move.PASS) ?: return@launch
                moves.add(Move.PASS)
                _state.value = next
                appendLog(Move.PASS, mover, next)
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
