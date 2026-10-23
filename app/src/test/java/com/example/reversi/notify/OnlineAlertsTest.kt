package com.example.reversi.notify

import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import com.example.reversi.net.PlayerId
import com.example.reversi.net.PlayerIdentity
import com.example.reversi.net.RemoteMove
import com.example.reversi.net.SessionState
import com.example.reversi.net.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAlertsTest {

    private val ravi = PlayerIdentity(PlayerId("guest"), "Ravi")

    /** An ACTIVE room where we play black against Ravi. */
    private fun active(moves: List<RemoteMove> = emptyList(), me: Player = Player.BLACK) =
        SessionState(status = SessionStatus.ACTIVE, myColor = me, opponent = ravi, moves = moves)

    /** The first [n] moves of a game where each side plays its first legal move (or passes). */
    private fun firstMoves(n: Int): List<RemoteMove> {
        var s = GameState()
        return (1..n).map { seq ->
            val move = s.legalMoves.firstOrNull() ?: Move.PASS
            RemoteMove.from(seq, s.toMove, move).also { s = s.play(move)!! }
        }
    }

    /** A whole game played that way, up to the end. */
    private fun wholeGame(): List<RemoteMove> {
        var s = GameState()
        val moves = mutableListOf<RemoteMove>()
        while (!s.isTerminal) {
            val move = s.legalMoves.firstOrNull() ?: Move.PASS
            moves += RemoteMove.from(moves.size + 1, s.toMove, move)
            s = s.play(move)!!
        }
        return moves
    }

    @Test
    fun tellsTheHostWhenSomeoneJoins() {
        val waiting = SessionState(status = SessionStatus.WAITING, myColor = Player.BLACK)
        assertEquals(
            Notice("Ravi joined", "The game has started. You're black, so it's your move."),
            OnlineAlerts.between(waiting, active()),
        )
        assertEquals(
            Notice("Ravi joined", "The game has started. You're white; Ravi moves first."),
            OnlineAlerts.between(waiting.copy(myColor = Player.WHITE), active(me = Player.WHITE)),
        )
    }

    @Test
    fun tellsThePlayerWhenTheOpponentHasMoved() {
        val before = active(firstMoves(1))
        val after = active(firstMoves(2))
        assertEquals(
            Notice("Your move", "Ravi has played. It's your turn."),
            OnlineAlerts.between(before, after),
        )
    }

    @Test
    fun staysQuietAboutThePlayersOwnMove() {
        assertNull(OnlineAlerts.between(active(), active(firstMoves(1))))
    }

    @Test
    fun announcesTheEndOfTheGame() {
        val game = wholeGame()
        val me = game.last().by.opponent()
        val notice = OnlineAlerts.between(active(game.dropLast(1), me), active(game, me))!!
        assertTrue(notice.title, notice.title.startsWith("Game over"))
    }

    @Test
    fun tellsThePlayerWhenTheOpponentResigns() {
        assertEquals(
            Notice("Ravi resigned", "You win the game."),
            OnlineAlerts.between(active(), active().copy(resignedBy = Player.WHITE)),
        )
        assertNull(OnlineAlerts.between(active(), active().copy(resignedBy = Player.BLACK)))
    }

    @Test
    fun tellsThePlayerAboutARematchRequestUnlessTheyAskedFirst() {
        val asked = active().copy(rematchRequestedByOpponent = true)
        assertEquals(
            Notice("Ravi wants a rematch", "Open the game and tap Play again to start it."),
            OnlineAlerts.between(active(), asked),
        )
        assertNull(
            OnlineAlerts.between(
                active().copy(rematchRequestedByMe = true),
                asked.copy(rematchRequestedByMe = true),
            ),
        )
    }

    @Test
    fun announcesTheRematchStarting() {
        val next = active(me = Player.WHITE).copy(round = 1)
        assertEquals(
            Notice("Rematch with Ravi", "You're white; Ravi moves first."),
            OnlineAlerts.between(active(firstMoves(4)), next),
        )
    }

    @Test
    fun saysNothingOnceTheRoomIsGone() {
        assertNull(
            OnlineAlerts.between(active(firstMoves(1)), active(firstMoves(2)).copy(status = SessionStatus.ABANDONED)),
        )
    }

    @Test
    fun fallsBackToYourOpponentWithoutAName() {
        val waiting = SessionState(status = SessionStatus.WAITING, myColor = Player.WHITE)
        assertEquals(
            Notice("Your opponent joined", "The game has started. You're white; your opponent moves first."),
            OnlineAlerts.between(waiting, active(me = Player.WHITE).copy(opponent = null)),
        )
    }
}
