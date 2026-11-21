package dev.fbrowser.app.engine

import android.content.Context

/**
 * Host-based tracker / ad blocklist. Lookup is O(number of labels in the host) because it walks
 * parent domains through a [HashSet] instead of scanning the whole list, so it is cheap enough to
 * run for every sub-resource request on WebView's IO thread.
 */
class BlockList {
    @Volatile
    private var hosts: Set<String> = emptySet()

    /** Loads assets/blocklist.txt. On any failure the browser simply runs without blocking. */
    fun load(context: Context) {
        try {
            val set = HashSet<String>(1024)
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { set.add(it) }
            }
            hosts = set
        } catch (_: Exception) {
            // keep running without a list rather than crash
        }
    }

    fun isBlocked(host: String): Boolean {
        val set = hosts
        if (set.isEmpty()) return false
        var h = host
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
