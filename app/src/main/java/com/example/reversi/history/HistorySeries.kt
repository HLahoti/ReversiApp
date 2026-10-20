package com.example.reversi.history

/** One row of the history list: a lone game, or the rounds of a series. */
sealed interface HistoryItem {
    data class Single(val entry: HistoryEntry) : HistoryItem

    /**
     * Rounds played back to back with "Play again". [shown] are the rounds
     * that pass the filter, newest first; [tally] and [rounds] count the
     * whole series, so the summary doesn't change with the filter.
     */
    data class Series(
        val id: String,
        val shown: List<HistoryEntry>,
        val rounds: Int,
        val tally: HistoryTally,
    ) : HistoryItem {
        val latest: HistoryEntry get() = shown.first()
    }

    companion object {
        /**
         * [shown] (newest first) with each series of two or more rounds in
         * [all] gathered under one item, placed where its newest shown round
         * would be.
         */
        fun group(shown: List<HistoryEntry>, all: List<HistoryEntry>): List<HistoryItem> {
            val series = all.filter { it.seriesId != null }.groupBy { it.seriesId!! }
                .filterValues { it.size > 1 }
            val placed = mutableSetOf<String>()
            return buildList {
                for (e in shown) {
                    val id = e.seriesId
                    val rounds = id?.let(series::get)
                    if (rounds == null) {
                        add(Single(e))
                    } else if (placed.add(id)) {
                        add(Series(id, shown.filter { it.seriesId == id }, rounds.size, HistoryTally.of(rounds)))
                    }
                }
            }
        }
    }
}
