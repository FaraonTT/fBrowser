package dev.fbrowser.app.engine

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession

enum class ErrorKind { Network, Certificate, Crash }

@Immutable
data class TabError(val kind: ErrorKind, val url: String, val detail: String)

/**
 * One browser tab. Fields read by the UI are Compose state; the GeckoSession is plain engine state.
 * A tab can exist without a session (new, restored or hibernated tabs): the session is created
 * lazily when the tab is first shown, which keeps startup fast and memory low.
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
    var isSecure by mutableStateOf(true)
    var uaMode by mutableStateOf(UaMode.FBROWSER)
    var error by mutableStateOf<TabError?>(null)

    /** Gecko does not expose favicons; kept so list screens can show one if it is ever added. */
    var favicon by mutableStateOf<Bitmap?>(null)

    /** Bumped whenever the GeckoSession is replaced so the UI attaches the right one. */
    var sessionVersion by mutableIntStateOf(0)

    // ---- engine state ----
    var session: GeckoSession? = null
    var savedState: GeckoSession.SessionState? = null
    var lastActive: Long = 0L

    @Volatile
    var host: String = ""
    var pendingUpgrade: Pair<String, String>? = null
    val httpHosts = HashSet<String>()
    val crashTimes = ArrayDeque<Long>()
    var skipInitialLoad = false

    val isStartPage: Boolean get() = url.isEmpty()
}
