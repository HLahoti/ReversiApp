package com.example.reversi.history

import com.example.reversi.game.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySeriesTest {

    private fun entry(id: String, series: String?, outcome: HistoryOutcome? = HistoryOutcome.WIN) = HistoryEntry(
        id = id, mode = HistoryMode.BOT, myColor = Player.BLACK, opponentName = "Guk",
        status = HistoryStatus.COMPLETED, outcome = outcome, blackCount = 40, whiteCount = 24,
        moveCount = 60, startedAt = 0, updatedAt = 0, endedAt = 0, seriesId = series,
    )

    @Test
    fun roundsOfASeriesAreGatheredWhereTheNewestOneIs() {
        val all = listOf(
            entry("solo", "s1"),
            entry("r3", "s2", HistoryOutcome.LOSS),
            entry("old", null),
            entry("r2", "s2"),
            entry("r1", "s2", HistoryOutcome.DRAW),
        )
        val items = HistoryItem.group(all, all)
        assertEquals(3, items.size)
        assertEquals("solo", (items[0] as HistoryItem.Single).entry.id)
        val s = items[1] as HistoryItem.Series
        assertEquals(listOf("r3", "r2", "r1"), s.shown.map { it.id })
        assertEquals(HistoryTally(wins = 1, losses = 1, draws = 1), s.tally)
        assertEquals("old", (items[2] as HistoryItem.Single).entry.id)
    }

    @Test
    fun theSummaryCountsRoundsTheFilterHides() {
        val all = listOf(entry("r3", "s"), entry("r2", "s", HistoryOutcome.LOSS), entry("r1", "s"))
        val s = HistoryItem.group(all.filter { it.outcome == HistoryOutcome.WIN }, all).single() as HistoryItem.Series
        assertEquals(listOf("r3", "r1"), s.shown.map { it.id })
        assertEquals(3, s.rounds)
        assertEquals(HistoryTally(wins = 2, losses = 1, draws = 0), s.tally)
    }
}
