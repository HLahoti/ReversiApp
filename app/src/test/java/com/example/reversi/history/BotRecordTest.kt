package com.example.reversi.history

import com.example.reversi.game.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class BotRecordTest {

    private var next = 0

    private fun entry(
        botId: String?,
        outcome: HistoryOutcome?,
        status: HistoryStatus = HistoryStatus.COMPLETED,
        mode: HistoryMode = HistoryMode.BOT,
    ) = HistoryEntry(
        id = "g${next++}", mode = mode, myColor = Player.BLACK,
        opponentName = botId, botId = botId, status = status, outcome = outcome,
        blackCount = 40, whiteCount = 24, moveCount = 60, startedAt = 0, updatedAt = 0,
    )

    @Test
    fun countsOnlyFinishedGamesAgainstThatBot() {
        val all = listOf(
            entry("ARJUN", HistoryOutcome.WIN),
            entry("ARJUN", HistoryOutcome.WIN),
            entry("ARJUN", HistoryOutcome.LOSS),
            entry("ARJUN", null, HistoryStatus.ABANDONED),
            entry("ARJUN", null, HistoryStatus.IN_PROGRESS),
            entry("GUK", HistoryOutcome.LOSS),
            entry(null, HistoryOutcome.DRAW, mode = HistoryMode.ONLINE),
        )
        assertEquals(HistoryTally(wins = 2, losses = 1, draws = 0), HistoryTally.vsBot(all, "ARJUN"))
        assertEquals(HistoryTally(wins = 0, losses = 1, draws = 0), HistoryTally.vsBot(all, "GUK"))
    }

    @Test
    fun lineNamesTheBotAndPluralises() {
        assertEquals(
            "You haven't finished a game against Guk yet",
            HistoryTally(0, 0, 0).recordLine("Guk"),
        )
        assertEquals("Your record vs Guk: 1 win · 2 losses", HistoryTally(1, 2, 0).recordLine("Guk"))
        assertEquals("Your record vs Guk: 3 wins · 1 loss · 1 draw", HistoryTally(3, 1, 1).recordLine("Guk"))
    }
}
