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
            version = "1.8",
            title = "Online game notifications",
            changes = listOf(
                "Switch apps during an online game and get a notification when your opponent joins, plays, asks for a rematch, or the game ends. Tap it to go straight back to the game.",
                "Turn the notifications on or off under Online play in Settings.",
                "History groups the rounds you play with Play again into one series, with the score in rounds won.",
                "Names in History stay whole on narrow screens.",
                "Update now opens the download page.",
            ),
        ),
        Entry(
            version = "1.7",
            title = "Game history, replays and online names",
            changes = listOf(
                "History, on the home screen, lists your games against the bots and online: who played which colour, the result and when. Filter by result, opponent, colour and more.",
                "Tap a finished game in History to replay it move by move, or let it play itself.",
                "History marks the games where you used hints or took moves back.",
                "Pick a name before playing online. Your opponent sees it, and you're warned if you both have the same one.",
                "Game titles and results name your opponent and show your colour, online and against the bots.",
                "New settings to turn off hints, takebacks and the game start animation.",
                "History, How to play and Settings now sit in one bar of icons on the home screen.",
            ),
        ),
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
