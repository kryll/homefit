package com.kryll.homefit.data

import com.kryll.homefit.domain.ReleaseInfo
import com.kryll.homefit.domain.UpdateCheck
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Consulta la última versión publicada, solo cuando el usuario lo pide (sin sondeo en segundo plano). */
object UpdateChecker {
    suspend fun latest(url: String = UpdateCheck.RELEASES_URL): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (c.responseCode != 200) return@runCatching null
                UpdateCheck.parseRelease(c.inputStream.bufferedReader().readText())
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
