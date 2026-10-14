package com.example.reversi.bot.learning

import com.example.reversi.bot.AlphaBetaSearch
import com.example.reversi.bot.SearchEngine
import com.example.reversi.game.Board
import com.example.reversi.game.Cell
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Logistello's opening-book learning (M. Buro, "Toward Opening Book
 * Learning", ICCA Journal 1999), so the bot never loses the same way twice.
 *
 * Every finished game is added to a tree of positions (a DAG, since move
 * orders transpose, with mirrored positions merged by [BoardSymmetry]). Each
 * position before [horizonPlies] also stores its *deviation*: the best legal
 * move nobody has played there yet, scored by the engine's own search. A
 * game's final position holds the exact result.
 *
 * Positions get values by negamax over the book. At each node the side to
 * move takes the better of its known continuations and its deviation. A
 * decided result lies beyond ±1000 and heuristic scores lie within ±1, so:
 *  - a line the bot lost is worth less than any untried alternative, so the
 *    bot deviates from it at the last point where it had a choice;
 *  - a line the bot won is worth more than any alternative, so it repeats
 *    the win until the opponent finds a way to change course;
 *  - moves that won for the *opponent* score highly for the side that played
 *    them, so with colours reversed the bot copies the moves that beat it.
 *
 * In positions the book has never seen, or past [horizonPlies], the bot
 * plays its normal search.
 */
class OpeningBook(private val horizonPlies: Int) : LearningStrategy {

    private class Node(
        /** Played continuations: canonical move → child key. */
        val children: MutableMap<Int, String> = HashMap(),
        /** Exact score for the side to move, set only for a game's final position. */
        var result: Float? = null,
        /** Best unplayed move (canonical) and its search score, if computed. */
        var deviation: Int? = null,
        var deviationScore: Float = 0f,
        /** Key the deviation leads to; it goes stale once that key is played. */
        var deviationTo: String? = null,
        /** Every legal move has been played, so there's nothing left to deviate to. */
        var exhausted: Boolean = false,
    ) {
        fun hasLiveDeviation() = deviationTo != null && deviationTo !in children.values
        fun needsDeviation() = result == null && !exhausted && !hasLiveDeviation()
    }

    private var nodes = HashMap<String, Node>()
    private val values = HashMap<String, Float>()

    /** Finished games learned so far. */
    var gamesLearned = 0
        private set

    val size: Int get() = nodes.size

    override fun chooseMove(board: Board, player: Player, engine: SearchEngine): Move? {
        if (ply(board) >= horizonPlies) return null
        val (key, t) = BoardSymmetry.canonical(board, player)
        val node = nodes[key] ?: return null
        if (node.result != null) return null

        // Prefer a known continuation on a tie: it has actually been played.
        var bestMove: Int? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for ((m, child) in node.children) {
            val score = -value(child)
            if (bestMove == null || score > bestScore) { bestMove = m; bestScore = score }
        }
        if (node.hasLiveDeviation() && node.deviationScore > bestScore) {
            bestMove = node.deviation
        }
        return bestMove?.let { BoardSymmetry.fromCanonical(it, t) }
    }

    override fun learn(game: GameRecord, engine: SearchEngine) {
        // Replay the game. Anything illegal or unfinished is not a lesson.
        val positions = ArrayList<GameState>(game.moves.size + 1)
        var state = GameState()
        for (m in game.moves) {
            positions += state
            state = state.play(m) ?: return
        }
        if (!state.isTerminal) return
        positions += state

        val keys = positions.map { BoardSymmetry.canonical(it.board, it.toMove) }
        for ((i, m) in game.moves.withIndex()) {
            val node = nodes.getOrPut(keys[i].key) { Node() }
            node.children[BoardSymmetry.toCanonical(m, keys[i].transform)] = keys[i + 1].key
        }
        nodes.getOrPut(keys.last().key) { Node() }.result =
            AlphaBetaSearch.terminalScore(state.board, state.toMove)

        // Find a fresh deviation wherever this game used up the old one.
        for (i in game.moves.indices) {
            val pos = positions[i]
            if (ply(pos.board) >= horizonPlies) break
            val node = nodes.getValue(keys[i].key)
            if (node.needsDeviation()) findDeviation(node, pos.board, pos.toMove, keys[i].transform, engine)
        }
        values.clear()
        gamesLearned++
    }

    /** Score the best legal move whose resulting position isn't in [node] yet. */
    private fun findDeviation(node: Node, board: Board, player: Player, t: Int, engine: SearchEngine) {
        val played = node.children.values.toSet()
        val childKeys = HashMap<Move, String>()
        val seen = HashSet<String>()
        for (m in board.legalMoves(player)) {
            val key = BoardSymmetry.canonical(board.apply(m, player).first, player.opponent()).key
            // Mirror-image moves reach the same key; keep one of each.
            if (key !in played && seen.add(key)) childKeys[m] = key
        }
        val best = engine.bestOf(board, player, childKeys.keys.toList())
        if (best == null) {
            node.exhausted = true
            node.deviation = null
            node.deviationTo = null
        } else {
            node.deviation = BoardSymmetry.toCanonical(best.move, t)
            node.deviationScore = best.score
            node.deviationTo = childKeys.getValue(best.move)
        }
    }

    /** Negamax value of a book position for its side to move, memoised until the next [learn]. */
    private fun value(key: String): Float {
        values[key]?.let { return it }
        val node = nodes.getValue(key)
        val v = node.result ?: run {
            var best = Float.NEGATIVE_INFINITY
            for (child in node.children.values) best = maxOf(best, -value(child))
            if (node.hasLiveDeviation()) best = maxOf(best, node.deviationScore)
            best
        }
        values[key] = v
        return v
    }

    private fun ply(board: Board) = board.count(Cell.BLACK) + board.count(Cell.WHITE) - 4

    // ── Persistence ───────────────────────────────────────────────────────
    // Short field names: a book holds tens of thousands of positions.

    @Serializable
    private data class NodeDto(
        val c: Map<Int, String> = emptyMap(),
        val r: Float? = null,
        val d: Int? = null,
        val ds: Float = 0f,
        val dt: String? = null,
        val x: Boolean = false,
    )

    @Serializable
    private data class BookDto(
        val version: Int,
        val games: Int,
        val nodes: Map<String, NodeDto>,
    )

    override fun export(): String = json.encodeToString(
        BookDto.serializer(),
        BookDto(
            version = VERSION,
            games = gamesLearned,
            nodes = nodes.mapValues { (_, n) ->
                NodeDto(n.children, n.result, n.deviation, n.deviationScore, n.deviationTo, n.exhausted)
            },
        ),
    )

    override fun import(data: String) {
        val dto = json.decodeFromString(BookDto.serializer(), data)
        require(dto.version == VERSION) { "unsupported opening book version ${dto.version}" }
        val loaded = HashMap<String, Node>(dto.nodes.size * 2)
        for ((key, n) in dto.nodes) {
            loaded[key] = Node(n.c.toMutableMap(), n.r, n.d, n.ds, n.dt, n.x)
        }
        // Every edge must land on a stored node, or negamax would fail later.
        for (n in loaded.values) {
            require(n.children.values.all { it in loaded }) { "opening book has a dangling edge" }
        }
        nodes = loaded
        values.clear()
        gamesLearned = dto.games
    }

    companion object {
        private const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true }
    }
}
