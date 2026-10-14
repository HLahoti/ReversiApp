package com.example.reversi.bot.learning

import com.example.reversi.bot.Bot
import com.example.reversi.bot.BotPlayer
import com.example.reversi.bot.ModelLoader
import com.example.reversi.bot.Opponent
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Board
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpeningBookTest {

    private class MemoryStore(var data: String? = null) : KnowledgeStore {
        override fun read() = data
        override fun write(data: String) { this.data = data }
    }

    /** Deterministic engine, so any change in Shadow's play comes from the book. */
    private fun engine(o: Opponent): Bot {
        val m = File("src/main/assets/${o.assetPath}").inputStream().use(ModelLoader::load)
        return Bot(m.evaluator, m.searchDepth, m.id)
    }

    private fun play(black: BotPlayer, white: BotPlayer): Pair<GameState, List<Move>> {
        var s = GameState()
        val moves = ArrayList<Move>()
        while (!s.isTerminal) {
            val m = if (s.legalMoves.isEmpty()) Move.PASS
            else (if (s.toMove == Player.BLACK) black else white).chooseMove(s.board, s.toMove)
            moves += m
            s = s.play(m)!!
        }
        return s to moves
    }

    @Test
    fun symmetryRoundTripsMovesAndMergesMirrors() {
        for (t in 0 until 8) for (y in 0 until 8) for (x in 0 until 8) {
            val m = Move(x, y)
            assertEquals(m, BoardSymmetry.fromCanonical(BoardSymmetry.toCanonical(m, t), t))
        }
        // The four opening moves are mirror images: one canonical position.
        val start = Board()
        val keys = start.legalMoves(Player.BLACK).map {
            BoardSymmetry.canonical(start.apply(it, Player.BLACK).first, Player.WHITE).key
        }.toSet()
        assertEquals(1, keys.size)
    }

    @Test
    fun neverLosesTheSameWayTwice() {
        val book = OpeningBook(AppConfig.Learning.BOOK_HORIZON_PLIES)
        val shadow = LearningBot(engine(Opponent.SHADOW), book, MemoryStore())
        val rival = engine(Opponent.KONERU) // stronger, and plays the same every time

        val losses = HashSet<List<Move>>()
        repeat(GAMES) { g ->
            val shadowColor = if (g % 2 == 0) Player.BLACK else Player.WHITE
            val (end, moves) = if (shadowColor == Player.BLACK) play(shadow, rival) else play(rival, shadow)
            if (end.winner == shadowColor.opponent()) {
                assertTrue("game $g repeats an earlier loss", losses.add(moves))
            }
            shadow.onGameFinished(GameRecord(moves, shadowColor))
        }
        assertEquals(GAMES, book.gamesLearned)
    }

    @Test
    fun exportImportKeepsTheSameChoices() {
        val base = engine(Opponent.SHADOW)
        val store = MemoryStore()
        val first = LearningBot(base, OpeningBook(AppConfig.Learning.BOOK_HORIZON_PLIES), store)
        val rival = engine(Opponent.PRAGG)
        val (_, moves) = play(rival, first)
        first.onGameFinished(GameRecord(moves, Player.WHITE))
        assertNotNull(store.data)

        val restored = OpeningBook(AppConfig.Learning.BOOK_HORIZON_PLIES)
        restored.import(store.data!!)
        assertEquals(1, restored.gamesLearned)
        var s = GameState()
        for (m in moves.take(AppConfig.Learning.BOOK_HORIZON_PLIES)) {
            if (s.toMove == Player.WHITE && s.legalMoves.isNotEmpty()) {
                assertEquals(first.chooseMove(s.board, s.toMove), restored.chooseMove(s.board, s.toMove, base))
            }
            s = s.play(m)!!
        }
    }

    @Test
    fun badDataLeavesTheBookUntouched() {
        val book = OpeningBook(AppConfig.Learning.BOOK_HORIZON_PLIES)
        val (_, moves) = play(engine(Opponent.VISHY), engine(Opponent.GUK))
        book.learn(GameRecord(moves, Player.BLACK), engine(Opponent.SHADOW))
        val before = book.export()
        runCatching { book.import("""{"version":1,"games":3,"nodes":{"a":{"c":{"1":"missing"}}}}""") }
        runCatching { book.import("not json") }
        assertEquals(before, book.export())
        assertFalse(book.size == 0)
    }

    private companion object {
        const val GAMES = 8
    }
}
