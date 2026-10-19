package com.example.reversi.history

import com.example.reversi.game.Player

/** What the history screen shows. Each null field means "any". */
data class HistoryFilter(
    val status: HistoryStatus? = null,
    val mode: HistoryMode? = null,
    /** Only meaningful for completed games; picking one hides the rest. */
    val outcome: HistoryOutcome? = null,
    val myColor: Player? = null,
    val opponent: String? = null,
    val help: HelpFilter? = null,
) {
    fun matches(e: HistoryEntry): Boolean =
        (status == null || e.status == status) &&
            (mode == null || e.mode == mode) &&
            (outcome == null || e.outcome == outcome) &&
            (myColor == null || e.myColor == myColor) &&
            (opponent == null || e.opponentName == opponent) &&
            (help == null || help.matches(e))

    val isDefault: Boolean get() = this == HistoryFilter()
}

/** Whether the player had help from hints or takebacks. */
enum class HelpFilter {
    HINTS, TAKEBACKS, NONE;

    fun matches(e: HistoryEntry): Boolean = when (this) {
        HINTS -> e.hintsUsed > 0
        TAKEBACKS -> e.takebacks > 0
        NONE -> e.hintsUsed == 0 && e.takebacks == 0
    }
}

/** Wins, losses and draws among [entries]' completed games. */
data class HistoryTally(val wins: Int, val losses: Int, val draws: Int) {
    companion object {
        fun of(entries: List<HistoryEntry>) = HistoryTally(
            wins = entries.count { it.outcome == HistoryOutcome.WIN },
            losses = entries.count { it.outcome == HistoryOutcome.LOSS },
            draws = entries.count { it.outcome == HistoryOutcome.DRAW },
        )
    }
}
