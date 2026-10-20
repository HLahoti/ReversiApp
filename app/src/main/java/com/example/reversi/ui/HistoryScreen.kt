package com.example.reversi.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reversi.R
import com.example.reversi.game.Player
import com.example.reversi.history.HelpFilter
import com.example.reversi.history.HistoryEntry
import com.example.reversi.history.HistoryFilter
import com.example.reversi.history.HistoryItem
import com.example.reversi.history.HistoryMode
import com.example.reversi.history.HistoryOutcome
import com.example.reversi.history.HistoryRegistry
import com.example.reversi.history.HistoryStatus
import com.example.reversi.history.HistoryTally
import com.example.reversi.profile.ProfileRegistry
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Past vs-bot and online games, newest first, with filters. Completed games
 * whose moves were kept open in a replay through [onReplay].
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, onReplay: (entryId: String) -> Unit) {
    val haptics = rememberHaptics()
    val all by HistoryRegistry.current.entries.collectAsState()
    val selfLabel = ProfileRegistry.current.selfLabel
    var filter by remember { mutableStateOf(HistoryFilter()) }
    var moreFilters by rememberSaveable { mutableStateOf(false) }
    val shown = remember(all, filter) { all.filter(filter::matches) }
    val items = remember(shown, all) { HistoryItem.group(shown, all) }

    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                IconButton(onClick = { haptics.tap(); onBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Felt.OnDark,
                    )
                }
                Text("History", color = Felt.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(8.dp))
            FilterRow(
                options = listOf(
                    null to "All",
                    HistoryStatus.COMPLETED to "Completed",
                    HistoryStatus.IN_PROGRESS to "In progress",
                    HistoryStatus.ABANDONED to "Abandoned",
                ),
                selected = filter.status,
                onSelect = { haptics.tap(); filter = filter.copy(status = it) },
            )
            FilterRow(
                options = listOf(null to "All games", HistoryMode.BOT to "Bots", HistoryMode.ONLINE to "Online"),
                selected = filter.mode,
                // The opponent list depends on the mode, so a stale pick is dropped.
                onSelect = { haptics.tap(); filter = filter.copy(mode = it, opponent = null) },
            )
            if (moreFilters) {
                FilterRow(
                    options = listOf(
                        null to "Any result",
                        HistoryOutcome.WIN to "Won",
                        HistoryOutcome.LOSS to "Lost",
                        HistoryOutcome.DRAW to "Drawn",
                    ),
                    selected = filter.outcome,
                    onSelect = { haptics.tap(); filter = filter.copy(outcome = it) },
                )
                FilterRow(
                    options = listOf(null to "Either colour", Player.BLACK to "As black", Player.WHITE to "As white"),
                    selected = filter.myColor,
                    onSelect = { haptics.tap(); filter = filter.copy(myColor = it) },
                )
                val opponents = remember(all, filter.mode) {
                    all.filter { filter.mode == null || it.mode == filter.mode }
                        .mapNotNull { it.opponentName }
                        .distinct()
                        .sortedBy { it.lowercase() }
                }
                if (opponents.size > 1) {
                    FilterRow(
                        options = listOf<Pair<String?, String>>(null to "Anyone") + opponents.map { it to it },
                        selected = filter.opponent,
                        onSelect = { haptics.tap(); filter = filter.copy(opponent = it) },
                    )
                }
                FilterRow(
                    options = listOf(
                        null to "Any help",
                        HelpFilter.HINTS to "Used hints",
                        HelpFilter.TAKEBACKS to "Took back",
                        HelpFilter.NONE to "No help",
                    ),
                    selected = filter.help,
                    onSelect = { haptics.tap(); filter = filter.copy(help = it) },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    summary(shown),
                    color = Felt.OnDarkDim,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (!filter.isDefault) {
                    TextButton(onClick = { haptics.tap(); filter = HistoryFilter() }) {
                        Text("Clear", color = Felt.Highlight, fontSize = 13.sp)
                    }
                }
                TextButton(onClick = { haptics.tap(); moreFilters = !moreFilters }) {
                    Text(
                        if (moreFilters) "Fewer filters" else "More filters",
                        color = Felt.Highlight,
                        fontSize = 13.sp,
                    )
                }
            }

            GlassPanel(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Column {
                    HeaderRow()
                    HorizontalDivider(color = Felt.GlassBorder)
                    if (shown.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (all.isEmpty()) {
                                    "No games yet. Games against the bots and online opponents show up here."
                                } else {
                                    "No games match these filters."
                                },
                                color = Felt.OnDarkDim,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        LazyColumn {
                            val replay = { e: HistoryEntry ->
                                if (e.moves != null) {
                                    { haptics.tap(); onReplay(e.id) }
                                } else {
                                    null
                                }
                            }
                            items(
                                items,
                                key = {
                                    when (it) {
                                        is HistoryItem.Single -> it.entry.id
                                        is HistoryItem.Series -> "series:${it.id}"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is HistoryItem.Single -> EntryRow(item.entry, selfLabel, replay(item.entry))
                                    is HistoryItem.Series -> SeriesBlock(item, selfLabel, replay)
                                }
                                HorizontalDivider(color = Felt.GlassBorder.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DateColumn = 76.dp
private val ResultColumn = 88.dp
/** Room for the replay chevron, kept on every row so the columns line up. */
private val ChevronColumn = 18.dp

@Composable
private fun HeaderRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RowPadding, vertical = 10.dp),
    ) {
        Box(Modifier.width(DateColumn)) { HeaderIcon(Icons.Default.CalendarToday, "Date") }
        Box(Modifier.weight(1f)) { Piece(Player.BLACK) }
        Box(Modifier.weight(1f)) { Piece(Player.WHITE) }
        Box(Modifier.width(ResultColumn)) { HeaderIcon(Icons.Default.EmojiEvents, "Result") }
        Spacer(Modifier.width(ChevronColumn))
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = label, tint = Felt.OnDarkDim, modifier = Modifier.size(18.dp))
}

private val RowPadding = 12.dp
private val SeriesInset = 6.dp
private val SeriesBorder = 1.dp

/**
 * The rounds of a series inside an outline, under a row with the score in
 * rounds. Rows inside are inset so their columns still line up with the rest.
 */
@Composable
private fun SeriesBlock(
    s: HistoryItem.Series,
    selfLabel: String,
    replay: (HistoryEntry) -> (() -> Unit)?,
) {
    val shape = RoundedCornerShape(10.dp)
    val inner = RowPadding - SeriesInset - SeriesBorder
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SeriesInset, vertical = 4.dp)
            .border(SeriesBorder, Felt.Highlight.copy(alpha = 0.45f), shape)
            .background(Felt.Highlight.copy(alpha = 0.05f), shape),
    ) {
        SeriesHeader(s, selfLabel, inner)
        for (e in s.shown) {
            HorizontalDivider(color = Felt.GlassBorder.copy(alpha = 0.4f))
            EntryRow(e, selfLabel, replay(e), horizontalPadding = inner)
        }
    }
}

/** "You 3 – 1 Vishy" over "Series · 4 rounds", with who's ahead. */
@Composable
private fun SeriesHeader(s: HistoryItem.Series, selfLabel: String, horizontalPadding: Dp) {
    val ctx = LocalContext.current
    val t = s.tally
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 10.dp),
    ) {
        Column(Modifier.width(DateColumn)) {
            val (day, time) = formatWhen(ctx, s.latest.lastActivity)
            Text(day, color = Felt.OnDark, fontSize = 13.sp)
            Text(time, color = Felt.OnDarkDim, fontSize = 12.sp)
        }
        Column(Modifier.weight(2f).padding(end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selfLabel,
                    color = Felt.OnDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "  ${t.wins} – ${t.losses}  ",
                    color = Felt.Highlight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    opponentLabel(s.latest),
                    color = Felt.OnDark,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            val drawn = if (t.draws > 0) " · ${t.draws} drawn" else ""
            Text("Series · ${s.rounds} rounds$drawn", color = Felt.OnDarkDim, fontSize = 11.sp, maxLines = 1)
        }
        Box(Modifier.width(ResultColumn)) {
            val (label, color) = when {
                s.latest.status == HistoryStatus.IN_PROGRESS -> "In progress" to Felt.Highlight
                t.wins > t.losses -> "Won" to Felt.Highlight
                t.wins < t.losses -> "Lost" to Felt.OnDark
                else -> "Level" to Felt.OnDark
            }
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(ChevronColumn))
    }
}

private fun opponentLabel(e: HistoryEntry): String =
    e.opponentName ?: if (e.mode == HistoryMode.ONLINE) "Online player" else "Bot"

/** One game; tappable to replay it when [onClick] is set. */
@Composable
private fun EntryRow(
    e: HistoryEntry,
    selfLabel: String,
    onClick: (() -> Unit)?,
    horizontalPadding: Dp = RowPadding,
) {
    val ctx = LocalContext.current
    val opponent = opponentLabel(e)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
    ) {
        Column(Modifier.width(DateColumn)) {
            val (day, time) = formatWhen(ctx, e.lastActivity)
            Text(day, color = Felt.OnDark, fontSize = 13.sp)
            Text(time, color = Felt.OnDarkDim, fontSize = 12.sp)
        }
        for (side in listOf(Player.BLACK, Player.WHITE)) {
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                if (side == e.myColor) {
                    SideName(selfLabel, bold = true, marks = helpMarks(e))
                } else {
                    SideName(
                        opponent,
                        bold = false,
                        marks = if (e.mode == HistoryMode.BOT) listOf(Icons.Default.SmartToy to "Bot") else emptyList(),
                    )
                }
            }
        }
        Column(Modifier.width(ResultColumn)) {
            val (label, color) = resultLabel(e)
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${e.blackCount}–${e.whiteCount}", color = Felt.OnDarkDim, fontSize = 11.sp, maxLines = 1)
                if (e.resignedBy != null) {
                    Spacer(Modifier.width(6.dp))
                    // A white flag: someone surrendered.
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = "Resigned",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Box(Modifier.width(ChevronColumn), contentAlignment = Alignment.CenterEnd) {
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Replay",
                    tint = Felt.OnDarkDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * A player's name with small [marks] after it (a robot for bots; a bulb and
 * an undo arrow for the help you had), which stay put if the name is cut short.
 */
@Composable
private fun SideName(name: String, bold: Boolean, marks: List<Pair<ImageVector, String>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            color = Felt.OnDark,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        for ((icon, label) in marks) {
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = label, tint = Felt.OnDarkDim, modifier = Modifier.size(14.dp))
        }
    }
}

/** Hints and takebacks the player used in [e], as marks for their name. */
private fun helpMarks(e: HistoryEntry): List<Pair<ImageVector, String>> = buildList {
    if (e.hintsUsed > 0) add(Icons.Default.Lightbulb to "Used hints")
    if (e.takebacks > 0) add(Icons.AutoMirrored.Filled.Undo to "Took moves back")
}

/** The board's own disc render, as the column heading for that colour. */
@Composable
private fun Piece(color: Player) {
    Image(
        painterResource(if (color == Player.BLACK) R.drawable.piece_black else R.drawable.piece_white),
        contentDescription = if (color == Player.BLACK) "Black" else "White",
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun <T> FilterRow(options: List<Pair<T?, String>>, selected: T?, onSelect: (T?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Text(
                label,
                color = if (on) Felt.Deep else Felt.OnDark,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .background(
                        if (on) Felt.Highlight.copy(alpha = 0.9f) else Felt.Glass,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

private fun resultLabel(e: HistoryEntry): Pair<String, Color> = when (e.status) {
    HistoryStatus.IN_PROGRESS -> "In progress" to Felt.Highlight
    HistoryStatus.ABANDONED -> "Abandoned" to Felt.OnDarkDim
    HistoryStatus.COMPLETED -> when (e.outcome) {
        HistoryOutcome.WIN -> "Won" to Felt.Highlight
        HistoryOutcome.LOSS -> "Lost" to Felt.OnDark
        else -> "Draw" to Felt.OnDark
    }
}

/** "12 games · 7 won, 4 lost, 1 drawn", counting only completed games in the tally. */
private fun summary(entries: List<HistoryEntry>): String {
    val n = entries.size
    val games = if (n == 1) "1 game" else "$n games"
    val t = HistoryTally.of(entries)
    if (t.wins + t.losses + t.draws == 0) return games
    return "$games · ${t.wins} won, ${t.losses} lost, ${t.draws} drawn"
}

/** Day (with the year only when it isn't this one) and time, in the device's style. */
private fun formatWhen(ctx: android.content.Context, millis: Long): Pair<String, String> {
    val locale = Locale.getDefault()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = then.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    val dayPattern = DateFormat.getBestDateTimePattern(locale, if (sameYear) "dMMM" else "dMMMyy")
    val date = Date(millis)
    return DateFormat.format(dayPattern, date).toString() to DateFormat.getTimeFormat(ctx).format(date)
}
