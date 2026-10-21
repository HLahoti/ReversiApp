package com.example.reversi.analysis

import com.example.reversi.bot.Bot
import com.example.reversi.bot.ModelLoader
import com.example.reversi.bot.MoveSelection
import com.example.reversi.bot.Opponent
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Board
import com.example.reversi.game.Cell
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Not a test: a report for tuning [AppConfig.Analysis]. Plays the bundled
 * bots against each other, then writes solve times by empties, how many
 * discs each reviewed move gave away, how often each grade comes up, and
 * sample verdicts with their boards. Runs only when CALIBRATE names the file
 * to write:
 *
 *     CALIBRATE=/tmp/report.txt ./gradlew :app:testDebugUnitTest --tests '*EndgameCalibration*'
 */
class EndgameCalibration {

    private fun bot(o: Opponent, seed: Int): Bot {
        val m = File("src/main/assets/${o.assetPath}").inputStream().use(ModelLoader::load)
        val selection = AppConfig.Bot.VARIETY[o] ?: MoveSelection.DETERMINISTIC
        return Bot(m.evaluator, m.searchDepth, m.id, selection, Random(seed))
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

    @Test
    fun report() {
        val out = System.getenv("CALIBRATE")
        assumeTrue(out != null)
        val text = StringBuilder()
        fun line(s: String = "") { text.appendLine(s); println(s) }

        val pairings = listOf(
            Opponent.VISHY to Opponent.KONERU, Opponent.KONERU to Opponent.VISHY,
            Opponent.GUK to Opponent.ARJUN, Opponent.ARJUN to Opponent.GUK,
            Opponent.VISHY to Opponent.GUK, Opponent.PRAGG to Opponent.ARJUN,
        )
        val games = (System.getenv("GAMES")?.toInt() ?: 8).let { perPairing ->
            pairings.flatMapIndexed { i, (b, w) ->
                (0 until perPairing).map { g -> play(bot(b, i * 100 + g), bot(w, i * 100 + g + 50)) }
            }
        }
        line("${games.size} games")

        // Solve time by empties, over the positions these games reached.
        line("\n== Solve time (whole position, ms) ==")
        line("empties  positions  median   p90     max     nodes(median)")
        for (e in 10..18 step 2) {
            val times = ArrayList<Double>(); val nodes = ArrayList<Long>()
            for (g in games.take(24)) {
                val s = positionAt(g, e) ?: continue
                val solver = EndgameSolver()
                val t0 = System.nanoTime()
                solver.solve(s.board, s.toMove)
                times += (System.nanoTime() - t0) / 1e6
                nodes += solver.nodes
            }
            times.sort(); nodes.sort()
            if (times.isEmpty()) continue
            line("%7d  %9d  %6.1f  %6.1f  %6.1f  %d".format(
                e, times.size, times[times.size / 2], times[(times.size * 9) / 10], times.last(), nodes[nodes.size / 2],
            ))
        }

        // Whole-game review time at the configured depth.
        val reviewTimes = games.map { g ->
            val t0 = System.nanoTime(); EndgameReview.of(g); (System.nanoTime() - t0) / 1e6
        }.sorted()
        line("\nFull review at MAX_EMPTIES=${AppConfig.Analysis.MAX_EMPTIES}: median %.0f ms, max %.0f ms"
            .format(reviewTimes[reviewTimes.size / 2], reviewTimes.last()))

        val reviews = games.map { it to EndgameReview.of(it) }
        val verdicts = reviews.flatMap { it.second.verdicts.values }
        line("\n== Discs given away per reviewed move (${verdicts.size} moves) ==")
        val buckets = listOf(0..0, 1..2, 3..5, 6..9, 10..13, 14..19, 20..29, 30..128)
        for (b in buckets) {
            val n = verdicts.count { it.loss in b }
            line("%-7s %5d  %5.1f%%  %s".format(
                if (b.first == b.last) "${b.first}" else "${b.first}-${b.last}", n, 100.0 * n / verdicts.size, "#".repeat(n * 60 / verdicts.size),
            ))
        }
        val resultDrops = verdicts.count { Integer.signum(it.best) > Integer.signum(it.played) }
        line("Moves that made the result worse: $resultDrops")

        line("\n== Grades (scale ${AppConfig.Analysis.STAKE_SCALE_DISCS}, MISTAKE >= ${AppConfig.Analysis.MISTAKE_STAKE}, BLUNDER >= ${AppConfig.Analysis.BLUNDER_STAKE}) ==")
        for (grade in Grade.entries) {
            val n = verdicts.count { it.grade == grade }
            line("%-8s %4d moves (%.1f%%), %.2f per player per game".format(
                grade, n, 100.0 * n / verdicts.size, n / (2.0 * games.size),
            ))
        }
        line("Graded moves per player per game, by bot:")
        for (o in Opponent.entries) {
            val sides = pairings.withIndex().flatMap { (i, pr) ->
                val perPairing = games.size / pairings.size
                (0 until perPairing).flatMap { g ->
                    val review = reviews[i * perPairing + g].second
                    listOfNotNull(
                        if (pr.first == o) review to Player.BLACK else null,
                        if (pr.second == o) review to Player.WHITE else null,
                    )
                }
            }
            if (sides.isEmpty()) continue
            val m = sides.sumOf { (r, p) -> r.count(p, Grade.MISTAKE) }
            val b = sides.sumOf { (r, p) -> r.count(p, Grade.BLUNDER) }
            line("  %-7s mistakes %.2f  blunders %.2f  (%d games)".format(o.displayName, m.toDouble() / sides.size, b.toDouble() / sides.size, sides.size))
        }

        line("\n== Sample verdicts ==")
        val samples = verdicts.filter { it.grade != null }.shuffled(Random(7)).take(8) +
            verdicts.filter { it.grade == null && it.loss >= 8 }.shuffled(Random(8)).take(3)
        for (v in samples) {
            val game = reviews.first { v in it.second.verdicts.values }.first
            line(describe(game, v))
        }
        File(out).writeText(text.toString())
    }

    private fun positionAt(game: List<Move>, empties: Int): GameState? {
        var s = GameState()
        for (m in game) {
            if (64 - s.blackCount - s.whiteCount == empties) return s
            s = s.play(m)!!
        }
        return null
    }

    private fun describe(game: List<Move>, v: MoveVerdict): String {
        var s = GameState()
        for (m in game.take(v.ply)) s = s.play(m)!!
        val sq = { m: Move -> "${'a' + m.x}${m.y + 1}" }
        val sb = StringBuilder()
        sb.appendLine("Ply ${v.ply + 1}, ${v.mover.name.lowercase()} to move, ${64 - s.blackCount - s.whiteCount} empty: " +
            "played ${sq(v.move)} -> ${signed(v.played)}, best ${sq(v.bestMove)} -> ${signed(v.best)}, " +
            "loses ${v.loss}: ${v.grade ?: "not marked"}")
        sb.appendLine("   a b c d e f g h")
        for (y in 0 until 8) {
            sb.append(y + 1).append(' ')
            for (x in 0 until 8) {
                val c = when {
                    x == v.move.x && y == v.move.y -> 'P'
                    x == v.bestMove.x && y == v.bestMove.y && v.bestMove != v.move -> '*'
                    else -> when (s.board.at(x, y)) { Cell.BLACK -> 'X'; Cell.WHITE -> 'O'; Cell.EMPTY -> '.' }
                }
                sb.append(' ').append(c)
            }
            sb.appendLine()
        }
        return sb.append("   X black, O white, P played, * best").toString()
    }

    private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

    @Suppress("unused")
    private fun Board.empties() = 64 - count(Cell.BLACK) - count(Cell.WHITE)
}
