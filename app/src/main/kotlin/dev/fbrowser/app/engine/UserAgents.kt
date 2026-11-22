package dev.fbrowser.app.engine

import android.os.Build

/**
 * Identity (User-Agent) presented to websites.
 *
 * FBROWSER is the default: the browser reports itself as fBrowser, so "what is my browser" pages
 * show fBrowser. The other modes exist for sites that only accept Chrome or Firefox.
 */
enum class UaMode(val id: String) {
    FBROWSER("fbrowser"),
    FIREFOX("firefox"),
    CHROME("chrome"),
    CHROME_DESKTOP("chrome_desktop");

    val isDesktop: Boolean get() = this == CHROME_DESKTOP

    companion object {
        fun fromId(id: String?): UaMode = entries.firstOrNull { it.id == id } ?: FBROWSER
    }
}

object UserAgents {
    /** Version of the bundled Gecko. Keep in sync with `geckoview` in gradle/libs.versions.toml. */
    const val GECKO_VERSION = "157.0"

    /** Chrome version reported in the Chrome modes. Chrome freezes the rest of the string. */
    const val CHROME_MAJOR = "140"

    fun forMode(mode: UaMode, appVersion: String): String {
        val android = Build.VERSION.RELEASE
        return when (mode) {
            UaMode.FBROWSER ->
                "Mozilla/5.0 (Android $android; Mobile; rv:$GECKO_VERSION) Gecko/$GECKO_VERSION fBrowser/$appVersion"
            UaMode.FIREFOX ->
                "Mozilla/5.0 (Android $android; Mobile; rv:$GECKO_VERSION) Gecko/$GECKO_VERSION Firefox/$GECKO_VERSION"
            UaMode.CHROME ->
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/$CHROME_MAJOR.0.0.0 Mobile Safari/537.36"
            UaMode.CHROME_DESKTOP ->
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/$CHROME_MAJOR.0.0.0 Safari/537.36"
        }
    }
}
