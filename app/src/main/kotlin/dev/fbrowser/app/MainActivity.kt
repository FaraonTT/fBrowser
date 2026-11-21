package dev.fbrowser.app

import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.fbrowser.app.ui.BrowserRoot
import dev.fbrowser.app.ui.BrowserViewModel
import dev.fbrowser.app.ui.theme.FBrowserTheme

class MainActivity : ComponentActivity() {

    private val vm: BrowserViewModel by viewModels()

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
            fileCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.manager.attach(this, ::launchFileChooser)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FBrowserTheme {
                BrowserRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Links opened from other apps (fBrowser can be the default browser). Only http(s) is accepted. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && (uri.scheme == "http" || uri.scheme == "https")) {
            vm.openExternal(uri.toString())
        }
    }

    private fun launchFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        fileCallback?.onReceiveValue(null) // a previous request is still pending: cancel it
        fileCallback = callback
        return try {
            fileLauncher.launch(params.createIntent())
            true
        } catch (_: Exception) {
            fileCallback = null
            callback.onReceiveValue(null)
            false
        }
    }

    override fun onResume() {
        super.onResume()
        vm.manager.onHostResume()
    }

    override fun onPause() {
        vm.manager.onHostPause()
        super.onPause()
    }

    override fun onStop() {
        vm.onStop() // persist session + history
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) vm.manager.trimMemory()
    }

    override fun onDestroy() {
        vm.manager.detach(this)
        super.onDestroy()
    }
}
