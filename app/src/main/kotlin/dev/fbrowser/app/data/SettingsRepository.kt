package dev.fbrowser.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Search engines. Google is the default, as requested. */
enum class SearchEngine(
    val id: String,
    val label: String,
    private val template: String,
    val home: String,
) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com/"),
    DUCKDUCKGO("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s", "https://www.startpage.com/");

    fun searchUrl(query: String): String = template.replace("%s", Uri.encode(query))

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

/** Immutable snapshot of every user setting. Defaults are the privacy-protective ones. */
data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val blockTrackers: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val httpsFirst: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val sendPrivacySignals: Boolean = true,
    val safeBrowsing: Boolean = true,
    val javascript: Boolean = true,
    val darkWebPages: Boolean = true,
    val hideBarsOnScroll: Boolean = true,
    val clearOnExit: Boolean = false,
    /** Hosts (without www.) where tracker blocking is switched off by the user. */
    val allowlist: Set<String> = emptySet(),
)

private val Context.settingsStore by preferencesDataStore(name = "fbrowser_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val engine = stringPreferencesKey("engine")
        val blockTrackers = booleanPreferencesKey("block_trackers")
        val blockCookies = booleanPreferencesKey("block_3p_cookies")
        val httpsFirst = booleanPreferencesKey("https_first")
        val stripParams = booleanPreferencesKey("strip_params")
        val signals = booleanPreferencesKey("privacy_signals")
        val safeBrowsing = booleanPreferencesKey("safe_browsing")
        val javascript = booleanPreferencesKey("javascript")
        val darkWeb = booleanPreferencesKey("dark_web")
        val hideBars = booleanPreferencesKey("hide_bars")
        val clearOnExit = booleanPreferencesKey("clear_on_exit")
        val allowlist = stringSetPreferencesKey("allowlist")
    }

    val flow: Flow<BrowserSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Atomic read-modify-write. */
    suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): BrowserSettings {
        val d = BrowserSettings()
        return BrowserSettings(
            searchEngine = SearchEngine.fromId(this[Keys.engine]),
            blockTrackers = this[Keys.blockTrackers] ?: d.blockTrackers,
            blockThirdPartyCookies = this[Keys.blockCookies] ?: d.blockThirdPartyCookies,
            httpsFirst = this[Keys.httpsFirst] ?: d.httpsFirst,
            stripTrackingParams = this[Keys.stripParams] ?: d.stripTrackingParams,
            sendPrivacySignals = this[Keys.signals] ?: d.sendPrivacySignals,
            safeBrowsing = this[Keys.safeBrowsing] ?: d.safeBrowsing,
            javascript = this[Keys.javascript] ?: d.javascript,
            darkWebPages = this[Keys.darkWeb] ?: d.darkWebPages,
            hideBarsOnScroll = this[Keys.hideBars] ?: d.hideBarsOnScroll,
            clearOnExit = this[Keys.clearOnExit] ?: d.clearOnExit,
            allowlist = this[Keys.allowlist] ?: d.allowlist,
        )
    }

    private fun MutablePreferences.write(s: BrowserSettings) {
        this[Keys.engine] = s.searchEngine.id
        this[Keys.blockTrackers] = s.blockTrackers
        this[Keys.blockCookies] = s.blockThirdPartyCookies
        this[Keys.httpsFirst] = s.httpsFirst
        this[Keys.stripParams] = s.stripTrackingParams
        this[Keys.signals] = s.sendPrivacySignals
        this[Keys.safeBrowsing] = s.safeBrowsing
        this[Keys.javascript] = s.javascript
        this[Keys.darkWeb] = s.darkWebPages
        this[Keys.hideBars] = s.hideBarsOnScroll
        this[Keys.clearOnExit] = s.clearOnExit
        this[Keys.allowlist] = s.allowlist
    }
}
