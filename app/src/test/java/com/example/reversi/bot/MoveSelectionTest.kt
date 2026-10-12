package com.example.reversi.bot

import com.example.reversi.config.AppConfig
import com.example.reversi.game.Board
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class MoveSelectionTest {

    private fun load(o: Opponent) =
        File("src/main/assets/${o.assetPath}").inputStream().use(ModelLoader::load)

    private fun bot(o: Opponent, seed: Long): Bot {
        val m = load(o)
        return Bot(m.evaluator, m.searchDepth, m.id, AppConfig.Bot.VARIETY.getValue(o), Random(seed))
    }

    /** Bot plays both sides for [plies] plies; returns the move sequence. */
    private fun selfPlay(bot: Bot, plies: Int): List<Move> {
        var board = Board()
        var p = Player.BLACK
        val out = ArrayList<Move>()
        repeat(plies) {
            if (board.isTerminal()) return out
            val m = bot.chooseMove(board, p)
            out.add(m)
            board = board.apply(m, p).first
            p = p.opponent()
        }
        return out
    }

    @Test
    fun strengthTapersToZero() {
        val s = MoveSelection(margin = 0.2f, temperature = 0.1f, variedPlies = 10, taperPlies = 4)
        assertEquals(1f, s.strengthAt(0))
        assertEquals(1f, s.strengthAt(9))
        assertEquals(1f, s.strengthAt(10))
        assertEquals(0.5f, s.strengthAt(12))
        assertEquals(0f, s.strengthAt(14))
        assertEquals(0f, s.strengthAt(60))
        assertEquals(0f, MoveSelection(0.2f, 0.1f, 10, 0).strengthAt(10))
    }

    @Test
    fun everyOpponentHasVariety() {
        for (o in Opponent.entries) {
            val openings = (0L until 20L).map { selfPlay(bot(o, it), 12) }.toSet()
            assertTrue("${o.name} produced only ${openings.size} distinct openings", openings.size >= 3)
        }
    }

    @Test
    fun sameSeedSameGame() {
        for (o in Opponent.entries) {
            assertEquals(selfPlay(bot(o, 42), 60), selfPlay(bot(o, 42), 60))
        }
    }

    @Test
    fun variedPickIsWithinMarginOfBest() {
        val m = load(Opponent.VISHY)
        val sel = AppConfig.Bot.VARIETY.getValue(Opponent.VISHY)
        val eval: Evaluator = { b, p -> m.evaluator.evaluate(b, p) }
        val search = AlphaBetaSearch(m.searchDepth)
        val rnd = Random(7)
        var board = Board()
        var p = Player.BLACK
        repeat(sel.variedPlies) {
            val moves = board.legalMoves(p)
            if (moves.isEmpty()) return
            val scores = moves.associateWith { mv ->
                -reference(board.apply(mv, p).first, p.opponent(), m.searchDepth - 1, eval)
            }
            val best = scores.values.max()
            repeat(5) { i ->
                val pick = search.chooseMove(board, p, eval, sel, Random(i.toLong()))
                assertTrue(scores.getValue(pick) >= best - sel.margin - 1e-4f)
            }
            board = board.apply(moves[rnd.nextInt(moves.size)], p).first
            p = p.opponent()
        }
    }

    /** Plain negamax without pruning, same leaf/terminal/pass rules as the search. */
    private fun reference(board: Board, p: Player, depth: Int, eval: Evaluator): Float {
        if (board.isTerminal()) {
            val diff = board.count(p.toCell()) - board.count(p.opponent().toCell())
            return if (diff > 0) 1000f + diff else if (diff < 0) -1000f + diff else 0f
        }
        if (depth <= 0) return eval(board, p)
        val moves = board.legalMoves(p)
        if (moves.isEmpty()) return -reference(board, p.opponent(), depth - 1, eval)
        return moves.maxOf { -reference(board.apply(it, p).first, p.opponent(), depth - 1, eval) }
    }
}
