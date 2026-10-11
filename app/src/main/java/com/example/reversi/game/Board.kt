package com.example.reversi.game

enum class Cell { EMPTY, BLACK, WHITE }

enum class Player {
    BLACK, WHITE;

    fun opponent(): Player = if (this == BLACK) WHITE else BLACK
    fun toCell(): Cell = if (this == BLACK) Cell.BLACK else Cell.WHITE
}

data class Move(val x: Int, val y: Int, val isPass: Boolean = false) {
    companion object { val PASS = Move(-1, -1, isPass = true) }
}

/** 8x8 Reversi board with standard rules. Mirrors the C++ Board.hpp. */
class Board private constructor(private val cells: IntArray) {

    constructor() : this(IntArray(SIZE * SIZE).also {
        it[idx(3, 3)] = Cell.WHITE.ordinal
        it[idx(4, 4)] = Cell.WHITE.ordinal
        it[idx(3, 4)] = Cell.BLACK.ordinal
        it[idx(4, 3)] = Cell.BLACK.ordinal
    })

    fun at(x: Int, y: Int): Cell = Cell.entries[cells[idx(x, y)]]

    fun legalMoves(p: Player): List<Move> {
        val out = ArrayList<Move>(16)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (cells[idx(x, y)] != Cell.EMPTY.ordinal) continue
            for (d in DIRS) if (wouldFlip(x, y, d[0], d[1], p)) {
                out.add(Move(x, y)); break
            }
        }
        return out
    }

    fun isLegal(m: Move, p: Player): Boolean {
        if (m.isPass) return legalMoves(p).isEmpty()
        if (!inBounds(m.x, m.y)) return false
        if (cells[idx(m.x, m.y)] != Cell.EMPTY.ordinal) return false
        for (d in DIRS) if (wouldFlip(m.x, m.y, d[0], d[1], p)) return true
        return false
    }

    /** Returns a new board with the move applied and the list of flipped squares. */
    fun apply(m: Move, p: Player): Pair<Board, List<Pair<Int, Int>>> {
        if (m.isPass) return Board(cells.copyOf()) to emptyList()
        require(isLegal(m, p)) { "illegal move $m for $p" }
        val next = cells.copyOf()
        val own = p.toCell().ordinal
        val opp = p.opponent().toCell().ordinal
        next[idx(m.x, m.y)] = own
        val flips = ArrayList<Pair<Int, Int>>()
        for (d in DIRS) {
            if (!wouldFlip(m.x, m.y, d[0], d[1], p)) continue
            var cx = m.x + d[0]; var cy = m.y + d[1]
            while (inBounds(cx, cy) && cells[idx(cx, cy)] == opp) {
                next[idx(cx, cy)] = own
                flips.add(cx to cy)
                cx += d[0]; cy += d[1]
            }
        }
        return Board(next) to flips
    }

    fun hasAnyMove(p: Player): Boolean = legalMoves(p).isNotEmpty()
    fun isTerminal(): Boolean = !hasAnyMove(Player.BLACK) && !hasAnyMove(Player.WHITE)
    fun count(c: Cell): Int = cells.count { it == c.ordinal }

    private fun wouldFlip(x: Int, y: Int, dx: Int, dy: Int, p: Player): Boolean {
        val own = p.toCell().ordinal
        val opp = p.opponent().toCell().ordinal
        var cx = x + dx; var cy = y + dy
        var seenOpp = false
        while (inBounds(cx, cy) && cells[idx(cx, cy)] == opp) {
            seenOpp = true; cx += dx; cy += dy
        }
        return seenOpp && inBounds(cx, cy) && cells[idx(cx, cy)] == own
    }

    companion object {
        const val SIZE = 8
        private val DIRS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
            intArrayOf(-1,  0),                     intArrayOf(1,  0),
            intArrayOf(-1,  1), intArrayOf(0,  1), intArrayOf(1,  1),
        )
        fun inBounds(x: Int, y: Int) = x in 0 until SIZE && y in 0 until SIZE
        private fun idx(x: Int, y: Int) = y * SIZE + x

        /**
         * Builds an arbitrary position from [SIZE] row strings, one char per
         * square: 'B'/'b' black, 'W'/'w' white, anything else (e.g. '.') empty.
         * Used to author fixed positions such as the how-to-play tutorial;
         * it does not check that the position is reachable in real play.
         */
        fun fromRows(rows: List<String>): Board {
            require(rows.size == SIZE) { "need $SIZE rows, got ${rows.size}" }
            val cells = IntArray(SIZE * SIZE)
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                cells[idx(x, y)] = when (rows[y].getOrNull(x)) {
                    'B', 'b' -> Cell.BLACK.ordinal
                    'W', 'w' -> Cell.WHITE.ordinal
                    else -> Cell.EMPTY.ordinal
                }
            }
            return Board(cells)
        }
    }
}
