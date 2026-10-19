package com.example.reversi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reversi.bot.BotPlayer
import com.example.reversi.bot.Bots
import com.example.reversi.bot.Opponent
import com.example.reversi.bot.learning.GameRecord
import com.example.reversi.bot.learning.LearningScope
import com.example.reversi.config.AppConfig
import com.example.reversi.data.GameStore
import com.example.reversi.data.SavedGame
import com.example.reversi.data.SavedMode
import com.example.reversi.data.SavedMove
import com.example.reversi.data.UserSettings
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import com.example.reversi.history.HistoryEntry
import com.example.reversi.history.HistoryMode
import com.example.reversi.history.HistoryMoves
import com.example.reversi.history.HistoryOutcome
import com.example.reversi.history.HistoryRegistry
import com.example.reversi.history.HistoryStatus
import com.example.reversi.net.OnlineScope
import com.example.reversi.net.OnlineSession
import com.example.reversi.net.SessionState
import com.example.reversi.net.SessionStatus
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who plays each colour. */
enum class Participant {
    /** A seat driven by the user holding this device. */
    HUMAN,
    /** A seat driven by the local bot. */
    BOT,
    /** A seat driven by another device, delivered via [OnlineSession]. */
    REMOTE,
}

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

/** Snapshot of the online layer surfaced to the UI (nullable when offline). */
data class OnlineUiState(
    val status: SessionStatus,
    val opponentName: String?,
    val error: String?,
    /** We've pressed "Play again" and are waiting on the opponent. */
    val rematchRequested: Boolean = false,
    /** The opponent has pressed "Play again" and is waiting on us. */
    val opponentWantsRematch: Boolean = false,
    /** The opponent left and the room is gone; no more moves or rematches. */
    val opponentLeft: Boolean = false,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GameStore(app)
    private val history = HistoryRegistry.current

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

    /** The colour that resigned this game, or null. Ends the game like a terminal board. */
    private val _resigned = MutableStateFlow<Player?>(null)
    val resigned: StateFlow<Player?> = _resigned.asStateFlow()

    /** The move the bot suggests for the human, or null. Cleared once the board changes. */
    private val _hint = MutableStateFlow<Move?>(null)
    val hint: StateFlow<Move?> = _hint.asStateFlow()

    /** True while a replay is stepping through its moves by itself. */
    private val _replayPlaying = MutableStateFlow(false)
    val replayPlaying: StateFlow<Boolean> = _replayPlaying.asStateFlow()

    /** Null when playing offline; populated in online mode. */
    private val _online = MutableStateFlow<OnlineUiState?>(null)
    val online: StateFlow<OnlineUiState?> = _online.asStateFlow()

    private var botJob: Job? = null
    private var hintJob: Job? = null
    private var openingJob: Job? = null
    private var sessionJob: Job? = null
    private var replayJob: Job? = null
    private var bot: BotPlayer? = null
    private var opponent: Opponent? = null

    /** Every ply that has been applied, in order. Replayed on resume, written on persist. */
    private val moves = mutableListOf<Move>()

    /** The finished game last handed to the bot, so each game is reported once. */
    private var reportedGame: List<Move>? = null

    /** Set once configure/resume has run, so persist() knows which file to write. */
    private var mode: SavedMode? = null

    /**
     * This game's entry in the game history, from its first move. Null
     * before then, and always for two-player games, which aren't recorded.
     */
    private var historyId: String? = null

    /**
     * Showing a finished game from the history: [moves] holds the whole game
     * and the board steps through it, but nothing can be played, saved or
     * recorded.
     */
    private var isReplay = false

    // ── Online-mode fields ────────────────────────────────────────────────
    /**
     * True for the whole life of an online game, including after it ends or
     * the opponent leaves. Kept apart from [session] (the live connection),
     * which can go away while the screen is still showing the online game —
     * offline-only behaviour such as rewinding must stay off regardless.
     */
    private var isOnline = false
    private var session: OnlineSession? = null
    private var localColor: Player? = null
    /** Which game in the room the board is showing; see [SessionState.round]. */
    private var onlineRound = 0
    /** "Play again" was pressed and the request hasn't shown up in a snapshot yet. */
    private var rematchPending = false
    /**
     * Set when the room's move list stops matching the local game (an illegal
     * or out-of-turn move arrived). No further remote moves are applied.
     */
    private var desyncError: String? = null

    fun configure(players: Players, opponent: Opponent? = null) {
        abandonHistory()
        _players.value = players
        mode = modeFor(players)
        setOpponent(opponent)
        resetInternal()
        mode?.let { m ->
            // A new game replaces this bot's paused one, which can't be resumed now.
            if (m == SavedMode.VS_BOT) {
                store.load(m, slotOpponent())?.historyId?.let(history::abandon)
            }
            store.clear(m, slotOpponent())
        }
        playOpening()
    }

    /**
     * Replay [saved] into the state. The caller is responsible for picking
     * `players` consistent with the save (two-human for a two-player save,
     * one-bot with [SavedGame.humanColor] as the human for a vs-bot save).
     * [opponent] is the slot the save was loaded from; it falls back to the
     * one recorded in the save.
     */
    fun resume(saved: SavedGame, players: Players, opponent: Opponent? = null) {
        _players.value = players
        mode = modeFor(players)
        setOpponent(opponent ?: Opponent.fromSaved(saved.difficulty))
        resetInternal()
        historyId = saved.historyId
        _state.value = loadMoves(saved.moves.map { if (it.isPass) Move.PASS else Move(it.x, it.y) })
        _viewingIndex.value = moves.size - 1
        maybeRunBot()
    }

    /**
     * Show [record], a finished game from the history, to step through.
     * The board starts at the opening position with the whole game in the
     * move log; [stepReplay], [replayTo] and [toggleReplayPlay] move through
     * it. Taps, passes and everything else that would change the game are
     * ignored.
     */
    fun configureReplay(record: List<Move>) {
        isReplay = true
        mode = null
        _players.value = Players(Participant.REMOTE, Participant.REMOTE)
        resetInternal()
        loadMoves(record)
        _state.value = GameState()
        _viewingIndex.value = -1
    }

    /**
     * One ply forward, with the usual flip animation, or one back. Ignored
     * while a flip is still playing out, so taps can't outrun the board.
     */
    fun stepReplay(forward: Boolean) {
        if (!isReplay || _animating.value) return
        stopReplayPlay()
        if (forward) stepReplayForward() else replayTo(_viewingIndex.value - 1)
    }

    /** Jump straight to the position after ply [index]; -1 is the opening position. */
    fun replayTo(index: Int) {
        if (!isReplay) return
        stopReplayPlay()
        if (index !in -1 until moves.size) return
        _flips.value = FlipAnimations()
        _animating.value = false
        var s = GameState()
        for (i in 0..index) s = s.play(moves[i]) ?: return
        _state.value = s
        _viewingIndex.value = index
    }

    /** Play the rest of the game by itself (from the start if at the end), or stop. */
    fun toggleReplayPlay() {
        if (!isReplay) return
        if (_replayPlaying.value) {
            stopReplayPlay()
            return
        }
        if (_viewingIndex.value >= moves.size - 1) replayTo(-1)
        _replayPlaying.value = true
        replayJob = viewModelScope.launch {
            while (_viewingIndex.value < moves.size - 1) {
                delay(stepReplayForward() + AppConfig.Replay.STEP_MS)
            }
            _replayPlaying.value = false
        }
    }

    private fun stopReplayPlay() {
        replayJob?.cancel()
        replayJob = null
        _replayPlaying.value = false
    }

    /** Animate the next ply of the replay onto the board; returns how long its flips take. */
    private fun stepReplayForward(): Long {
        val i = _viewingIndex.value + 1
        if (i >= moves.size) return 0L
        val move = moves[i]
        val next = _state.value.play(move) ?: return 0L
        _state.value = next
        _viewingIndex.value = i
        return animateFlips(move, next) {}
    }

    /**
     * Fill [moves] and the move log from [record], stopping at the first
     * illegal ply, and return the position after the last one.
     */
    private fun loadMoves(record: List<Move>): GameState {
        var s = GameState()
        val entries = mutableListOf<MoveLogEntry>()
        for (mv in record) {
            val mover = s.toMove
            val next = s.play(mv) ?: break
            moves.add(mv)
            entries += MoveLogEntry(labelFor(mv), mover, next.blackCount, next.whiteCount)
            s = next
        }
        _log.value = entries
        return s
    }

    /**
     * Switch this view-model into online mode: taps for [localColor] route
     * through [session], and all moves in the room (ours and theirs) replay
     * through the normal engine as they arrive.
     *
     * The view-model takes ownership of [session] and will close it on
     * [onCleared], [leaveOnline], or an explicit [resetGame] call.
     */
    fun configureOnline(session: OnlineSession, localColor: Player) {
        isOnline = true
        this.session = session
        this.localColor = localColor
        onlineRound = session.state.value.round
        rematchPending = false
        desyncError = null
        historyId = null
        _players.value = Players(
            black = if (localColor == Player.BLACK) Participant.HUMAN else Participant.REMOTE,
            white = if (localColor == Player.WHITE) Participant.HUMAN else Participant.REMOTE,
        )
        mode = null // online games are not persisted locally
        setOpponent(null)
        resetInternal()
        _online.value = OnlineUiState(
            status = session.state.value.status,
            opponentName = session.state.value.opponent?.displayName,
            error = session.state.value.error,
        )
        subscribeToSession(session)
        playOpening()
    }

    /**
     * Cleanly end the current online game, deleting the room on the backend
     * so neither side's device is left tracking an orphan. Idempotent.
     *
     * Dispatched on [OnlineScope] (not [viewModelScope]) so the finish
     * survives the VM being cleared during the same back-navigation that
     * triggered this call.
     */
    fun leaveOnline() {
        abandonHistory()
        isOnline = false
        val s = session ?: return
        sessionJob?.cancel()
        sessionJob = null
        OnlineScope.launch { runCatching { s.finish() } }
        session = null
        localColor = null
        _online.value = null
    }

    /**
     * "Play again" in an online game: ask the opponent for a rematch. The
     * board resets once both players have asked, when the session reports
     * the next round (see [onSessionSnapshot]).
     */
    fun requestRematch() {
        val s = session ?: return
        if (!isGameOver() || rematchPending) return
        val round = onlineRound
        rematchPending = true
        _online.update { it?.copy(rematchRequested = true) }
        viewModelScope.launch {
            runCatching { s.requestRematch(round) }
                .onFailure { t ->
                    rematchPending = false
                    _online.update {
                        it?.copy(rematchRequested = false, error = t.message ?: "Could not request a rematch.")
                    }
                }
        }
    }

    /**
     * Forfeit the game as the human seat. Vs-bot it ends at once; online the
     * room records it so the opponent's board ends too. Not offered in
     * pass-and-play, where there is no single human seat.
     */
    fun resign() {
        if (isReplay || isGameOver()) return
        if (isOnline) {
            val s = session ?: return
            val me = localColor ?: return
            val round = onlineRound
            _resigned.value = me
            recordHistory()
            viewModelScope.launch {
                runCatching { s.resign(round) }
                    .onFailure { t ->
                        if (onlineRound == round) {
                            _resigned.value = null
                            recordHistory()
                        }
                        _online.update { it?.copy(error = t.message ?: "Could not resign.") }
                    }
            }
            return
        }
        val human = humanColorIn(_players.value) ?: return
        botJob?.cancel()
        clearHint()
        _botThinking.value = false
        _resigned.value = human
        recordHistory()
        mode?.let { store.clear(it, slotOpponent()) }
    }

    /**
     * Vs-bot only: ask the bot being played for a move on the human's
     * behalf. It is shown the board from the human's side, as if it were
     * the bot's own turn, so it picks the move it would make there.
     */
    fun requestHint() {
        if (isOnline || isReplay || isGameOver()) return
        val human = humanColorIn(_players.value) ?: return
        if (_botThinking.value || _animating.value || hintJob?.isActive == true) return
        val cur = _state.value
        if (cur.toMove != human || cur.legalMoves.isEmpty()) return
        val o = slotOpponent()
        hintJob = viewModelScope.launch {
            val chosen = withContext(Dispatchers.Default) {
                val b = bot ?: Bots.load(getApplication(), o).also { bot = it }
                b.chooseMove(cur.board, human)
            }
            // Drop it if the board moved on while the bot was thinking.
            if (_state.value === cur && !chosen.isPass) _hint.value = chosen
        }
    }

    private fun clearHint() {
        hintJob?.cancel()
        _hint.value = null
    }

    private fun isGameOver(): Boolean = _state.value.isTerminal || _resigned.value != null

    override fun onCleared() {
        // If leaveOnline() wasn't called explicitly (e.g. activity killed by
        // the system), tear down listeners but leave the room — the next app
        // launch sweeps whatever's still in the tracker.
        session?.let { s ->
            sessionJob?.cancel()
            s.close()
        }
        super.onCleared()
    }

    private fun subscribeToSession(session: OnlineSession) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            session.state.collectLatest { snapshot -> onSessionSnapshot(snapshot) }
        }
    }

    /**
     * Project a session snapshot onto the local game. Any new moves (beyond
     * what we've already applied) are played through the engine in order;
     * our own echoes are naturally dropped because [moves.size] already
     * covers them.
     */
    private fun onSessionSnapshot(snapshot: SessionState) {
        if (snapshot.status == SessionStatus.ABANDONED) {
            // The opponent left and deleted the room. Stay on the board, but
            // drop the connection; finish() also clears the tracker entry.
            abandonHistory()
            sessionJob?.cancel()
            sessionJob = null
            session?.let { s -> OnlineScope.launch { runCatching { s.finish() } } }
            session = null
            rematchPending = false
            _online.value = OnlineUiState(
                status = snapshot.status,
                opponentName = snapshot.opponent?.displayName,
                error = snapshot.error,
                opponentLeft = true,
            )
            return
        }
        if (snapshot.round != onlineRound) {
            // Both players asked for a rematch: start the new round's board.
            onlineRound = snapshot.round
            rematchPending = false
            desyncError = null
            historyId = null // the finished round keeps its entry; the next gets its own
            snapshot.myColor?.let { color ->
                localColor = color
                _players.value = Players(
                    black = if (color == Player.BLACK) Participant.HUMAN else Participant.REMOTE,
                    white = if (color == Player.WHITE) Participant.HUMAN else Participant.REMOTE,
                )
            }
            resetInternal()
            playOpening()
        }
        // Keep an optimistic local resignation until the room confirms it.
        snapshot.resignedBy?.let {
            val changed = _resigned.value != it
            _resigned.value = it
            if (changed) recordHistory()
        }
        if (snapshot.rematchRequestedByMe) rematchPending = false
        _online.value = OnlineUiState(
            status = snapshot.status,
            opponentName = snapshot.opponent?.displayName,
            error = snapshot.error ?: desyncError,
            rematchRequested = snapshot.rematchRequestedByMe || rematchPending,
            opponentWantsRematch = snapshot.rematchRequestedByOpponent,
        )
        // Apply any moves we haven't seen yet, in order.
        if (desyncError == null && snapshot.moves.size > moves.size) {
            for (i in moves.size until snapshot.moves.size) {
                val rm = snapshot.moves[i]
                val expectedSeq = i + 1
                if (rm.seq != expectedSeq) {
                    // Out-of-order write; the next snapshot should repair it.
                    return
                }
                val cur = _state.value
                if (rm.by != cur.toMove || cur.play(rm.toMove()) == null) {
                    // The room no longer matches this board. Stop here rather
                    // than apply later moves on top of the wrong position.
                    desyncError = "This game is out of sync with the room. Leave and start a new one."
                    _online.update { it?.copy(error = desyncError) }
                    return
                }
                applyMove(rm.toMove(), broadcast = false)
            }
        }
    }

    /** The bot this game is played against, which also picks its save slot. */
    private fun slotOpponent(): Opponent = opponent ?: Opponent.DEFAULT

    private fun setOpponent(o: Opponent?) {
        if (o != opponent) {
            opponent = o
            bot = null
        }
    }

    /**
     * Rewind the board to the state right after move [index] in the log,
     * without touching the stored move history. If the player then plays a
     * new move, [applyMove]/[passTurn] truncate everything past [index].
     *
     * No-op in online mode: history is shared across devices and cannot be
     * rewound unilaterally.
     */
    fun jumpTo(index: Int) {
        if (isReplay) return replayTo(index)
        if (isOnline) return
        if (index < 0 || index >= moves.size) return
        if (index == _viewingIndex.value) return
        botJob?.cancel()
        clearHint()
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

    /**
     * Reset the local game. In online mode a fresh game needs both sides to
     * agree, so this asks for a rematch instead.
     */
    fun resetGame() {
        if (isReplay) return
        if (isOnline) {
            requestRematch()
            return
        }
        botJob?.cancel()
        abandonHistory()
        resetInternal()
        mode?.let { store.clear(it, slotOpponent()) }
        playOpening()
    }

    /**
     * "Play again" after a finished game: start a new one with the colours
     * swapped, so the first move alternates. Online, the rematch swaps the
     * room's colours once both players agree.
     */
    fun playAgain() {
        if (isReplay) return
        if (isOnline) {
            requestRematch()
            return
        }
        _players.update { Players(black = it.white, white = it.black) }
        resetGame()
    }

    /**
     * Call on back / app pause. Writes the current unfinished game to the
     * matching save slot, or clears it if there is nothing worth resuming
     * (no moves yet, or the game is already over). No-op online.
     */
    fun persist() {
        if (isOnline || isReplay) return
        val m = mode ?: return
        if (moves.isEmpty() || isGameOver()) {
            store.clear(m, slotOpponent())
            return
        }
        val saved = moves.map { SavedMove(it.x, it.y, it.isPass) }
        when (m) {
            SavedMode.TWO_PLAYER -> store.writeTwoPlayer(saved)
            SavedMode.VS_BOT -> {
                val human = humanColorIn(_players.value) ?: return
                store.writeVsBot(saved, human, slotOpponent(), historyId)
            }
        }
    }

    fun onTapCell(x: Int, y: Int) {
        if (isReplay) return
        if (_resigned.value != null) return
        if (_botThinking.value) return
        if (_animating.value) return
        val cur = _state.value
        if (_players.value.of(cur.toMove) != Participant.HUMAN) return
        if (isOnline && (session == null || cur.toMove != localColor)) return
        val mv = Move(x, y)
        if (!cur.board.isLegal(mv, cur.toMove)) return
        applyMove(mv)
    }

    fun passTurn() {
        if (isReplay) return
        if (_resigned.value != null) return
        val cur = _state.value
        if (cur.legalMoves.isNotEmpty()) return
        if (isOnline && (session == null || cur.toMove != localColor)) return
        val mover = cur.toMove
        val next = cur.play(Move.PASS) ?: return
        clearHint()
        truncateToViewing()
        moves.add(Move.PASS)
        _state.value = next
        appendLog(Move.PASS, mover, next)
        broadcastIfOnline(mover, Move.PASS)
        maybeRunBot()
    }

    private fun resetInternal() {
        botJob?.cancel()
        stopReplayPlay()
        clearHint()
        openingJob?.cancel()
        _openingFrame.value = null
        _state.value = GameState()
        _flips.value = FlipAnimations()
        _botThinking.value = false
        _animating.value = false
        moves.clear()
        _resigned.value = null
        reportedGame = null
        _log.value = emptyList()
        _viewingIndex.value = -1
    }

    /**
     * Play the game-start intro frames, then hand off to normal play.
     * The opening animation reuses [_animating] to gate taps and the bot
     * until the board has settled into its starting position. Skipped
     * straight to normal play when the intro is turned off in Settings.
     */
    private fun playOpening() {
        openingJob?.cancel()
        if (!UserSettings.openingAnimation.value) {
            _openingFrame.value = null
            _animating.value = false
            maybeRunBot()
            return
        }
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
        recordHistory()
    }

    /**
     * Write this game as it stands to the game history: called after every
     * move and resignation. Two-player games aren't recorded.
     */
    private fun recordHistory() {
        val kind = when {
            isOnline -> HistoryMode.ONLINE
            mode == SavedMode.VS_BOT -> HistoryMode.BOT
            else -> return
        }
        val me = (if (isOnline) localColor else humanColorIn(_players.value)) ?: return
        if (moves.isEmpty()) return
        val now = System.currentTimeMillis()
        val id = historyId ?: UUID.randomUUID().toString().also { historyId = it }
        val s = _state.value
        val resignedBy = _resigned.value
        val mine = if (me == Player.BLACK) s.blackCount else s.whiteCount
        val theirs = if (me == Player.BLACK) s.whiteCount else s.blackCount
        val outcome = when {
            resignedBy != null -> if (resignedBy == me) HistoryOutcome.LOSS else HistoryOutcome.WIN
            !s.isTerminal -> null
            mine > theirs -> HistoryOutcome.WIN
            mine < theirs -> HistoryOutcome.LOSS
            else -> HistoryOutcome.DRAW
        }
        history.record(
            HistoryEntry(
                id = id,
                mode = kind,
                myColor = me,
                opponentName = if (isOnline) _online.value?.opponentName else slotOpponent().displayName,
                botId = if (isOnline) null else slotOpponent().name,
                status = if (outcome == null) HistoryStatus.IN_PROGRESS else HistoryStatus.COMPLETED,
                outcome = outcome,
                resignedBy = resignedBy,
                blackCount = s.blackCount,
                whiteCount = s.whiteCount,
                moveCount = moves.size,
                startedAt = now,
                updatedAt = now,
                endedAt = if (outcome == null) null else now,
                moves = if (outcome == null) null else HistoryMoves.encode(moves),
            ),
        )
    }

    /** The game stops here; if it hadn't ended, its history entry is abandoned. */
    private fun abandonHistory() {
        historyId?.let { if (!isGameOver()) history.abandon(it) }
        historyId = null
    }

    private fun labelFor(move: Move): String =
        if (move.isPass) "—" else "${'A' + move.x}${move.y + 1}"

    /**
     * Apply [move] to the live board, run the flip animation, and (unless
     * [broadcast] is false) publish it over the online session.
     *
     * `broadcast = false` is used when we're replaying a move that already
     * came in through the session listener — we must not round-trip it back.
     */
    private fun applyMove(move: Move, broadcast: Boolean = true) {
        val cur = _state.value
        val mover = cur.toMove
        val next = cur.play(move) ?: return
        clearHint()
        truncateToViewing()
        _state.value = next
        moves.add(move)
        appendLog(move, mover, next)
        if (broadcast) broadcastIfOnline(mover, move)
        animateFlips(move, next) { maybeRunBot() }
    }

    /**
     * Run [move]'s flip animation, now on the board as [next], rippling out
     * from the placed disc; [onDone] runs once it has played out (at once if
     * nothing flipped). Returns how long it takes.
     */
    private fun animateFlips(move: Move, next: GameState, onDone: () -> Unit): Long {
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
                onDone()
            }
        } else {
            onDone()
        }
        return totalMs
    }

    /** Fire-and-forget send of a locally-applied move to the room. */
    private fun broadcastIfOnline(by: Player, move: Move) {
        val s = session ?: return
        val seq = moves.size // moves already contains this move; seq is 1-based.
        val round = onlineRound
        viewModelScope.launch {
            runCatching { s.sendMove(round, seq, by, move) }
                .onFailure { t ->
                    // The room refuses moves once the opponent has resigned.
                    if (_resigned.value != null) return@onFailure
                    _online.update { prev ->
                        prev?.copy(error = t.message ?: "Could not send move.")
                    }
                }
        }
    }

    private fun maybeRunBot() {
        if (isReplay || _resigned.value != null) return
        val cur = _state.value
        if (cur.isTerminal) {
            reportFinishedGame()
            return
        }

        // Auto-pass when the side-to-move has no legal moves. In online mode
        // we only ever auto-pass for ourselves — the opponent's auto-pass
        // reaches us through the session as a normal move.
        if (cur.legalMoves.isEmpty()) {
            val shouldAutoPass = when {
                isOnline -> session != null && cur.toMove == localColor
                else -> true
            }
            if (!shouldAutoPass) return
            viewModelScope.launch {
                delay(AppConfig.Bot.AUTO_PASS_DELAY_MS)
                if (_resigned.value != null) return@launch
                val mover = cur.toMove
                val next = cur.play(Move.PASS) ?: return@launch
                moves.add(Move.PASS)
                _state.value = next
                appendLog(Move.PASS, mover, next)
                broadcastIfOnline(mover, Move.PASS)
                maybeRunBot()
            }
            return
        }
        if (_players.value.of(cur.toMove) != Participant.BOT) return
        _botThinking.value = true
        val o = slotOpponent()
        botJob = viewModelScope.launch {
            val chosen = withContext(Dispatchers.Default) {
                val b = bot ?: Bots.load(getApplication(), o).also { bot = it }
                b.chooseMove(cur.board, cur.toMove)
            }
            _botThinking.value = false
            delay(AppConfig.Bot.THINKING_MIN_MS)
            if (_resigned.value != null) return@launch
            applyMove(chosen)
        }
    }

    /**
     * Hand a finished vs-bot game to its bot, so a learning bot can study it.
     * Runs on [LearningScope] so learning outlives this screen. A game is
     * reported once; rewinding and finishing a different line reports again.
     */
    private fun reportFinishedGame() {
        if (isOnline) return
        val botColor = humanColorIn(_players.value)?.opponent() ?: return
        if (reportedGame == moves) return
        val record = GameRecord(moves.toList(), botColor)
        reportedGame = record.moves
        val app = getApplication<Application>()
        val o = slotOpponent()
        LearningScope.launch {
            runCatching { Bots.load(app, o).onGameFinished(record) }
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
