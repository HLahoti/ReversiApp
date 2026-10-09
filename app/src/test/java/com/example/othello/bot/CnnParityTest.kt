package com.example.othello.bot

import com.example.othello.game.Board
import com.example.othello.game.Move
import com.example.othello.game.Player
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Pins the Kotlin CNN port to OthelloEngine. The expected values were produced
 * by the C++ engine (`Engine::evaluate` / `Engine::chooseMove`) on the same
 * bundled assets, so a drift in the encoder, genome slicing, or conv maths
 * fails here instead of silently weakening Pragg and Koneru.
 */
class CnnParityTest {

    private class Case(
        val rows: List<String>,
        val toMove: Player,
        val evals: FloatArray, // [pragg, koneru]
        val bestMoves: List<Move>,
    )

    private val cases = listOf(
        Case(listOf("........", "........", "........", "...WB...", "...BW...", "........", "........", "........"), Player.BLACK, floatArrayOf(1.9037762e-01f, 1.5521166e-01f), listOf(Move(2, 3), Move(5, 4))),
        Case(listOf("........", "........", "........", ".W.WB...", ".BBBW...", "...WWW..", "........", "........"), Player.BLACK, floatArrayOf(3.0834568e-01f, 2.3416643e-01f), listOf(Move(0, 2), Move(5, 4))),
        Case(listOf("........", "........", "B.......", ".B.WWWW.", ".BBWBW..", "..WWWW..", "...B....", "........"), Player.BLACK, floatArrayOf(2.6542428e-01f, 2.6135820e-01f), listOf(Move(2, 6), Move(2, 6))),
        Case(listOf("........", "........", "B...B...", ".BWWWWW.", ".BWWBW..", "..WBBW..", ".WWWB...", "..W....."), Player.BLACK, floatArrayOf(3.0525690e-01f, 3.9721107e-01f), listOf(Move(1, 2), Move(1, 5))),
        Case(listOf("........", "......W.", "BB..BWW.", ".BBWWWW.", ".BWBBW..", "..WBBBB.", ".WWWWW..", "..W....."), Player.BLACK, floatArrayOf(3.0562338e-01f, 4.5210814e-01f), listOf(Move(2, 2), Move(2, 2))),
        Case(listOf("........", "......W.", "BB..BWWW", ".BBBBBWB", "WWWBBW.B", "..WWWWBB", ".WWWWW.B", "..W....."), Player.BLACK, floatArrayOf(2.3896237e-01f, 3.8286653e-01f), listOf(Move(7, 1), Move(7, 1))),
        Case(listOf("......WB", ".....WWB", "BB.WWBWB", ".BWWBBWB", "WWWBBW.B", "..WWWBBB", ".WWWWWBB", "..W....."), Player.BLACK, floatArrayOf(5.5779260e-01f, 6.3631052e-01f), listOf(Move(6, 4), Move(6, 4))),
        Case(listOf("....BWWB", ".....WWB", "BB.WWWBB", ".BWWBWWB", "WWWBBWBB", "..WBWBBB", ".WBWWWBB", ".BWWW..."), Player.BLACK, floatArrayOf(3.9423561e-01f, 5.4204547e-01f), listOf(Move(0, 7), Move(0, 7))),
    )

    private fun load(o: Opponent) =
        File("src/main/assets/${o.assetPath}").inputStream().use(ModelLoader::load)

    @Test
    fun everyOpponentLoads() {
        for (o in Opponent.entries) load(o)
    }

    @Test
    fun cnnMatchesEngine() {
        val models = listOf(load(Opponent.PRAGG), load(Opponent.KONERU))
        for (c in cases) {
            val board = Board.fromRows(c.rows)
            models.forEachIndexed { i, m ->
                assertEquals(c.evals[i], m.evaluator.evaluate(board, c.toMove), 1e-4f)
                val move = Bot(m.evaluator, m.searchDepth).chooseMove(board, c.toMove)
                assertEquals(c.bestMoves[i], move)
            }
        }
    }
}
