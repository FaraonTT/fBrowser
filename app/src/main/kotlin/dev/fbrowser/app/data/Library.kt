package dev.fbrowser.app.data

import android.content.Context
import android.util.AtomicFile
import dev.fbrowser.app.engine.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class Bookmark(val url: String, val title: String, val added: Long)
data class HistoryEntry(val url: String, val title: String, val visited: Long)
data class Suggestion(val url: String, val title: String, val isBookmark: Boolean)

/**
 * Crash-safe JSON files. [AtomicFile] writes to a backup first, so a crash or power loss in the
 * middle of a write can never leave a half-written (corrupt) file behind.
 */
internal object JsonFiles {
    fun readText(file: File): String? = try {
        AtomicFile(file).readFully().toString(Charsets.UTF_8)
    } catch (_: IOException) {
        null
    }

    fun write(file: File, text: String) {
        val af = AtomicFile(file)
        val out = af.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            af.finishWrite(out)
        } catch (e: IOException) {
            af.failWrite(out)
        }
    }

    fun <T> readArray(file: File, parse: (JSONObject) -> T): List<T> = try {
        val arr = JSONArray(readText(file) ?: return emptyList())
        buildList(arr.length()) {
            for (i in 0 until arr.length()) add(parse(arr.getJSONObject(i)))
        }
    } catch (_: Exception) {
        emptyList() // a damaged file must never crash the app
    }
}

/** Bookmarks and history, kept in memory and persisted as small JSON files (no database needed). */
class Library(context: Context, private val scope: CoroutineScope) {
    private val bookmarksFile = File(context.filesDir, "bookmarks.json")
    private val historyFile = File(context.filesDir, "history.json")
    private val writeLock = Mutex()
    private var historyJob: Job? = null

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val loaded = scope.async(Dispatchers.IO) {
        _bookmarks.value = JsonFiles.readArray(bookmarksFile) {
            Bookmark(it.getString("u"), it.optString("t"), it.optLong("a"))
        }
        _history.value = JsonFiles.readArray(historyFile) {
            HistoryEntry(it.getString("u"), it.optString("t"), it.optLong("v"))
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    fun isBookmarked(url: String): Boolean = _bookmarks.value.any { it.url == url }

    fun toggleBookmark(url: String, title: String) {
        if (url.isBlank()) return
        val cur = _bookmarks.value
        _bookmarks.value = if (cur.any { it.url == url }) {
            cur.filterNot { it.url == url }
        } else {
            listOf(Bookmark(url, title.ifBlank { UrlUtils.displayHost(url) }, System.currentTimeMillis())) + cur
        }
        persistBookmarks()
    }

    fun removeBookmark(url: String) {
        _bookmarks.value = _bookmarks.value.filterNot { it.url == url }
        persistBookmarks()
    }

    fun addVisit(url: String, title: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val now = System.currentTimeMillis()
        val cur = _history.value
        val first = cur.firstOrNull()
        _history.value = if (first != null && first.url == url) {
            listOf(first.copy(title = title.ifBlank { first.title }, visited = now)) + cur.drop(1)
        } else {
            (listOf(HistoryEntry(url, title, now)) + cur).take(MAX_HISTORY)
        }
        historyJob?.cancel()
        historyJob = scope.launch {
            delay(2_000) // debounce: one disk write per burst of navigations
            writeHistory()
        }
    }

    fun removeHistory(url: String) {
        _history.value = _history.value.filterNot { it.url == url }
        flush()
    }

    fun clearHistory() {
        _history.value = emptyList()
        flush()
    }

    /** Write pending history immediately (called when the app goes to background). */
    fun flush() {
        historyJob?.cancel()
        scope.launch { writeHistory() }
    }

    /** Fast in-memory suggestions from bookmarks first, then history. */
    fun suggest(query: String, limit: Int = 6): List<Suggestion> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val out = LinkedHashMap<String, Suggestion>()
        for (b in _bookmarks.value) {
            if (b.url.lowercase().contains(q) || b.title.lowercase().contains(q)) {
                out.putIfAbsent(b.url, Suggestion(b.url, b.title, true))
            }
        }
        for (h in _history.value) {
            if (out.size >= limit) break
            if (h.url.lowercase().contains(q) || h.title.lowercase().contains(q)) {
                out.putIfAbsent(h.url, Suggestion(h.url, h.title, false))
            }
        }
        return out.values.take(limit)
    }

    private fun persistBookmarks() {
        val snapshot = _bookmarks.value
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val arr = JSONArray()
                snapshot.forEach { arr.put(JSONObject().put("u", it.url).put("t", it.title).put("a", it.added)) }
                JsonFiles.write(bookmarksFile, arr.toString())
            }
        }
    }

    private suspend fun writeHistory() {
        val snapshot = _history.value
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                val arr = JSONArray()
                snapshot.forEach { arr.put(JSONObject().put("u", it.url).put("t", it.title).put("v", it.visited)) }
                JsonFiles.write(historyFile, arr.toString())
            }
        }
    }

    private companion object {
        const val MAX_HISTORY = 3000
    }
}
