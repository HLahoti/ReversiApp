package com.example.reversi.bot

import com.example.reversi.bot.learning.GameRecord
import com.example.reversi.game.Board
import com.example.reversi.game.Move
import com.example.reversi.game.Player

/**
 * The seam between the game and any bot opponent. The view model only ever
 * talks to this: a fixed engine ([Bot]) and a self-improving one
 * ([com.example.reversi.bot.learning.LearningBot]) look the same from there.
 */
interface BotPlayer {
    /** Pick a move for [player]. Blocking, possibly slow: call off the main thread. */
    fun chooseMove(board: Board, player: Player): Move

    /**
     * A game against this bot reached its end. Fixed bots ignore it; learning
     * bots study it. Blocking, possibly slow: call off the main thread.
     */
    fun onGameFinished(game: GameRecord) {}
}

/**
 * What a [com.example.reversi.bot.learning.LearningStrategy] may ask of the
 * engine it improves: a normal move, or the best of a chosen set of moves with
 * its score. Scores are in search units: heuristic evaluations sit in roughly
 * -1..1, and decided games score beyond ±1000 (see
 * [AlphaBetaSearch.terminalScore]), so the two never get confused.
 */
interface SearchEngine {
    fun chooseMove(board: Board, player: Player): Move

    /** Best of [candidates] (legal moves for [player]), or null if empty. */
    fun bestOf(board: Board, player: Player, candidates: List<Move>): ScoredMove?
}
