package com.example.reversi.history

import com.example.reversi.game.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalGameHistoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "history.json")

    /** Writes run inline, so the file is up to date as soon as a call returns. */
    private fun history() = LocalGameHistory(file(), CoroutineScope(Dispatchers.Unconfined))

    private fun entry(
        id: String,
        at: Long,
        status: HistoryStatus = HistoryStatus.IN_PROGRESS,
        outcome: HistoryOutcome? = null,
        mode: HistoryMode = HistoryMode.BOT,
        color: Player = Player.BLACK,
        opponent: String = "Guk",
    ) = HistoryEntry(
        id = id, mode = mode, myColor = color, opponentName = opponent, status = status,
        outcome = outcome, blackCount = 2, whiteCount = 2, moveCount = 1,
        startedAt = at, updatedAt = at, endedAt = if (status == HistoryStatus.IN_PROGRESS) null else at,
    )

    @Test
    fun recordUpdatesInPlaceAndKeepsTheStartTime() {
        val h = history()
        h.record(entry("a", at = 100))
        h.record(entry("a", at = 200, status = HistoryStatus.COMPLETED, outcome = HistoryOutcome.WIN))

        val only = h.entries.value.single()
        assertEquals(100L, only.startedAt)
        assertEquals(200L, only.endedAt)
        assertEquals(HistoryOutcome.WIN, only.outcome)
    }

    @Test
    fun newestActivityComesFirst() {
        val h = history()
        h.record(entry("old", at = 100))
        h.record(entry("new", at = 300))
        h.record(entry("old", at = 500))
        assertEquals(listOf("old", "new"), h.entries.value.map { it.id })
    }

    @Test
    fun abandonOnlyTouchesGamesInProgress() {
        val h = history()
        h.record(entry("done", at = 100, status = HistoryStatus.COMPLETED, outcome = HistoryOutcome.LOSS))
        h.record(entry("live", at = 100))
        h.abandon("done")
        h.abandon("live")

        val byId = h.entries.value.associateBy { it.id }
        assertEquals(HistoryStatus.COMPLETED, byId.getValue("done").status)
        assertEquals(HistoryStatus.ABANDONED, byId.getValue("live").status)
    }

    @Test
    fun startupAbandonsWhatCannotBeResumedAsOfItsLastMove() {
        val h = history()
        h.record(entry("saved", at = 100))
        h.record(entry("lost", at = 200, mode = HistoryMode.ONLINE))
        h.abandonAllExcept(setOf("saved"))

        val byId = h.entries.value.associateBy { it.id }
        assertEquals(HistoryStatus.IN_PROGRESS, byId.getValue("saved").status)
        assertEquals(HistoryStatus.ABANDONED, byId.getValue("lost").status)
        assertEquals(200L, byId.getValue("lost").endedAt)
    }

    @Test
    fun survivesARestartAndShrugsOffACorruptFile() {
        history().record(entry("a", at = 100, color = Player.WHITE))
        assertEquals(Player.WHITE, history().entries.value.single().myColor)

        file().writeText("{ not json")
        assertTrue(history().entries.value.isEmpty())
    }

    @Test
    fun filtersCombine() {
        val entries = listOf(
            entry("w", at = 1, status = HistoryStatus.COMPLETED, outcome = HistoryOutcome.WIN),
            entry("l", at = 2, status = HistoryStatus.COMPLETED, outcome = HistoryOutcome.LOSS, color = Player.WHITE),
            entry("o", at = 3, mode = HistoryMode.ONLINE, opponent = "Alex"),
        )
        fun ids(f: HistoryFilter) = entries.filter(f::matches).map { it.id }

        assertEquals(listOf("w", "l", "o"), ids(HistoryFilter()))
        assertEquals(listOf("w", "l"), ids(HistoryFilter(status = HistoryStatus.COMPLETED)))
        assertEquals(listOf("o"), ids(HistoryFilter(mode = HistoryMode.ONLINE)))
        assertEquals(listOf("l"), ids(HistoryFilter(myColor = Player.WHITE)))
        assertEquals(listOf("w"), ids(HistoryFilter(mode = HistoryMode.BOT, outcome = HistoryOutcome.WIN)))
        assertEquals(listOf("o"), ids(HistoryFilter(opponent = "Alex")))
        assertEquals(HistoryTally(1, 1, 0), HistoryTally.of(entries))
        assertNull(entries.last().outcome)
    }

    @Test
    fun helpFilterSplitsHintsTakebacksAndNeither() {
        val plain = entry("plain", at = 1)
        val hinted = entry("hinted", at = 2).copy(hintsUsed = 2)
        val both = entry("both", at = 3).copy(hintsUsed = 1, takebacks = 1)
        val entries = listOf(plain, hinted, both)
        fun ids(h: HelpFilter) = entries.filter(HistoryFilter(help = h)::matches).map { it.id }

        assertEquals(listOf("hinted", "both"), ids(HelpFilter.HINTS))
        assertEquals(listOf("both"), ids(HelpFilter.TAKEBACKS))
        assertEquals(listOf("plain"), ids(HelpFilter.NONE))
    }
}
