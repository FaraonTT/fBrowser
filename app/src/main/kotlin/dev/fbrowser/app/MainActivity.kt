package dev.fbrowser.app

import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private var pendingFileResult: ((List<Uri>) -> Unit)? = null

    private val singleFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingFileResult?.invoke(uri?.let(::listOf) ?: emptyList())
            pendingFileResult = null
        }

    private val multipleFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            pendingFileResult?.invoke(uris)
            pendingFileResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        vm.manager.attach { multiple, onResult ->
            pendingFileResult?.invoke(emptyList())
            pendingFileResult = onResult
            try {
                if (multiple) multipleFilePicker.launch(arrayOf("*/*"))
                else singleFilePicker.launch(arrayOf("*/*"))
            } catch (_: Exception) {
                pendingFileResult = null
                onResult(emptyList())
            }
        }

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

    /** Accept only normal web links opened by other apps. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW &&
            (uri.scheme == "http" || uri.scheme == "https")
        ) {
            vm.openExternal(uri.toString())
        }
    }

    override fun onStop() {
        vm.onStop()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            vm.manager.trimMemory()
        }
    }

    override fun onDestroy() {
        vm.manager.detach()
        super.onDestroy()
    }
}
