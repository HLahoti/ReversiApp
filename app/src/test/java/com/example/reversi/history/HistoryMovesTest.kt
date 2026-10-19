package com.example.reversi.history

import com.example.reversi.game.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMovesTest {

    @Test
    fun roundTripsSquaresAndPasses() {
        val moves = listOf(Move(3, 2), Move(2, 4), Move.PASS, Move(0, 0), Move(7, 7))
        val text = HistoryMoves.encode(moves)
        assertEquals("d3c5--a1h8", text)
        assertEquals(moves, HistoryMoves.decode(text))
    }

    @Test
    fun emptyGameIsEmpty() {
        assertEquals("", HistoryMoves.encode(emptyList()))
        assertEquals(emptyList<Move>(), HistoryMoves.decode(""))
    }

    @Test
    fun rejectsMalformedRecords() {
        assertNull(HistoryMoves.decode("d3c"))
        assertNull(HistoryMoves.decode("i1"))
        assertNull(HistoryMoves.decode("a9"))
        assertNull(HistoryMoves.decode("D3"))
    }
}
