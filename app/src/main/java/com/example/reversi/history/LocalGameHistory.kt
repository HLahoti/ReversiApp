package com.example.reversi.history

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * [GameHistory] kept in one JSON file on the device. The list is held in
 * memory and the whole file rewritten after each change, off the main thread
 * and one write at a time so they land in order. A corrupt file starts the
 * history afresh rather than crashing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalGameHistory(
    private val file: File,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) : GameHistory {
    constructor(context: Context) : this(File(context.filesDir, "history.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(HistoryEntry.serializer())

    private val _entries = MutableStateFlow(sorted(read()))
    override val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    override fun record(entry: HistoryEntry) = change { list ->
        val old = list.find { it.id == entry.id }
        val kept = if (old == null) entry else entry.copy(startedAt = old.startedAt)
        listOf(kept) + list.filter { it.id != entry.id }
    }

    override fun abandon(id: String) = change { list ->
        val now = System.currentTimeMillis()
        list.map {
            if (it.id == id && it.status == HistoryStatus.IN_PROGRESS) {
                it.copy(status = HistoryStatus.ABANDONED, endedAt = now)
            } else {
                it
            }
        }
    }

    override fun abandonAllExcept(resumable: Set<String>) = change { list ->
        list.map {
            if (it.status == HistoryStatus.IN_PROGRESS && it.id !in resumable) {
                it.copy(status = HistoryStatus.ABANDONED, endedAt = it.updatedAt)
            } else {
                it
            }
        }
    }

    private fun change(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        _entries.update { cur -> sorted(transform(cur)).take(MAX_ENTRIES) }
        // Writes run one at a time; each saves whatever is newest by then.
        scope.launch { write(_entries.value) }
    }

    private fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun write(list: List<HistoryEntry>) {
        // Write beside, then swap in, so a crash mid-write can't leave half a file.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun sorted(list: List<HistoryEntry>) = list.sortedByDescending { it.lastActivity }

    companion object {
        /** Oldest entries beyond this are dropped. */
        const val MAX_ENTRIES = 1000
    }
}
