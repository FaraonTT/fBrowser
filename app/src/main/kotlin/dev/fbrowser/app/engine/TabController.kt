package dev.fbrowser.app.engine

import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/** Callbacks from the Java navigation delegate (see FNavigationDelegate). */
interface NavigationHandler {
    fun onLocationChange(url: String?)
    fun onCanGoBack(canGoBack: Boolean)
    fun onCanGoForward(canGoForward: Boolean)

    /** @return true to let Gecko continue the load, false to cancel it. */
    fun onLoadRequest(uri: String, isRedirect: Boolean, hasUserGesture: Boolean): Boolean
    fun onNewWindow(uri: String)
    fun onLoadError(uri: String?, category: Int, code: Int)
}

/** Connects one Gecko session to its [Tab]; every Gecko delegate callback arrives on the UI thread. */
internal class TabController(
    private val tab: Tab,
    private val manager: TabManager,
) : NavigationHandler,
    GeckoSession.ProgressDelegate,
    GeckoSession.ContentDelegate,
    ContentBlocking.Delegate,
    GeckoSession.ScrollDelegate {

    private var lastScrollY = 0

    // ---- navigation ----

    override fun onLocationChange(url: String?) {
        if (!url.isNullOrEmpty() && url != "about:blank") {
            tab.url = url // keeps the address bar right for single-page apps
            tab.host = UrlUtils.hostOf(url)
        }
    }

    override fun onCanGoBack(canGoBack: Boolean) {
        tab.canGoBack = canGoBack
    }

    override fun onCanGoForward(canGoForward: Boolean) {
        tab.canGoForward = canGoForward
    }

    override fun onLoadRequest(uri: String, isRedirect: Boolean, hasUserGesture: Boolean): Boolean =
        manager.handleLoadRequest(tab, uri, hasUserGesture)

    override fun onNewWindow(uri: String) {
        manager.openInNewTab(uri)
    }

    override fun onLoadError(uri: String?, category: Int, code: Int) {
        manager.handleLoadError(tab, uri, category, code)
    }

    // ---- progress ----

    override fun onPageStart(session: GeckoSession, url: String) {
        tab.error = null
        tab.loading = true
        tab.progress = 0
        tab.blockedCount = 0
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        tab.loading = false
        if (success) {
            tab.progress = 100
            manager.recordVisit(tab)
        }
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        tab.progress = progress
    }

    override fun onSecurityChange(
        session: GeckoSession,
        securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
    ) {
        tab.isSecure = securityInfo.isSecure
    }

    override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
        tab.savedState = sessionState // used to restore the tab after hibernation or a crash
    }

    // ---- content ----

    override fun onTitleChange(session: GeckoSession, title: String?) {
        tab.title = title.orEmpty()
    }

    override fun onCrash(session: GeckoSession) {
        manager.onContentGone(tab, crashed = true)
    }

    override fun onKill(session: GeckoSession) {
        manager.onContentGone(tab, crashed = false)
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        manager.onFullScreen(fullScreen)
    }

    override fun onCloseRequest(session: GeckoSession) {
        manager.close(tab.id)
    }

    override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
        manager.download(response)
    }

    // ---- tracker blocking statistics (Gecko Enhanced Tracking Protection) ----

    override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
        tab.blockedCount += 1
    }

    // ---- scrolling (hides the toolbar while reading) ----

    override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
        val delta = scrollY - lastScrollY
        lastScrollY = scrollY
        manager.onScroll(tab, scrollY, delta)
    }
}

/** File upload (<input type=file>). Everything else Gecko asks for is declined by default. */
internal class FilePromptHandler(
    private val manager: TabManager,
    private val context: android.content.Context,
) : GeckoSession.PromptDelegate {

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
        manager.pickFiles(multiple) { uris ->
            when {
                uris.isEmpty() -> result.complete(prompt.dismiss())
                multiple -> result.complete(prompt.confirm(context, uris.toTypedArray()))
                else -> result.complete(prompt.confirm(context, uris[0]))
            }
        }
        return result
    }
}
