package dev.fbrowser.app.engine

import android.net.Uri
import dev.fbrowser.app.data.SearchEngine

object UrlUtils {
    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "gbraid", "wbraid", "dclid", "msclkid", "mc_eid", "mc_cid", "igshid",
        "yclid", "_hsenc", "_hsmi", "twclid", "ttclid", "li_fat_id", "oly_anon_id", "oly_enc_id",
        "vero_id", "ref_src", "_openstat", "ysclid",
    )

    // host, localhost, IPv4 or [IPv6], optional port, optional path. \p{L} keeps IDN such as яндекс.рф working.
    private val HOST_LIKE = Regex(
        """^(localhost|(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+\p{L}{2,}|\d{1,3}(?:\.\d{1,3}){3}|\[[0-9a-f:]+])(?::\d{1,5})?(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val PRIVATE_IP = Regex("""^(10\.|127\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.).*""")

    fun isTrackingParam(name: String): Boolean =
        name.startsWith("utm_", ignoreCase = true) || name.lowercase() in TRACKING_PARAMS

    /** Removes well-known tracking query parameters. Returns the same string if nothing to strip. */
    fun stripTracking(url: String): String {
        val uri = Uri.parse(url)
        if (!uri.isHierarchical) return url
        val names = uri.queryParameterNames
        if (names.none(::isTrackingParam)) return url
        val builder = uri.buildUpon().clearQuery()
        for (name in names) {
            if (isTrackingParam(name)) continue
            for (value in uri.getQueryParameters(name)) builder.appendQueryParameter(name, value)
        }
        return builder.build().toString()
    }

    /**
     * Turns whatever the user typed into a loadable URL. Only http(s) is ever produced from a
     * typed scheme: javascript:, data:, file:, intent: etc. are treated as search text.
     */
    fun resolve(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return engine.home
        val hasSpace = text.any { it.isWhitespace() }
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return if (hasSpace) engine.searchUrl(text) else text
        }
        if (!hasSpace && HOST_LIKE.matches(text)) {
            return (if (isLocalHost(text)) "http://" else "https://") + text
        }
        return engine.searchUrl(text)
    }

    fun hostOf(url: String): String = try {
        Uri.parse(if (url.contains("://")) url else "http://$url").host.orEmpty().lowercase()
    } catch (_: Exception) {
        ""
    }

    /** Key used for per-site settings: host without a leading www. */
    fun siteKey(host: String): String = host.removePrefix("www.")

    fun displayHost(url: String): String = siteKey(hostOf(url)).ifEmpty { url }

    fun isLocalHost(url: String): Boolean {
        val host = hostOf(url)
        return host == "localhost" || host.endsWith(".local") || host.endsWith(".localhost") ||
            PRIVATE_IP.matches(host)
    }
}
