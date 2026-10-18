package com.example.reversi.data

/**
 * What changed in each version, shown under "What's new" in Settings → About
 * for the installed version. Bundled rather than fetched, so it works offline
 * and doesn't depend on where the app was installed from.
 *
 * When bumping versionName, add (or finish) its entry here; keep it in step
 * with the GitHub release notes.
 */
object Changelog {
    data class Entry(val version: String, val title: String, val changes: List<String>)

    val entries: List<Entry> = listOf(
        Entry(
            version = "1.6",
            title = "Resign, hints and update alerts",
            changes = listOf(
                "Resign a game against a bot or an online opponent from the new flag under the board.",
                "Stuck against a bot? The lightbulb asks the bot you're playing for a move, which pulses on the board.",
                "Play again after a game against a bot and you swap colours, so you take turns going first.",
                "Pause and reset now sit under the board, and reset asks before throwing away your game.",
                "The home screen tells you when a new version is out. Settings shows the app version and what's new.",
            ),
        ),
        Entry(
            version = "1.5",
            title = "Bot picker fits every screen",
            changes = listOf(
                "On shorter screens the home screen now scrolls, so the Start, Resume and New game buttons in Play against the bot are always reachable.",
                "Opening the bot options, or picking a bot with a paused game, scrolls the buttons into view.",
                "A fade with a down arrow shows when there's more below. Tap it to jump down.",
            ),
        ),
        Entry(
            version = "1.4",
            title = "Smarter bots and Shadow, a bot that learns from you",
            changes = listOf(
                "Shadow, a new opponent that studies every game you finish against it and never loses to you the same way twice.",
                "Bots vary their play, so the same moves from you no longer get the same game every time.",
                "The game title names the bot you're playing, and each bot has its own saved game.",
            ),
        ),
    )

    /** The entry for [version], or null if it has none. */
    fun forVersion(version: String): Entry? = entries.firstOrNull { it.version == version }
}
