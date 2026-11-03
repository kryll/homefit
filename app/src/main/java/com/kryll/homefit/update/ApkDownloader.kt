package com.kryll.homefit.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.kryll.homefit.domain.ReleaseInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object ApkDownloader {
    suspend fun download(context: Context, release: ReleaseInfo, progress: (Int?) -> Unit): File = withContext(Dispatchers.IO) {
        val url = requireNotNull(release.apkUrl)
        require(UpdateSecurity.trustedApk(url, release.tag)) { "origen" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val temp = File(dir, "homefit.part")
        val apk = File(dir, "homefit.apk")
        temp.delete(); apk.delete()
        try {
            var current = url
            var c: HttpURLConnection? = null
            for (redirect in 0..5) {
                val connection = URL(current).openConnection() as HttpURLConnection
                c = connection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                    val next = URL(URL(current), connection.getHeaderField("Location") ?: error("redirección")).toString()
                    connection.disconnect()
                    require(UpdateSecurity.trustedRedirect(next)) { "redirección" }
                    current = next
                } else break
            }
            val connection = requireNotNull(c)
            try {
                require(connection.responseCode == 200) { "HTTP" }
                val size = connection.contentLengthLong
                require(size <= UpdateSecurity.MAX_APK_BYTES) { "tamaño" }
                var count = 0L
                var lastProgress: Int? = null
                connection.inputStream.use { input -> temp.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= UpdateSecurity.MAX_APK_BYTES) { "tamaño" }
                        out.write(buffer, 0, n)
                        val percent = if (size > 0) (100 * count / size).toInt().coerceIn(0, 100) else null
                        if (percent != lastProgress) { progress(percent); lastProgress = percent }
                    }
                } }
                require(count > 0 && (size <= 0 || count == size)) { "incompleto" }
            } finally { connection.disconnect() }
            verify(context, temp)
            check(temp.renameTo(apk)) { "archivo" }
            apk
        } catch (e: Throwable) { temp.delete(); apk.delete(); throw e }
    }

    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val candidate = requireNotNull(pm.getPackageArchiveInfo(apk.absolutePath, flags)) { "APK" }
        fun version(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
        fun signers(p: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
            return signatures.orEmpty().map { sig ->
                MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        require(UpdateSecurity.acceptsPackage(context.packageName, candidate.packageName, version(installed), version(candidate), signers(installed), signers(candidate))) { "paquete, versión o firma" }
    }
}
