package com.example.reversi.game

/** Immutable snapshot of a game at one moment. */
data class GameState(
    val board: Board = Board(),
    val toMove: Player = Player.BLACK,
    val lastMove: Move? = null,
    val lastFlips: List<Pair<Int, Int>> = emptyList(),
    val passedLast: Boolean = false,
) {
    val legalMoves: List<Move> by lazy { board.legalMoves(toMove) }
    val isTerminal: Boolean get() = board.isTerminal()
    val blackCount: Int get() = board.count(Cell.BLACK)
    val whiteCount: Int get() = board.count(Cell.WHITE)

    /** Returns the next state after `move`, or null if the move is illegal. */
    fun play(move: Move): GameState? {
        if (move.isPass) {
            if (legalMoves.isNotEmpty()) return null
            return copy(
                board = board,
                toMove = toMove.opponent(),
                lastMove = Move.PASS,
                lastFlips = emptyList(),
                passedLast = true,
            )
        }
        if (!board.isLegal(move, toMove)) return null
        val (next, flips) = board.apply(move, toMove)
        return GameState(
            board = next,
            toMove = toMove.opponent(),
            lastMove = move,
            lastFlips = flips,
            passedLast = false,
        )
    }

    /** Winner, or null on draw. Only meaningful once terminal. */
    val winner: Player? get() {
        if (!isTerminal) return null
        val b = blackCount; val w = whiteCount
        return when {
            b > w -> Player.BLACK
            w > b -> Player.WHITE
            else -> null
        }
    }
}
