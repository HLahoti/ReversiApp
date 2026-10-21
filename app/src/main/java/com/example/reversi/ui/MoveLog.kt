package com.example.reversi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reversi.analysis.Grade
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Player

private val CELL_WIDTH = 36.dp
private val CELL_HEIGHT = 24.dp
private val CELL_GAP = 2.dp
private val COLUMN_GAP = 3.dp
private val COLUMN_PAD = 2.dp
private val STRIP_HEIGHT = CELL_HEIGHT * 3 + CELL_GAP * 2 + COLUMN_PAD * 2

@Composable
fun MoveLog(
    entries: List<MoveLogEntry>,
    currentIndex: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Grades from an endgame review, by index into [entries]. */
    marks: Map<Int, Grade> = emptyMap(),
) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size, currentIndex) {
        // -1 is the opening position, before the first entry (a replay's start).
        val target = when {
            currentIndex in entries.indices -> currentIndex
            currentIndex < 0 -> 0
            else -> entries.lastIndex
        }
        if (target >= 0) listState.animateScrollToItem(target)
    }
    LazyRow(
        state = listState,
        modifier = modifier.height(STRIP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        itemsIndexed(entries) { index, e ->
            MoveColumn(
                entry = e,
                grade = marks[index],
                isCurrent = index == currentIndex,
                onClick = { onJump(index) },
            )
        }
    }
}

@Composable
private fun MoveColumn(entry: MoveLogEntry, grade: Grade?, isCurrent: Boolean, onClick: () -> Unit) {
    val moverBg = if (entry.mover == Player.BLACK) AppConfig.Palette.BlackBadge else AppConfig.Palette.WhiteBadge
    val moverFg = if (entry.mover == Player.BLACK) Color.White else Color.Black
    val outline = if (isCurrent) {
        Modifier.border(1.5.dp, AppConfig.Palette.Highlight, RoundedCornerShape(6.dp))
    } else {
        Modifier
    }
    Column(
        modifier = outline
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(COLUMN_PAD),
        verticalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        Box {
            LogCell(entry.label + (grade?.suffix ?: ""), moverBg, moverFg)
            if (grade != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(7.dp)
                        .background(grade.color, CircleShape),
                )
            }
        }
        LogCell(entry.black.toString(), AppConfig.Palette.BlackBadge, Color.White)
        LogCell(entry.white.toString(), AppConfig.Palette.WhiteBadge, Color.Black)
    }
}

@Composable
private fun LogCell(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .size(width = CELL_WIDTH, height = CELL_HEIGHT)
            .background(bg, shape = RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
