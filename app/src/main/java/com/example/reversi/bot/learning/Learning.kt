package com.example.reversi.bot.learning

import com.example.reversi.config.AppConfig

/**
 * The self-improvement strategies an [com.example.reversi.bot.Opponent] can
 * be built with. To swap one in, add an entry here and point the opponent at
 * it. Each strategy saves to its own file (see [com.example.reversi.bot.Bots]),
 * so switching never feeds one strategy another's saved data.
 */
enum class Learning {
    /** Logistello-style book learning: never lose the same way twice. */
    OPENING_BOOK {
        override fun create(): LearningStrategy = OpeningBook(AppConfig.Learning.BOOK_HORIZON_PLIES)
    },
    ;

    abstract fun create(): LearningStrategy
}
