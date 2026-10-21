package com.example.reversi.analysis

import com.example.reversi.bot.Bot
import com.example.reversi.bot.ModelLoader
import com.example.reversi.bot.MoveSelection
import com.example.reversi.bot.Opponent
import com.example.reversi.config.AppConfig
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.random.Random

/**
 * Not a test: a report on whether the bots can grade moves the endgame solver
 * can't reach. In positions with few enough empties to solve, every legal move
 * is scored both exactly and by a bot's search; the bot's scores are mapped to
 * discs (fitted on half the games, checked on the other half) and graded with
 * the same rules as [EndgameReview], then compared with the exact grades.
 * Runs only when AGREEMENT names the file to write:
 *
 *     AGREEMENT=/tmp/agreement.txt ./gradlew :app:testDebugUnitTest --tests '*EngineAgreement*'
 */
class EngineAgreement {

    private class Engine(val name: String, val opponent: Opponent, val depth: Int)

    private val engines = listOf(
        Engine("Koneru d3", Opponent.KONERU, 3),
        Engine("Koneru d5", Opponent.KONERU, 5),
        Engine("Arjun d4", Opponent.ARJUN, 4),
    )

    /** One position: every legal move, its exact score, and each engine's score (mover's side). */
    private class Sample(
        val game: Int,
        val empties: Int,
        val moves: List<Move>,
        val exact: IntArray,
        val engine: List<FloatArray>,
    )

    private fun load(o: Opponent, depth: Int, variety: Boolean, seed: Int = 0): Bot {
        val m = File("src/main/assets/${o.assetPath}").inputStream().use(ModelLoader::load)
        val selection = if (variety) AppConfig.Bot.VARIETY[o] ?: MoveSelection.DETERMINISTIC else MoveSelection.DETERMINISTIC
        return Bot(m.evaluator, if (variety) m.searchDepth else depth, m.id, selection, Random(seed))
    }

    private fun play(black: Bot, white: Bot): List<Move> {
        var s = GameState()
        val moves = ArrayList<Move>()
        while (!s.isTerminal) {
            val m = (if (s.toMove == Player.BLACK) black else white).chooseMove(s.board, s.toMove)
            moves += m
            s = s.play(m)!!
        }
        return moves
    }

    /** A search score in discs when it reached the end of the game, else null. */
    private fun terminalDiscs(score: Float): Int? = when {
        score >= 500f -> (score - 1000f).toInt()
        score <= -500f -> (score + 1000f).toInt()
        else -> null
    }

    @Test
    fun report() {
        val out = System.getenv("AGREEMENT")
        assumeTrue(out != null)
        val text = StringBuilder()
        fun line(s: String = "") { text.appendLine(s); println(s) }

        val gamesWanted = System.getenv("GAMES")?.toInt() ?: 120
        val pairings = listOf(
            Opponent.VISHY to Opponent.KONERU, Opponent.KONERU to Opponent.GUK,
            Opponent.GUK to Opponent.ARJUN, Opponent.ARJUN to Opponent.PRAGG,
            Opponent.VISHY to Opponent.GUK, Opponent.PRAGG to Opponent.VISHY,
        )
        val games = (0 until gamesWanted).map { g ->
            val (b, w) = pairings[g % pairings.size]
            play(load(b, 0, true, g * 2), load(w, 0, true, g * 2 + 1))
        }
        val emptiesWanted = mapOf(14 to games.size, 16 to games.size, 18 to games.size / 3)
        line("${games.size} games; positions at ${emptiesWanted.entries.joinToString { "${it.key} empties (≤${it.value})" }}")

        val bots = engines.map { load(it.opponent, it.depth, false) }
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val engineMs = DoubleArray(engines.size)
        val jobs = ArrayList<Callable<Sample?>>()
        for ((gi, game) in games.withIndex()) {
            for ((empties, limit) in emptiesWanted) {
                if (gi >= limit) continue
                jobs += Callable {
                    var s = GameState()
                    for (m in game) {
                        if (64 - s.blackCount - s.whiteCount == empties) break
                        s = s.play(m)!!
                    }
                    if (64 - s.blackCount - s.whiteCount != empties || s.legalMoves.size < 2) return@Callable null
                    val moves = s.legalMoves
                    val (p, o) = EndgameSolver.bits(s.board, s.toMove)
                    val solver = EndgameSolver()
                    val exact = IntArray(moves.size) { solver.scoreAfter(p, o, EndgameSolver.square(moves[it]), -64, 64) }
                    val scores = bots.mapIndexed { ei, bot ->
                        val t0 = System.nanoTime()
                        FloatArray(moves.size) { bot.bestOf(s.board, s.toMove, listOf(moves[it]))!!.score }
                            .also { synchronized(engineMs) { engineMs[ei] += (System.nanoTime() - t0) / 1e6 } }
                    }
                    Sample(gi, empties, moves, exact, scores)
                }
            }
        }
        val samples = pool.invokeAll(jobs).mapNotNull { it.get() }
        pool.shutdown()
        val nMoves = samples.sumOf { it.moves.size }
        line("${samples.size} positions, $nMoves moves scored (every legal move, as if it had been played)")
        line("Engine time per position (one core): " + engines.indices.joinToString {
            "${engines[it].name} %.0f ms".format(engineMs[it] / samples.size)
        })

        // Moves the bots' own search scored as finished games are exact already.
        val train = samples.filter { it.game % 2 == 0 }
        val test = samples.filter { it.game % 2 == 1 }

        for ((ei, e) in engines.withIndex()) {
            line("\n================ ${e.name} ================")
            // Fit: score → discs, by matching quantiles (keeps the spread, unlike
            // averaging per bin, which shrinks every gap towards the middle).
            val evals = ArrayList<Float>(); val discs = ArrayList<Int>()
            for (s in train) for (i in s.moves.indices) {
                if (terminalDiscs(s.engine[ei][i]) == null) { evals += s.engine[ei][i]; discs += s.exact[i] }
            }
            val sortedE = evals.sorted(); val sortedD = discs.sorted()
            fun toDiscs(v: Float): Int {
                terminalDiscs(v)?.let { return it }
                var lo = 0; var hi = sortedE.size
                while (lo < hi) { val mid = (lo + hi) / 2; if (sortedE[mid] < v) lo = mid + 1 else hi = mid }
                return sortedD[lo.coerceAtMost(sortedD.size - 1)]
            }
            line("Score → discs (fitted on ${evals.size} moves): " +
                listOf(-0.9f, -0.5f, -0.2f, 0f, 0.2f, 0.5f, 0.9f).joinToString { "%.1f→%+d".format(it, toDiscs(it)) })

            for (empties in listOf(null) + emptiesWanted.keys) {
                val set = test.filter { empties == null || it.empties == empties }
                if (set.isEmpty()) continue
                // Rows: exact grade; columns: the engine's grade.
                val grid = Array(3) { IntArray(3) }
                var bestHit = 0; var bestLoss = 0; var bigMiss = 0
                var playedGrid = Array(3) { IntArray(3) }
                for (s in set) {
                    val exactBest = s.exact.max()
                    val est = IntArray(s.moves.size) { toDiscs(s.engine[ei][it]) }
                    val estBest = est.max()
                    val pick = s.engine[ei].indices.maxBy { s.engine[ei][it] }
                    if (s.exact[pick] == exactBest) bestHit++
                    bestLoss += exactBest - s.exact[pick]
                    if (EndgameReview.grade(s.exact[pick], exactBest) == Grade.BLUNDER) bigMiss++
                    for (i in s.moves.indices) {
                        val g = EndgameReview.grade(s.exact[i], exactBest)
                        val eg = EndgameReview.grade(est[i], estBest)
                        grid[idx(g)][idx(eg)]++
                    }
                }
                line("\n-- ${if (empties == null) "All held-out positions" else "$empties empties"}: ${set.size} positions, ${set.sumOf { it.moves.size }} moves --")
                line("               engine: none  mistake blunder")
                for (r in 0..2) line("exact %-8s %8d %8d %8d".format(names[r], grid[r][0], grid[r][1], grid[r][2]))
                val exactMarked = grid[1].sum() + grid[2].sum()
                val caught = grid[1][1] + grid[1][2] + grid[2][1] + grid[2][2]
                val engineMarked = (0..2).sumOf { grid[it][1] + grid[it][2] }
                val falseMarks = grid[0][1] + grid[0][2]
                line("Exact mistakes+blunders the engine marks at all: %.0f%%".format(100.0 * caught / exactMarked))
                line("Exact blunders the engine calls blunders:       %.0f%%".format(100.0 * grid[2][2] / grid[2].sum()))
                line("Engine marks that are wrong (exact: fine):      %.0f%%".format(100.0 * falseMarks / engineMarked))
                line("Engine blunders that exact calls fine:          %.0f%%".format(100.0 * grid[0][2] / (0..2).sumOf { grid[it][2] }))
                line("Engine's best move is exactly best:             %.0f%%  (gives away %.1f discs on average; a blunder %d times)"
                    .format(100.0 * bestHit / set.size, bestLoss.toDouble() / set.size, bigMiss))
            }
        }
        File(out).writeText(text.toString())
    }

    private val names = listOf("none", "mistake", "blunder")
    private fun idx(g: Grade?) = when (g) { null -> 0; Grade.MISTAKE -> 1; Grade.BLUNDER -> 2 }

    @Suppress("unused")
    private fun Int.abs() = abs(this)
}
