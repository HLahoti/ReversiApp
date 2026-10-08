package com.example.othello.bot

import com.example.othello.game.Board
import com.example.othello.game.Player

/**
 * The single seam between the app's game logic and whatever model scores a
 * position. Everything above it — [Bot] and the alpha-beta search — knows only
 * this contract: given a board and the side to move, return a scalar where
 * higher is better for that side.
 *
 * Crucially the input is the [Board] itself, not a pre-encoded vector, so the
 * *representation* is each architecture's own business. A dense MLP flattens
 * the board to a feature vector ([NeuralNetworkEvaluator]); a future CNN would
 * build spatial planes instead — both satisfy this same interface, and the app
 * logic never learns which one it's talking to.
 */
fun interface PositionEvaluator {
    fun evaluate(board: Board, player: Player): Float
}
