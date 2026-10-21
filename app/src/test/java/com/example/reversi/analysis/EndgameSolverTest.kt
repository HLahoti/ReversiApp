package com.example.reversi.analysis

import com.example.reversi.game.Board
import com.example.reversi.game.Cell
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class EndgameSolverTest {

    /** A random game from the opening, stopped with [empties] squares left (or at its end). */
    private fun randomPosition(rnd: Random, empties: Int): GameState {
        var s = GameState()
        while (!s.isTerminal && 64 - s.blackCount - s.whiteCount > empties) {
            s = s.play(s.legalMoves.randomOrNull(rnd) ?: Move.PASS)!!
        }
        return s
    }

    /** Plain minimax over [Board], no pruning: slow, but obviously right. */
    private fun bruteForce(board: Board, mover: Player): Int {
        val moves = board.legalMoves(mover)
        if (moves.isEmpty()) {
            if (!board.hasAnyMove(mover.opponent())) {
                return board.count(mover.toCell()) - board.count(mover.opponent().toCell())
            }
            return -bruteForce(board, mover.opponent())
        }
        return moves.maxOf { -bruteForce(board.apply(it, mover).first, mover.opponent()) }
    }

    @Test
    fun movesAndFlipsMatchTheBoard() {
        val rnd = Random(1)
        repeat(300) {
            val s = randomPosition(rnd, rnd.nextInt(0, 60))
            val (p, o) = EndgameSolver.bits(s.board, s.toMove)
            val legal = s.legalMoves.map(EndgameSolver::square).toSet()
            val generated = (0 until 64).filter { (EndgameSolver.movesOf(p, o) ushr it) and 1L == 1L }.toSet()
            assertEquals(legal, generated)
            for (m in s.legalMoves) {
                val (next, flipped) = s.board.apply(m, s.toMove)
                val f = EndgameSolver.flips(EndgameSolver.square(m), p, o)
                assertEquals(flipped.map { (x, y) -> y * 8 + x }.toSet(), (0 until 64).filter { (f ushr it) and 1L == 1L }.toSet())
                val (np, _) = EndgameSolver.bits(next, s.toMove)
                assertEquals(np, p or f or (1L shl EndgameSolver.square(m)))
            }
        }
    }

    @Test
    fun solvesLikeBruteForce() {
        val rnd = Random(2)
        repeat(150) {
            val s = randomPosition(rnd, rnd.nextInt(1, 10))
            assertEquals(bruteForce(s.board, s.toMove), EndgameSolver().solve(s.board, s.toMove))
        }
    }

    @Test
    fun aFinishedBoardScoresItsDiscs() {
        val s = randomPosition(Random(3), 0)
        val me = s.board.count(Cell.BLACK) - s.board.count(Cell.WHITE)
        assertEquals(me, EndgameSolver().solve(s.board, Player.BLACK))
    }

    @Test
    fun reviewScoresMatchBruteForceAndPickABestMove() {
        val rnd = Random(4)
        repeat(40) {
            // A random game to the end, reviewed over its last 9 empties.
            var s = GameState()
            val moves = ArrayList<Move>()
            while (!s.isTerminal) {
                val m = s.legalMoves.randomOrNull(rnd) ?: Move.PASS
                moves += m
                s = s.play(m)!!
            }
            val review = EndgameReview.of(moves, maxEmpties = 9)
            var state = GameState()
            for ((ply, m) in moves.withIndex()) {
                review.verdicts[ply]?.let { v ->
                    val after = { mv: Move -> -bruteForce(state.board.apply(mv, state.toMove).first, state.toMove.opponent()) }
                    assertEquals(after(m), v.played)
                    val best = state.legalMoves.maxOf(after)
                    assertEquals(best, v.best)
                    assertEquals(best, after(v.bestMove))
                }
                state = state.play(m)!!
            }
        }
    }

    @Test
    fun gradesByWhatTheDiscsAreWorth() {
        // The same 8 discs: a mistake in a close game, nothing once it's decided.
        assertEquals(Grade.MISTAKE, EndgameReview.grade(played = 4, best = 12))
        assertEquals(Grade.MISTAKE, EndgameReview.grade(played = 2, best = 16))
        assertNull(EndgameReview.grade(played = 26, best = 34))
        assertNull(EndgameReview.grade(played = -42, best = -34))
        assertNull(EndgameReview.grade(played = 10, best = 12))
        assertEquals(Grade.BLUNDER, EndgameReview.grade(played = 4, best = 30))
        // Small losses that change the result still count.
        assertEquals(Grade.MISTAKE, EndgameReview.grade(played = 0, best = 2))
        assertEquals(Grade.MISTAKE, EndgameReview.grade(played = -2, best = 0))
        assertEquals(Grade.BLUNDER, EndgameReview.grade(played = -2, best = 2))
    }
}
