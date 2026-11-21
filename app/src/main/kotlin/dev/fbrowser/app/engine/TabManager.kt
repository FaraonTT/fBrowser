package dev.fbrowser.app.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.fbrowser.app.R
import dev.fbrowser.app.data.BrowserSettings
import dev.fbrowser.app.data.JsonFiles
import dev.fbrowser.app.data.Library
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val PRIVATE_PROFILE = "fbrowser_private"

/**
 * Owns all tabs and their WebViews.
 *
 * Stability: WebViews are created lazily, at most [MAX_LIVE] stay alive (the rest are hibernated
 * into a saved-state Bundle), renderer crashes are recovered, and the session is persisted.
 * Privacy: private tabs live in a separate WebView Profile that is deleted when the last private
 * tab closes (and purged before the first private tab of every process).
 */
class TabManager(
    private val app: Application,
    private val settingsProvider: () -> BrowserSettings,
    private val library: Library,
    private val blockList: BlockList,
) {
    val tabs = mutableStateListOf<Tab>()

    var activeId by mutableLongStateOf(-1L)
        private set
    val active: Tab? get() = tabs.firstOrNull { it.id == activeId }

    var barsVisible by mutableStateOf(true)
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    var findOpen by mutableStateOf(false)
        private set
    var findActive by mutableIntStateOf(0)
        private set
    var findTotal by mutableIntStateOf(0)
        private set

    val privateSupported: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }

    private var nextId = 1L
    private var host: Activity? = null
    private var fileChooser: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean)? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var privatePurged = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionFile get() = File(app.filesDir, "session.json")

    // ---- user agents: drop the "; wv" WebView marker so sites do not serve degraded pages ----
    private val defaultUa: String by lazy { WebSettings.getDefaultUserAgent(app) }
    private val mobileUa: String by lazy { defaultUa.replace("; wv", "").replace("Version/4.0 ", "") }
    private val desktopUa: String by lazy {
        val major = Regex("Chrome/(\\d+)").find(defaultUa)?.groupValues?.get(1) ?: "140"
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    internal fun settings(): BrowserSettings = settingsProvider()

    // ------------------------------------------------------------------ host activity

    fun attach(
        activity: Activity,
        chooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    ) {
        host = activity
        fileChooser = chooser
        tabs.forEach { it.contextWrapper?.setBaseContext(activity) }
    }

    /** Replace the Activity with the application context so no WebView leaks the Activity. */
    fun detach(activity: Activity) {
        if (host !== activity) return
        host = null
        fileChooser = null
        tabs.forEach { it.contextWrapper?.setBaseContext(app) }
    }

    fun onHostPause() { active?.webView?.onPause() }
    fun onHostResume() { active?.webView?.onResume() }

    // ------------------------------------------------------------------ tabs

    fun newTab(url: String = "", private: Boolean = false, select: Boolean = true): Tab? {
        if (private) {
            if (!privateSupported) return null
            if (!privatePurged && tabs.none { it.isPrivate }) {
                privatePurged = true
                deletePrivateProfile() // drop leftovers of a previous process that was killed
            }
        }
        val tab = Tab(nextId++, private, url)
        tabs.add(tab)
        if (select) select(tab.id)
        return tab
    }

    fun select(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        if (id != activeId) active?.webView?.onPause()
        activeId = id
        tab.lastActive = SystemClock.uptimeMillis()
        tab.webView?.onResume()
        barsVisible = true
        closeFind()
        evictIfNeeded()
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]
        val wasActive = tab.id == activeId
        destroyWebView(tab)
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            newTab()
        } else if (wasActive) {
            select(tabs[index.coerceAtMost(tabs.lastIndex)].id)
        }
        if (privateSupported && tabs.none { it.isPrivate }) deletePrivateProfile()
    }

    fun closeAll() {
        tabs.toList().forEach { destroyWebView(it) }
        tabs.clear()
        activeId = -1L
        if (privateSupported) deletePrivateProfile()
        newTab()
    }

    /** Opens [raw] (already a URL) in the current blank tab, or in a new one. */
    fun openUrl(raw: String) {
        val current = active
        val tab = if (current != null && current.isStartPage && !current.isPrivate) current else newTab() ?: return
        load(tab, raw)
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
        val wv = tab.webView ?: createWebView(tab, initialLoad = false)
        tab.savedState = null
        wv.loadUrl(prepared, privacyHeaders(s))
        if (tab.id == activeId) wv.onResume()
    }

    /**
     * Applies URL hygiene shared by typed and link navigations: tracking-parameter removal and
     * HTTPS-first upgrade. Records the upgrade so the client can fall back to http only when the
     * secure connection cannot be established.
     */
    internal fun prepare(tab: Tab, url: String, s: BrowserSettings): String {
        var out = url
        if (s.stripTrackingParams) out = UrlUtils.stripTracking(out)
        val previous = tab.pendingUpgrade
        tab.pendingUpgrade = null
        if (s.httpsFirst && out.startsWith("http://") && !UrlUtils.isLocalHost(out)) {
            val host = UrlUtils.hostOf(out)
            if (previous?.first == out) {
                // The site redirected us from https back to the same http URL: stop upgrading it.
                tab.httpHosts.add(host)
            } else if (host !in tab.httpHosts) {
                val upgraded = "https://" + out.removePrefix("http://")
                tab.pendingUpgrade = out to upgraded
                out = upgraded
            }
        }
        return out
    }

    internal fun privacyHeaders(s: BrowserSettings): Map<String, String> =
        if (s.sendPrivacySignals) mapOf("Sec-GPC" to "1", "DNT" to "1") else emptyMap()

    fun goBack(): Boolean {
        val wv = active?.webView ?: return false
        if (!wv.canGoBack()) return false
        wv.goBack()
        return true
    }

    fun goForward() { active?.webView?.takeIf { it.canGoForward() }?.goForward() }

    fun reload() {
        val tab = active ?: return
        val err = tab.error
        when {
            err != null -> load(tab, err.url)
            tab.webView == null -> if (tab.url.isNotEmpty()) webViewFor(tab)
            else -> tab.webView?.reload()
        }
    }

    fun stop() { active?.webView?.stopLoading() }

    fun setDesktop(tab: Tab, enabled: Boolean) {
        tab.desktopSite = enabled
        tab.webView?.let {
            it.settings.userAgentString = if (enabled) desktopUa else mobileUa
            it.reload()
        }
    }

    // ------------------------------------------------------------------ WebView lifecycle

    /** Returns the tab's WebView, creating (and restoring) it on first use. */
    fun webViewFor(tab: Tab): WebView =
        tab.webView ?: createWebView(tab, initialLoad = true).also { if (tab.id == activeId) it.onResume() }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWebView(tab: Tab, initialLoad: Boolean): WebView {
        val s = settingsProvider()
        val wrapper = MutableContextWrapper(host ?: app)
        val wv = WebView(wrapper)
        // The profile must be chosen before the WebView is used for anything else.
        if (tab.isPrivate && privateSupported) {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
            WebViewCompat.setProfile(wv, PRIVATE_PROFILE)
        }
        tab.webView = wv
        tab.contextWrapper = wrapper
        applySettings(wv, tab, s)
        wv.webViewClient = FWebViewClient(tab, this)
        wv.webChromeClient = FChromeClient(tab, this)
        wv.setDownloadListener { url, userAgent, contentDisposition, mime, _ ->
            download(tab, url, userAgent, contentDisposition, mime)
        }
        wv.setFindListener { ordinal, total, _ ->
            if (tab.id == activeId) {
                findActive = if (total == 0) 0 else ordinal + 1
                findTotal = total
            }
        }
        installScrollHandler(wv, tab)

        if (initialLoad && !tab.skipInitialLoad) {
            val saved = tab.savedState
            tab.savedState = null
            val restored = saved?.let { wv.restoreState(it) }
            if (restored == null && tab.url.isNotEmpty()) wv.loadUrl(tab.url, privacyHeaders(s))
        }
        return wv
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(wv: WebView, tab: Tab, s: BrowserSettings) {
        wv.settings.apply {
            javaScriptEnabled = s.javascript
            domStorageEnabled = true
            // Hardening: no local file or content:// access from web content.
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = s.safeBrowsing
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = if (tab.desktopSite) desktopUa else mobileUa
        }
        cookieManagerFor(tab).apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, !s.blockThirdPartyCookies)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, s.darkWebPages)
        }
    }

    fun applySettingsToAll() {
        val s = settingsProvider()
        tabs.forEach { tab -> tab.webView?.let { applySettings(it, tab, s) } }
    }

    private fun cookieManagerFor(tab: Tab): CookieManager =
        if (tab.isPrivate && privateSupported) {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE).cookieManager
        } else {
            CookieManager.getInstance()
        }

    /** Hide the toolbar while reading (scroll down) and bring it back on scroll up. */
    private fun installScrollHandler(wv: WebView, tab: Tab) {
        var accumulated = 0
        var lastToggle = 0L
        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (tab.id != activeId || !settingsProvider().hideBarsOnScroll) return@setOnScrollChangeListener
            if (scrollY <= 0) {
                barsVisible = true
                accumulated = 0
                return@setOnScrollChangeListener
            }
            val dy = scrollY - oldScrollY
            if (dy * accumulated < 0) accumulated = 0
            accumulated += dy
            val now = SystemClock.uptimeMillis()
            if (now - lastToggle < 350) return@setOnScrollChangeListener // avoid relayout feedback loops
            if (accumulated > 140 && barsVisible) {
                barsVisible = false
                lastToggle = now
                accumulated = 0
            } else if (accumulated < -60 && !barsVisible) {
                barsVisible = true
                lastToggle = now
                accumulated = 0
            }
        }
    }

    /** Keeps at most [MAX_LIVE] WebViews alive; the least recently used are hibernated. */
    private fun evictIfNeeded() {
        var excess = tabs.count { it.webView != null } - MAX_LIVE
        if (excess <= 0) return
        tabs.filter { it.webView != null && it.id != activeId }
            .sortedBy { it.lastActive }
            .forEach {
                if (excess <= 0) return
                hibernate(it)
                excess--
            }
    }

    fun hibernate(tab: Tab) {
        val wv = tab.webView ?: return
        val state = Bundle()
        wv.saveState(state)
        tab.savedState = state
        destroyWebView(tab)
    }

    /** Called from onTrimMemory: free every background WebView, they are restored on demand. */
    fun trimMemory() {
        tabs.filter { it.webView != null && it.id != activeId }.forEach { hibernate(it) }
    }

    private fun destroyWebView(tab: Tab) {
        val wv = tab.webView ?: return
        tab.webView = null
        tab.contextWrapper = null
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.webViewClient = android.webkit.WebViewClient()
        wv.webChromeClient = null
        wv.setDownloadListener(null)
        wv.destroy()
        tab.webViewVersion++
    }

    /** The renderer process died (crash or OOM kill). Recover instead of taking the app down. */
    internal fun onRenderGone(tab: Tab) {
        val now = SystemClock.uptimeMillis()
        tab.crashTimes.addLast(now)
        while (tab.crashTimes.isNotEmpty() && now - tab.crashTimes.first() > 30_000) tab.crashTimes.removeFirst()
        tab.savedState = null
        tab.progress = 0
        tab.loading = false
        if (tab.crashTimes.size >= 3) {
            // Crash loop: stop reloading automatically and let the user decide.
            tab.skipInitialLoad = true
            tab.error = TabError(ErrorKind.Crash, tab.url, "")
        }
        destroyWebView(tab) // UI re-attaches a fresh WebView through webViewVersion
    }

    // ------------------------------------------------------------------ privacy helpers

    internal fun isBlockingActive(pageHost: String): Boolean {
        val s = settingsProvider()
        return s.blockTrackers && UrlUtils.siteKey(pageHost) !in s.allowlist
    }

    internal fun shouldBlock(host: String): Boolean = blockList.isBlocked(host)

    internal fun recordVisit(tab: Tab, url: String, title: String) {
        if (!tab.isPrivate && tab.error == null) library.addVisit(url, title)
    }

    internal fun syncNavigation(tab: Tab, view: WebView) {
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

    internal fun openExternal(uri: Uri) {
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // no app can handle it
        }
    }

    private fun deletePrivateProfile(retry: Boolean = true) {
        try {
            ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
        } catch (_: IllegalStateException) {
            // profile still in use by a WebView that is being torn down: try once more shortly
            if (retry) mainHandler.postDelayed({
                if (tabs.none { it.isPrivate }) deletePrivateProfile(retry = false)
            }, 1_500)
        } catch (_: Exception) {
            // feature missing or already gone
        }
    }

    /** Wipes cookies, site storage and caches of regular browsing. Private tabs are untouched. */
    fun clearWebData() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        tabs.filter { !it.isPrivate }.forEach {
            it.webView?.clearCache(true)
            it.webView?.clearHistory()
        }
    }

    // ------------------------------------------------------------------ find in page

    fun openFind() { findOpen = true; findActive = 0; findTotal = 0 }

    fun find(query: String) {
        val wv = active?.webView ?: return
        if (query.isEmpty()) {
            wv.clearMatches()
            findActive = 0
            findTotal = 0
        } else {
            wv.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) { active?.webView?.findNext(forward) }

    fun closeFind() {
        if (!findOpen) return
        findOpen = false
        active?.webView?.clearMatches()
    }

    // ------------------------------------------------------------------ fullscreen video, files, downloads

    internal fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        customViewCallback = callback
    }

    fun hideCustomView() {
        if (fullscreenView == null) return
        fullscreenView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    internal fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        val launcher = fileChooser
        if (launcher == null) {
            callback.onReceiveValue(null)
            return false
        }
        return launcher(callback, params)
    }

    private fun download(tab: Tab, url: String, userAgent: String?, contentDisposition: String?, mime: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast(app.getString(R.string.download_unsupported))
            return
        }
        try {
            val name = URLUtil.guessFileName(url, contentDisposition, mime).replace('/', '_')
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mime)
                .addRequestHeader("User-Agent", userAgent ?: mobileUa)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            cookieManagerFor(tab).getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            (app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast(app.getString(R.string.download_started, name))
        } catch (_: Exception) {
            toast(app.getString(R.string.download_failed))
        }
    }

    fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ session

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

    /** Recreates tabs without WebViews; only the active one gets a WebView when it is shown. */
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
        hideCustomView()
        tabs.toList().forEach { destroyWebView(it) }
        tabs.clear()
        if (privateSupported) deletePrivateProfile()
    }

    private companion object {
        const val MAX_LIVE = 4
    }
}
