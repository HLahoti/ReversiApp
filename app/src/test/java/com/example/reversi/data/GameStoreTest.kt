package com.example.reversi.data

import com.example.reversi.bot.Opponent
import com.example.reversi.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val moves = listOf(SavedMove(2, 3), SavedMove(2, 2))

    @Test
    fun eachBotHasItsOwnSlot() {
        val store = GameStore(tmp.root)
        store.writeVsBot(moves, Player.BLACK, Opponent.GUK)
        store.writeVsBot(moves.take(1), Player.WHITE, Opponent.KONERU)

        assertEquals(setOf(Opponent.GUK, Opponent.KONERU), store.opponentsWithSaves())
        assertEquals(moves, store.load(SavedMode.VS_BOT, Opponent.GUK)?.moves)
        assertEquals("WHITE", store.load(SavedMode.VS_BOT, Opponent.KONERU)?.humanColor)
        assertNull(store.load(SavedMode.VS_BOT, Opponent.VISHY))

        store.clear(SavedMode.VS_BOT, Opponent.GUK)
        assertFalse(store.has(SavedMode.VS_BOT, Opponent.GUK))
        assertTrue(store.has(SavedMode.VS_BOT, Opponent.KONERU))
    }

    @Test
    fun vsBotSavesCarryTheirHistoryId() {
        val store = GameStore(tmp.root)
        store.writeVsBot(moves, Player.BLACK, Opponent.GUK, historyId = "g1")
        store.writeVsBot(moves, Player.BLACK, Opponent.PRAGG)

        assertEquals("g1", store.load(SavedMode.VS_BOT, Opponent.GUK)?.historyId)
        assertEquals(setOf("g1"), store.resumableHistoryIds())
    }

    @Test
    fun twoPlayerSlotIsSeparate() {
        val store = GameStore(tmp.root)
        store.writeTwoPlayer(moves)
        assertTrue(store.has(SavedMode.TWO_PLAYER))
        assertTrue(store.opponentsWithSaves().isEmpty())
    }

    @Test
    fun sharedLegacySaveMovesToItsBot() {
        File(tmp.root, "vs_bot.json").writeText(
            """{"humanColor":"BLACK","difficulty":"HARD","moves":[{"x":2,"y":3}]}"""
        )
        val store = GameStore(tmp.root)
        assertFalse(File(tmp.root, "vs_bot.json").exists())
        assertEquals(setOf(Opponent.ARJUN), store.opponentsWithSaves())
        assertEquals(listOf(SavedMove(2, 3)), store.load(SavedMode.VS_BOT, Opponent.ARJUN)?.moves)
    }

    @Test
    fun legacySaveWithoutBotGoesToDefault() {
        File(tmp.root, "vs_bot.json").writeText("""{"humanColor":"WHITE","moves":[]}""")
        assertEquals(setOf(Opponent.DEFAULT), GameStore(tmp.root).opponentsWithSaves())
    }

    @Test
    fun legacySaveNeverOverwritesExistingSlot() {
        GameStore(tmp.root).writeVsBot(moves, Player.BLACK, Opponent.ARJUN)
        File(tmp.root, "vs_bot.json").writeText(
            """{"humanColor":"WHITE","difficulty":"ARJUN","moves":[]}"""
        )
        val store = GameStore(tmp.root)
        assertFalse(File(tmp.root, "vs_bot.json").exists())
        assertEquals(moves, store.load(SavedMode.VS_BOT, Opponent.ARJUN)?.moves)
    }
}
