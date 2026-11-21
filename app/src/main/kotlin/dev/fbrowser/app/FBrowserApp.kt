package dev.fbrowser.app

import android.app.Application
import dev.fbrowser.app.engine.BlockList

class FBrowserApp : Application() {
    val blockList = BlockList()

    override fun onCreate() {
        super.onCreate()
        // Parse the tracker list off the main thread so cold start is not delayed.
        Thread({ blockList.load(this) }, "fb-blocklist").apply {
            isDaemon = true
            start()
        }
    }
}
