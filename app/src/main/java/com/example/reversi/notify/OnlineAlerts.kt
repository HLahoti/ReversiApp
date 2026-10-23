package com.example.reversi.notify

import com.example.reversi.game.GameState
import com.example.reversi.game.Player
import com.example.reversi.net.RemoteMove
import com.example.reversi.net.SessionState
import com.example.reversi.net.SessionStatus

/** What a notification says. */
data class Notice(val title: String, val text: String)

/**
 * Decides what is worth telling a player who has switched away from an
 * online game: the opponent joined, played (so it's their move), asked for a
 * rematch, or the game ended. Pure, so it's unit tested; [AwayNotifier] does
 * the posting.
 */
object OnlineAlerts {

    /**
     * The alert the change from [prev] to [next] is worth, or null. Only the
     * opponent's actions count: the player made their own, so never hears
     * about them.
     */
    fun between(prev: SessionState, next: SessionState): Notice? {
        val me = next.myColor ?: return null
        if (next.status == SessionStatus.ABANDONED) return null
        val name = next.opponent?.displayName?.takeIf { it.isNotBlank() }
        val they = name ?: "Your opponent"
        val them = name ?: "your opponent"

        if (prev.status == SessionStatus.WAITING && next.status == SessionStatus.ACTIVE) {
            return Notice("$they joined", "The game has started. ${firstMove(me, them)}")
        }
        if (next.round != prev.round) return Notice("Rematch with $them", firstMove(me, them))
        if (next.status != SessionStatus.ACTIVE) return null

        if (prev.resignedBy == null && next.resignedBy == me.opponent()) {
            return Notice("$they resigned", "You win the game.")
        }
        if (next.moves.size > prev.moves.size) {
            val last = next.moves.last()
            if (last.by == me) return null
            val state = replay(next.moves) ?: return null
            if (state.isTerminal) return gameOver(state, me, they, them)
            // With no legal move the game passes for the player by itself.
            if (state.toMove != me || state.legalMoves.isEmpty()) return null
            return Notice(
                "Your move",
                if (last.isPass) "$they had no move, so it's your turn again."
                else "$they has played. It's your turn.",
            )
        }
        if (!prev.rematchRequestedByOpponent && next.rematchRequestedByOpponent &&
            !next.rematchRequestedByMe
        ) {
            return Notice("$they wants a rematch", "Open the game and tap Play again to start it.")
        }
        return null
    }

    /** The ongoing notification shown while a room is open. */
    fun ongoing(state: SessionState, roomCode: String): Notice {
        val name = state.opponent?.displayName?.takeIf { it.isNotBlank() }
        return when {
            state.status == SessionStatus.WAITING -> Notice(
                "Waiting in room $roomCode",
                "You'll get a notification when someone joins.",
            )
            name != null -> Notice("Online game vs $name", "You'll get a notification when it's your move.")
            else -> Notice("Online game", "You'll get a notification when it's your move.")
        }
    }

    private fun firstMove(me: Player, them: String): String =
        if (me == Player.BLACK) "You're black, so it's your move."
        else "You're white; $them moves first."

    private fun gameOver(state: GameState, me: Player, they: String, them: String): Notice {
        val mine = if (me == Player.BLACK) state.blackCount else state.whiteCount
        val theirs = if (me == Player.BLACK) state.whiteCount else state.blackCount
        return when (state.winner) {
            me -> Notice("Game over: you won", "You beat $them $mine–$theirs.")
            null -> Notice("Game over: a draw", "You drew with $them, $mine–$theirs.")
            else -> Notice("Game over: $they won", "$they won $theirs–$mine.")
        }
    }

    /** The position after [moves], or null if they don't make a legal game. */
    private fun replay(moves: List<RemoteMove>): GameState? =
        moves.fold(GameState()) { s, m ->
            if (m.by != s.toMove) return null
            s.play(m.toMove()) ?: return null
        }
}
