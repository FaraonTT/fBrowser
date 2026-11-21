package dev.fbrowser.app.engine

import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import java.io.ByteArrayInputStream

internal class FWebViewClient(
    private val tab: Tab,
    private val manager: TabManager,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                if (!request.isForMainFrame) return false
                val s = manager.settings()
                val original = uri.toString()
                val prepared = manager.prepare(tab, original, s)
                if (prepared != original) {
                    view.loadUrl(prepared, manager.privacyHeaders(s))
                    return true
                }
                return false
            }
            "about", "blob" -> return false
            "mailto", "tel", "sms" -> {
                // Only when the user actually tapped a link, never from a script redirect.
                if (request.hasGesture()) manager.openExternal(uri)
                return true
            }
        }
        // intent:, market:, file:, javascript:, data:, custom schemes: blocked.
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        tab.error = null
        tab.loading = true
        tab.blocked.set(0)
        tab.blockedCount = 0
        if (!url.isNullOrEmpty() && url != "about:blank") {
            tab.url = url
            tab.host = UrlUtils.hostOf(url)
        }
        manager.syncNavigation(tab, view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        tab.loading = false
        tab.progress = 100
        tab.blockedCount = tab.blocked.get()
        manager.syncNavigation(tab, view)
        if (!url.isNullOrEmpty() && url != "about:blank") {
            manager.recordVisit(tab, url, view.title.orEmpty())
        }
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (!url.isNullOrEmpty() && url != "about:blank") {
            tab.url = url // keeps the address bar right for single-page apps
        }
        manager.syncNavigation(tab, view)
    }

    /** Block known trackers. Runs on WebView's IO thread, so it must stay allocation-light. */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null
        if (!manager.isBlockingActive(tab.host)) return null
        val host = request.url.host ?: return null
        if (manager.shouldBlock(host)) {
            tab.blocked.incrementAndGet()
            return WebResourceResponse(
                "text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)),
            )
        }
        return null
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val code = error.errorCode
        val failing = request.url.toString()
        val upgrade = tab.pendingUpgrade
        // HTTPS-first fallback: only for "cannot connect" style failures, never for certificate errors.
        if (upgrade != null && failing == upgrade.second &&
            (code == ERROR_CONNECT || code == ERROR_TIMEOUT)
        ) {
            tab.pendingUpgrade = null
            tab.httpHosts.add(UrlUtils.hostOf(upgrade.first))
            view.loadUrl(upgrade.first, manager.privacyHeaders(manager.settings()))
            return
        }
        tab.loading = false
        tab.error = TabError(ErrorKind.Network, failing, error.description?.toString().orEmpty())
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel() // never proceed past a certificate problem
        tab.loading = false
        tab.error = TabError(ErrorKind.Certificate, error.url.orEmpty(), error.primaryError.toString())
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        manager.onRenderGone(tab)
        return true // handled: do not crash the app
    }
}

internal class FChromeClient(
    private val tab: Tab,
    private val manager: TabManager,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        tab.progress = newProgress
        tab.blockedCount = tab.blocked.get()
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        tab.title = title.orEmpty()
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        tab.favicon = icon
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        manager.showCustomView(view, callback)
    }

    override fun onHideCustomView() {
        manager.hideCustomView()
    }

    // Camera, microphone and location are denied by default (privacy first).
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = manager.openFileChooser(filePathCallback, fileChooserParams)
}
