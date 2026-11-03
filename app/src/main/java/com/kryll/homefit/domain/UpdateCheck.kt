package com.kryll.homefit.domain

import org.json.JSONObject
import org.json.JSONArray

/** Una versión publicada de HomeFit. */
data class ReleaseInfo(val tag: String, val version: String, val apkUrl: String?, val pageUrl: String)

/** Estado de la comprobación de actualizaciones. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** No hay nada más nuevo. */
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data object Failed : UpdateState
}

object UpdateCheck {
    const val RELEASES_URL = "https://api.github.com/repos/kryll/homefit/releases?per_page=100"

    private val numericTag = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")

    /** Incluye versiones numéricas 0.x marcadas como prerelease; nunca borradores ni alfas/betas. */
    fun newestRelease(json: String): ReleaseInfo? {
        val releases = runCatching { JSONArray(json) }.getOrNull() ?: return null
        var newest: ReleaseInfo? = null
        for (i in 0 until releases.length()) {
            val item = releases.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false)) continue
            val release = parseRelease(item.toString()) ?: continue
            if (!numericTag.matches(release.tag)) continue
            if (newest == null || isNewer(release.version, newest.version)) newest = release
        }
        return newest
    }

    /** JSON de la API de releases de GitHub; null si no trae tag. */
    fun parseRelease(json: String): ReleaseInfo? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tag = o.optString("tag_name").ifBlank { return null }
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val url = assets.optJSONObject(i)?.optString("browser_download_url").orEmpty()
            if (url.endsWith(".apk")) { apk = url; break }
        }
        return ReleaseInfo(tag, tag.removePrefix("v"), apk, o.optString("html_url"))
    }

    /** true si [latest] ("1.2.0") es más nueva que [current] ("0.9.0-dev" cuenta como 0.9.0). */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
