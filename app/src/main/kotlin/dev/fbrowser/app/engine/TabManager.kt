package dev.fbrowser.app.engine

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.fbrowser.app.FBrowserApp
import dev.fbrowser.app.R
import dev.fbrowser.app.data.BrowserSettings
import dev.fbrowser.app.data.JsonFiles
import dev.fbrowser.app.data.Library
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File

/**
 * Owns all tabs and their Gecko sessions.
 *
 * Stability: sessions are created lazily, at most [MAX_LIVE] stay open (the rest are hibernated
 * and later restored from their SessionState), content-process crashes are recovered, and the
 * session is persisted. Privacy: Gecko Enhanced Tracking Protection, state partitioning of
 * third-party cookies, HTTPS-first, tracking-parameter stripping, private-mode sessions.
 */
class TabManager(
    private val app: FBrowserApp,
    private val settingsProvider: () -> BrowserSettings,
    private val library: Library,
) {
    val tabs = mutableStateListOf<Tab>()

    var activeId by mutableLongStateOf(-1L)
        private set
    val active: Tab? get() = tabs.firstOrNull { it.id == activeId }

    var barsVisible by mutableStateOf(true)
    var fullscreen by mutableStateOf(false)
        private set

    private var nextId = 1L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var filePicker: ((Boolean, (List<Uri>) -> Unit) -> Unit)? = null
    private val sessionFile get() = File(app.filesDir, "session.json")

    private val appVersion: String by lazy {
        try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
    }

    internal fun settings(): BrowserSettings = settingsProvider()

    // ------------------------------------------------------------------ host activity

    fun attach(picker: (multiple: Boolean, onResult: (List<Uri>) -> Unit) -> Unit) {
        filePicker = picker
    }

    fun detach() {
        filePicker = null
    }

    // ------------------------------------------------------------------ engine runtime

    /** Starts Gecko (if needed) and applies the global privacy settings. */
    fun warmUp() {
        ensureRuntime()
    }

    private fun ensureRuntime(): GeckoRuntime {
        val first = !app.runtimeReady
        val runtime = app.runtime
        if (first) applyRuntimeSettings(runtime)
        return runtime
    }

    private fun applyRuntimeSettings(runtime: GeckoRuntime) {
        val s = settingsProvider()
        val blocking = runtime.settings.contentBlocking
        val categories = if (s.blockTrackers) {
            ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
        } else {
            ContentBlocking.AntiTracking.NONE
        }
        blocking.setAntiTracking(categories)
        // Third-party cookies are partitioned per site (Total Cookie Protection) instead of shared.
        val cookies = if (s.blockThirdPartyCookies) {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_ALL
        }
        blocking.setCookieBehavior(cookies)
        blocking.setCookieBehaviorPrivateMode(cookies)
        blocking.setEnhancedTrackingProtectionLevel(
            if (s.blockTrackers) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.NONE,
        )
        runtime.settings.setPreferredColorScheme(
            if (s.darkWebPages) GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT,
        )
    }

    fun applySettingsToAll() {
        if (!app.runtimeReady) return
        applyRuntimeSettings(app.runtime)
        val s = settingsProvider()
        tabs.forEach { tab ->
            tab.session?.settings?.let {
                it.setAllowJavascript(s.javascript)
                it.setUseTrackingProtection(trackingFor(tab.url, s))
            }
        }
    }

    private fun trackingFor(url: String, s: BrowserSettings): Boolean =
        s.blockTrackers && UrlUtils.siteKey(UrlUtils.hostOf(url)) !in s.allowlist

    // ------------------------------------------------------------------ tabs

    fun newTab(url: String = "", private: Boolean = false, select: Boolean = true): Tab? {
        val tab = Tab(nextId++, private, url)
        tabs.add(tab)
        if (select) select(tab.id)
        return tab
    }

    fun select(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        activeId = id
        tab.lastActive = SystemClock.uptimeMillis()
        barsVisible = true
        evictIfNeeded()
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]
        val wasActive = tab.id == activeId
        closeSession(tab)
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            newTab()
        } else if (wasActive) {
            select(tabs[index.coerceAtMost(tabs.lastIndex)].id)
        }
    }

    fun closeAll() {
        tabs.toList().forEach { closeSession(it) }
        tabs.clear()
        activeId = -1L
        newTab()
    }

    /** Opens [raw] (already a URL) in the current blank tab, or in a new one. */
    fun openUrl(raw: String) {
        val current = active
        val tab = if (current != null && current.isStartPage && !current.isPrivate) current else newTab() ?: return
        load(tab, raw)
    }

    internal fun openInNewTab(uri: String) {
        mainHandler.post {
            val parentPrivate = active?.isPrivate == true
            newTab(private = parentPrivate)?.let { load(it, uri) }
        }
    }

    // ------------------------------------------------------------------ navigation

    /** Address-bar entry point: text -> URL (or search) -> load. */
    fun go(tab: Tab, input: String) = load(tab, UrlUtils.resolve(input, settingsProvider().searchEngine))

    fun load(tab: Tab, url: String) {
        val s = settingsProvider()
        val prepared = prepare(tab, url, s)
        tab.error = null
        tab.skipInitialLoad = false
        tab.url = prepared
        tab.title = ""
        tab.host = UrlUtils.hostOf(prepared)
        tab.progress = 0
        tab.loading = true
        val session = tab.session ?: createSession(tab, initialLoad = false)
        tab.savedState = null
        applySiteSettings(tab, prepared, s)
        session.load(loaderFor(prepared, s))
    }

    private fun loaderFor(url: String, s: BrowserSettings): GeckoSession.Loader {
        val loader = GeckoSession.Loader().uri(url)
        if (s.sendPrivacySignals) {
            loader.additionalHeaders(mapOf("Sec-GPC" to "1", "DNT" to "1"))
                .headerFilter(GeckoSession.HEADER_FILTER_UNRESTRICTED_UNSAFE)
        }
        return loader
    }

    /**
     * URL hygiene shared by typed and link navigations: tracking-parameter removal and the
     * HTTPS-first upgrade. The upgrade is remembered so a failed secure connection (but never a
     * certificate error) can fall back to http.
     */
    internal fun prepare(tab: Tab, url: String, s: BrowserSettings): String {
        var out = url
        if (s.stripTrackingParams) out = UrlUtils.stripTracking(out)
        val pending = tab.pendingUpgrade
        if (out.startsWith("http://")) {
            if (s.httpsFirst && !UrlUtils.isLocalHost(out)) {
                val host = UrlUtils.hostOf(out)
                if (pending?.first == out) {
                    // The site sent us from https back to the same http URL: stop upgrading it.
                    tab.httpHosts.add(host)
                    tab.pendingUpgrade = null
                } else if (host !in tab.httpHosts) {
                    val upgraded = "https://" + out.removePrefix("http://")
                    tab.pendingUpgrade = out to upgraded
                    out = upgraded
                }
            }
        } else if (pending != null && pending.second != out) {
            tab.pendingUpgrade = null
        }
        return out
    }

    /** Per-site settings that must be in place before the request is sent. */
    private fun applySiteSettings(tab: Tab, url: String, s: BrowserSettings) {
        val session = tab.session ?: return
        val mode = s.uaFor(UrlUtils.siteKey(UrlUtils.hostOf(url)))
        if (mode != tab.uaMode) {
            tab.uaMode = mode
            applyUa(session, mode)
        }
        session.settings.setUseTrackingProtection(trackingFor(url, s))
    }

    private fun applyUa(session: GeckoSession, mode: UaMode) {
        session.settings.setUserAgentOverride(UserAgents.forMode(mode, appVersion))
        session.settings.setUserAgentMode(
            if (mode.isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
        )
        session.settings.setViewportMode(
            if (mode.isDesktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
        )
    }

    /** Compatibility switch from the page menu: changes the identity and reloads. */
    fun setUaMode(tab: Tab, mode: UaMode) {
        tab.uaMode = mode
        tab.session?.let {
            applyUa(it, mode)
            it.reload()
        }
    }

    /** Called for every top-level load request. Returns true to let Gecko continue the load. */
    internal fun handleLoadRequest(tab: Tab, uri: String, hasUserGesture: Boolean): Boolean {
        val scheme = uri.substringBefore(':', "").lowercase()
        when (scheme) {
            "http", "https" -> {
                val s = settingsProvider()
                val prepared = prepare(tab, uri, s)
                if (prepared != uri) {
                    mainHandler.post { tab.session?.load(loaderFor(prepared, s)) }
                    return false
                }
                tab.host = UrlUtils.hostOf(uri)
                applySiteSettings(tab, uri, s)
                return true
            }
            "about" -> return uri == "about:blank"
            "blob" -> return true
            "mailto", "tel", "sms" -> {
                // Only after a real tap, never from a script redirect.
                if (hasUserGesture) openExternal(Uri.parse(uri))
                return false
            }
        }
        // intent:, market:, file:, data:, javascript:, custom schemes: blocked.
        return false
    }

    internal fun handleLoadError(tab: Tab, uri: String?, category: Int, code: Int) {
        val failing = uri.orEmpty()
        val upgrade = tab.pendingUpgrade
        // HTTPS-first fallback only for "cannot connect" failures, never for certificate errors.
        if (upgrade != null && failing == upgrade.second &&
            (code == WebRequestError.ERROR_CONNECTION_REFUSED || code == WebRequestError.ERROR_NET_TIMEOUT)
        ) {
            tab.pendingUpgrade = null
            tab.httpHosts.add(UrlUtils.hostOf(upgrade.first))
            mainHandler.post { tab.session?.loadUri(upgrade.first) }
            return
        }
        tab.loading = false
        val kind = if (category == WebRequestError.ERROR_CATEGORY_SECURITY) ErrorKind.Certificate else ErrorKind.Network
        tab.error = TabError(kind, failing, "#$code")
    }

    fun goBack(): Boolean {
        val tab = active ?: return false
        if (!tab.canGoBack) return false
        tab.session?.goBack()
        return true
    }

    fun goForward() {
        val tab = active ?: return
        if (tab.canGoForward) tab.session?.goForward()
    }

    fun reload() {
        val tab = active ?: return
        val err = tab.error
        when {
            err != null -> load(tab, err.url)
            tab.session == null -> if (tab.url.isNotEmpty()) sessionFor(tab)
            else -> tab.session?.reload()
        }
    }

    fun stop() {
        active?.session?.stop()
    }

    // ------------------------------------------------------------------ session lifecycle

    /** Returns the tab's open session, creating (and restoring) it on first use. */
    fun sessionFor(tab: Tab): GeckoSession = tab.session ?: createSession(tab, initialLoad = true)

    private fun createSession(tab: Tab, initialLoad: Boolean): GeckoSession {
        val runtime = ensureRuntime()
        val s = settingsProvider()
        val mode = s.uaFor(UrlUtils.siteKey(UrlUtils.hostOf(tab.url)))
        tab.uaMode = mode

        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(tab.isPrivate)
                .useTrackingProtection(trackingFor(tab.url, s))
                .userAgentOverride(UserAgents.forMode(mode, appVersion))
                .userAgentMode(
                    if (mode.isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
                )
                .viewportMode(
                    if (mode.isDesktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
                )
                .allowJavascript(s.javascript)
                .suspendMediaWhenInactive(true)
                .build(),
        )
        val controller = TabController(tab, this)
        session.setNavigationDelegate(FNavigationDelegate(controller))
        session.setProgressDelegate(controller)
        session.setContentDelegate(controller)
        session.setContentBlockingDelegate(controller)
        session.setScrollDelegate(controller)
        // Camera, microphone, location and notifications have no PermissionDelegate: denied.
        session.setPromptDelegate(FilePromptHandler(this, app))
        session.open(runtime)
        tab.session = session

        if (initialLoad && !tab.skipInitialLoad) {
            val saved = tab.savedState
            tab.savedState = null
            if (saved != null) {
                session.restoreState(saved)
            } else if (tab.url.isNotEmpty()) {
                session.load(loaderFor(tab.url, s))
            }
        }
        return session
    }

    private fun closeSession(tab: Tab) {
        val session = tab.session ?: return
        tab.session = null
        session.close()
    }

    /** Keeps at most [MAX_LIVE] sessions open; the least recently used ones are hibernated. */
    private fun evictIfNeeded() {
        var excess = tabs.count { it.session != null } - MAX_LIVE
        if (excess <= 0) return
        for (tab in tabs.filter { it.session != null && it.id != activeId }.sortedBy { it.lastActive }) {
            if (excess <= 0) return
            hibernate(tab)
            excess--
        }
    }

    /** Frees the engine session; the SessionState kept by the controller restores it later. */
    fun hibernate(tab: Tab) {
        closeSession(tab)
        tab.sessionVersion++
    }

    /** Called from onTrimMemory: free every background session, they are restored on demand. */
    fun trimMemory() {
        tabs.filter { it.session != null && it.id != activeId }.forEach { hibernate(it) }
    }

    /** The content process died. Recover instead of leaving a dead tab. */
    internal fun onContentGone(tab: Tab, crashed: Boolean) {
        tab.session = null // Gecko has already closed it
        tab.progress = 0
        tab.loading = false
        if (crashed) {
            val now = SystemClock.uptimeMillis()
            tab.crashTimes.addLast(now)
            while (tab.crashTimes.isNotEmpty() && now - tab.crashTimes.first() > 30_000) tab.crashTimes.removeFirst()
            if (tab.crashTimes.size >= 3) {
                // Crash loop: stop reloading automatically and let the user decide.
                tab.skipInitialLoad = true
                tab.error = TabError(ErrorKind.Crash, tab.url, "")
            }
        }
        tab.sessionVersion++ // the UI creates a fresh session and restores the saved state
    }

    // ------------------------------------------------------------------ callbacks from the controller

    internal fun recordVisit(tab: Tab) {
        if (!tab.isPrivate && tab.error == null) library.addVisit(tab.url, tab.title)
    }

    internal fun onScroll(tab: Tab, scrollY: Int, delta: Int) {
        if (tab.id != activeId || !settingsProvider().hideBarsOnScroll) return
        if (scrollY <= 0) {
            barsVisible = true
            scrollAccumulated = 0
            return
        }
        if (delta * scrollAccumulated < 0) scrollAccumulated = 0
        scrollAccumulated += delta
        val now = SystemClock.uptimeMillis()
        if (now - lastBarToggle < 350) return // avoid relayout feedback loops
        if (scrollAccumulated > 140 && barsVisible) {
            barsVisible = false
            lastBarToggle = now
            scrollAccumulated = 0
        } else if (scrollAccumulated < -60 && !barsVisible) {
            barsVisible = true
            lastBarToggle = now
            scrollAccumulated = 0
        }
    }

    private var scrollAccumulated = 0
    private var lastBarToggle = 0L

    internal fun onFullScreen(on: Boolean) {
        fullscreen = on
    }

    fun exitFullscreen() {
        active?.session?.exitFullScreen()
        fullscreen = false
    }

    internal fun pickFiles(multiple: Boolean, onResult: (List<Uri>) -> Unit) {
        val picker = filePicker
        if (picker == null) onResult(emptyList()) else picker(multiple, onResult)
    }

    internal fun openExternal(uri: Uri) {
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // no app can handle it
        }
    }

    internal fun download(response: WebResponse) {
        val url = response.uri
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast(app.getString(R.string.download_unsupported))
            return
        }
        try {
            val headers = response.headers
            val mime = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.substringBefore(';')
            val disposition = headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value
            val name = URLUtil.guessFileName(url, disposition, mime).replace('/', '_')
            val request = DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (mime != null) request.setMimeType(mime)
            (app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast(app.getString(R.string.download_started, name))
        } catch (_: Exception) {
            toast(app.getString(R.string.download_failed))
        }
    }

    fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ data

    /** Wipes cookies, site storage and caches. */
    fun clearWebData() {
        ensureRuntime().storageController.clearData(StorageController.ClearFlags.ALL)
    }

    // ------------------------------------------------------------------ session persistence

    /** Persists regular (non-private) tabs. With "clear on exit" the session is deleted instead. */
    fun saveSession() {
        if (settingsProvider().clearOnExit) {
            sessionFile.delete()
            return
        }
        val regular = tabs.filter { !it.isPrivate }
        val arr = JSONArray()
        var activeIndex = 0
        regular.forEachIndexed { i, t ->
            if (t.id == activeId) activeIndex = i
            arr.put(JSONObject().put("u", t.url).put("t", t.title))
        }
        JsonFiles.write(sessionFile, JSONObject().put("tabs", arr).put("active", activeIndex).toString())
    }

    /** Recreates tabs without sessions; only the shown tab gets a session. */
    fun restoreSession(): Boolean {
        val text = JsonFiles.readText(sessionFile) ?: return false
        try {
            val root = JSONObject(text)
            val arr = root.getJSONArray("tabs")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tabs.add(Tab(nextId++, false, o.optString("u")).also { it.title = o.optString("t") })
            }
            if (tabs.isEmpty()) return false
            select(tabs[root.optInt("active", 0).coerceIn(0, tabs.lastIndex)].id)
            return true
        } catch (_: Exception) {
            tabs.clear()
            return false
        }
    }

    fun destroy() {
        tabs.toList().forEach { closeSession(it) }
        tabs.clear()
    }

    private companion object {
        const val MAX_LIVE = 4
    }
}
