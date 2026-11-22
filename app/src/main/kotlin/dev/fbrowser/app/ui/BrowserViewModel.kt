package dev.fbrowser.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fbrowser.app.FBrowserApp
import dev.fbrowser.app.data.BrowserSettings
import dev.fbrowser.app.data.Library
import dev.fbrowser.app.data.SettingsRepository
import dev.fbrowser.app.engine.TabManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Survives configuration changes; owns settings, library and the tab/GeckoView manager. */
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val _settings = MutableStateFlow(BrowserSettings())
    val settings: StateFlow<BrowserSettings> = _settings.asStateFlow()

    val library = Library(app, viewModelScope)
    val manager = TabManager(app as FBrowserApp, { _settings.value }, library)

    /** False until settings and library are loaded, so no Gecko session is created with stale defaults. */
    var ready by mutableStateOf(false)
        private set

    private var pendingUrl: String? = null

    init {
        viewModelScope.launch {
            _settings.value = repo.flow.first()
            library.awaitLoaded()
            if (_settings.value.clearOnExit) manager.clearWebData()
            if (!manager.restoreSession()) manager.newTab()
            pendingUrl?.let { manager.openUrl(it) }
            pendingUrl = null
            ready = true
            // From now on, push every settings change into live Gecko sessions.
            repo.flow.drop(1).collect {
                _settings.value = it
                manager.applySettingsToAll()
            }
        }
    }

    fun update(transform: (BrowserSettings) -> BrowserSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun openExternal(url: String) {
        if (ready) manager.openUrl(url) else pendingUrl = url
    }

    fun clearBrowsingData() {
        library.clearHistory()
        manager.clearWebData()
    }

    fun onStop() {
        manager.saveSession()
        library.flush()
    }

    override fun onCleared() {
        if (_settings.value.clearOnExit) manager.clearWebData()
        manager.destroy()
    }
}
