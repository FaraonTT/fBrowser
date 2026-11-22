package dev.fbrowser.app

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class FBrowserApp : Application() {
    private var runtimeInstance: GeckoRuntime? = null

    val runtimeReady: Boolean get() = runtimeInstance != null

    /**
     * The one Gecko runtime of the process, created on first use (main thread). The start page and
     * the tab list need no engine, so cold start does not pay for Gecko until a page is opened.
     */
    val runtime: GeckoRuntime
        get() = runtimeInstance ?: GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false)
                .aboutConfigEnabled(false)
                .build(),
        ).also { runtimeInstance = it }
}
