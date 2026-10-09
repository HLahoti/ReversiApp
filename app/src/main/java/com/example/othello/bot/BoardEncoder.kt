package com.example.othello.bot

import com.example.othello.game.Board
import com.example.othello.game.Cell
import com.example.othello.game.Player

/**
 * Turns a board position into the float vector fed to a [NeuralNetwork]. This
 * is the single source of truth for the board → input encoding: if the feature
 * set changes, only an encoder changes, and [size] keeps the network's declared
 * input width honest (see the check in [Bot]). The evaluator is always written
 * from `perspective`'s point of view ("1 is my colour").
 */
interface BoardEncoder {
    /** Length of the vector [encode] returns; must equal the net's input size. */
    val size: Int

    fun encode(board: Board, perspective: Player): FloatArray
}

/**
 * The current encoding, mirroring OthelloEngine's `Board::networkInput`:
 *   [0..63]    own-disc plane (1.0 where `perspective` has a disc)
 *   [64..127]  opponent-disc plane
 *   [128..191] perspective's legal-move plane
 *   [192..204] 13 hand-crafted positional features (see [positionalFeatures])
 *
 * Keep this byte-for-byte in step with the engine: the evolved genomes were
 * trained against exactly these inputs, so any divergence silently weakens the
 * bots rather than failing loudly.
 */
object PlanesAndFeaturesEncoder : BoardEncoder {

    private const val PLANE = 64
    private const val NUM_PLANES = 3
    internal const val NUM_FEATURES = 13

    override val size: Int = NUM_PLANES * PLANE + NUM_FEATURES // 205

    // Corners, their diagonal "X-squares", and their orthogonal "C-squares".
    private val CORNERS = arrayOf(0 to 0, 7 to 0, 0 to 7, 7 to 7)
    private val X_SQUARES = arrayOf(1 to 1, 6 to 1, 1 to 6, 6 to 6)
    private val C_SQUARES = arrayOf(
        1 to 0, 0 to 1, 6 to 0, 7 to 1,
        1 to 7, 0 to 6, 6 to 7, 7 to 6,
    )
    private val DIRS = arrayOf(
        -1 to -1, 0 to -1, 1 to -1, -1 to 0,
        1 to 0, -1 to 1, 0 to 1, 1 to 1,
    )

    private fun idx(x: Int, y: Int) = y * 8 + x

    override fun encode(board: Board, perspective: Player): FloatArray {
        val out = FloatArray(size)
        val own = perspective.toCell()
        val opp = perspective.opponent().toCell()

        // Planes 0 and 1: own / opponent discs.
        for (y in 0 until 8) for (x in 0 until 8) {
            when (board.at(x, y)) {
                own -> out[idx(x, y)] = 1f
                opp -> out[PLANE + idx(x, y)] = 1f
                else -> {}
            }
        }
        // Plane 2: perspective's legal-move squares.
        for (m in board.legalMoves(perspective)) {
            if (!m.isPass) out[2 * PLANE + idx(m.x, m.y)] = 1f
        }
        // Hand-crafted features.
        val feats = positionalFeatures(board, perspective)
        for (i in 0 until NUM_FEATURES) out[NUM_PLANES * PLANE + i] = feats[i]
        return out
    }

    /**
     * The 13 strategic quantities corner-weighted play depends on, each roughly
     * normalised to [0, 1]. Order matches the engine exactly. Shared with
     * [CnnPlanesEncoder], whose head consumes the same features.
     */
    internal fun positionalFeatures(board: Board, perspective: Player): FloatArray {
        val own = perspective.toCell()
        val opp = perspective.opponent().toCell()

        fun countAt(squares: Array<Pair<Int, Int>>, c: Cell): Int =
            squares.count { (x, y) -> board.at(x, y) == c }

        val ownCorners = countAt(CORNERS, own)
        val oppCorners = countAt(CORNERS, opp)
        val ownX = countAt(X_SQUARES, own)
        val oppX = countAt(X_SQUARES, opp)
        val ownC = countAt(C_SQUARES, own)
        val oppC = countAt(C_SQUARES, opp)
        val ownMob = board.legalMoves(perspective).size
        val oppMob = board.legalMoves(perspective.opponent()).size
        val ownFront = countFrontier(board, own)
        val oppFront = countFrontier(board, opp)
        val ownStable = countStableAlongEdges(board, own)
        val oppStable = countStableAlongEdges(board, opp)
        val filled = board.count(Cell.BLACK) + board.count(Cell.WHITE)

        return floatArrayOf(
            ownCorners / 4f,
            oppCorners / 4f,
            ownX / 4f,
            oppX / 4f,
            ownC / 8f,
            oppC / 8f,
            ownMob / 20f,
            oppMob / 20f,
            ownFront / 30f,
            oppFront / 30f,
            ownStable / 20f,
            oppStable / 20f,
            filled / 64f,
        )
    }

    /** Discs of [c] that touch at least one empty square (weak, exposed discs). */
    private fun countFrontier(board: Board, c: Cell): Int {
        if (c == Cell.EMPTY) return 0
        var n = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (board.at(x, y) != c) continue
            for ((dx, dy) in DIRS) {
                val nx = x + dx
                val ny = y + dy
                if (Board.inBounds(nx, ny) && board.at(nx, ny) == Cell.EMPTY) {
                    n++; break
                }
            }
        }
        return n
    }

    /**
     * Approximate stable-disc count: from each owned corner, walk the two edges
     * marking same-colour discs until a different cell is hit. Captures the
     * dominant "unflippable" dynamic that drives corner play (matches engine).
     */
    private fun countStableAlongEdges(board: Board, c: Cell): Int {
        if (c == Cell.EMPTY) return 0
        val marked = BooleanArray(64)

        fun walk(startX: Int, startY: Int, dx: Int, dy: Int) {
            var cx = startX
            var cy = startY
            while (Board.inBounds(cx, cy) && board.at(cx, cy) == c) {
                marked[idx(cx, cy)] = true
                cx += dx; cy += dy
            }
        }

        for ((cx, cy) in CORNERS) {
            if (board.at(cx, cy) != c) continue
            val dxAlongTopBottom = if (cx == 0) 1 else -1
            val dyAlongLeftRight = if (cy == 0) 1 else -1
            walk(cx, cy, dxAlongTopBottom, 0)
            walk(cx, cy, 0, dyAlongLeftRight)
        }
        return marked.count { it }
    }
}

/**
 * The CNN encoding, mirroring OthelloEngine's `Board::networkInputCnn`: four
 * 8×8 one-hot planes followed by the same 13 features as the MLP.
 *   [0..63]    own-disc plane
 *   [64..127]  opponent-disc plane
 *   [128..191] perspective's legal-move plane
 *   [192..255] empty-cell plane
 *   [256..268] 13 hand-crafted positional features
 * Planes are row-major (`y * 8 + x`), which is the [channel, y, x] layout the
 * convolutions in [CnnNetwork] expect.
 */
object CnnPlanesEncoder : BoardEncoder {

    private const val PLANE = 64
    const val NUM_PLANES = 4
    const val NUM_FEATURES = PlanesAndFeaturesEncoder.NUM_FEATURES

    override val size: Int = NUM_PLANES * PLANE + NUM_FEATURES // 269

    override fun encode(board: Board, perspective: Player): FloatArray {
        val out = FloatArray(size)
        val own = perspective.toCell()
        val opp = perspective.opponent().toCell()

        // Planes 0, 1, 3: own / opponent / empty.
        for (y in 0 until 8) for (x in 0 until 8) {
            val i = y * 8 + x
            when (board.at(x, y)) {
                own -> out[i] = 1f
                opp -> out[PLANE + i] = 1f
                else -> out[3 * PLANE + i] = 1f
            }
        }
        // Plane 2: perspective's legal-move squares.
        for (m in board.legalMoves(perspective)) {
            if (!m.isPass) out[2 * PLANE + m.y * 8 + m.x] = 1f
        }
        val feats = PlanesAndFeaturesEncoder.positionalFeatures(board, perspective)
        for (i in 0 until NUM_FEATURES) out[NUM_PLANES * PLANE + i] = feats[i]
        return out
    }
}
