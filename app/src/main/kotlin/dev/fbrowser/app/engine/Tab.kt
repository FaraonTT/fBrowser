package dev.fbrowser.app.engine

import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger

enum class ErrorKind { Network, Certificate, Crash }

@Immutable
data class TabError(val kind: ErrorKind, val url: String, val detail: String)

/**
 * One browser tab. Fields read by the UI are Compose state; everything about the live WebView is
 * plain (non-observable) engine state. A tab can exist without a WebView (new, restored or
 * hibernated tabs): the WebView is created lazily the first time the tab is shown, which keeps
 * startup fast and memory low.
 */
@Stable
class Tab(val id: Long, val isPrivate: Boolean, startUrl: String = "") {
    // ---- observed by the UI ----
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blockedCount by mutableIntStateOf(0)
    var favicon by mutableStateOf<Bitmap?>(null)
    var desktopSite by mutableStateOf(false)
    var error by mutableStateOf<TabError?>(null)

    /** Bumped whenever the WebView instance is replaced so the UI re-attaches the right one. */
    var webViewVersion by mutableIntStateOf(0)

    // ---- engine state ----
    var webView: WebView? = null
    var contextWrapper: MutableContextWrapper? = null
    var savedState: Bundle? = null
    var lastActive: Long = 0L
    val blocked = AtomicInteger()

    @Volatile
    var host: String = ""
    var pendingUpgrade: Pair<String, String>? = null
    val httpHosts = HashSet<String>()
    val crashTimes = ArrayDeque<Long>()
    var skipInitialLoad = false

    val isStartPage: Boolean get() = url.isEmpty()
    val isSecure: Boolean get() = url.startsWith("https://")
}
