package com.kryll.homefit

import android.app.Application
import android.content.Context
import androidx.core.content.FileProvider
import com.kryll.homefit.update.ApkDownloader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = HomeFitApp::class)
class ApkValidationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Test fun refusesUnreadableApkAndDoesNotOpenInstaller() {
        val f = File(context.cacheDir, "updates/invalid.apk").apply { parentFile!!.mkdirs(); writeText("not an apk") }
        assertTrue(runCatching { ApkDownloader.verify(context, f) }.isFailure)
    }
    @Test fun providerSharesOnlyUpdateCacheNotDatabaseOrOtherFiles() {
        val apk = File(context.cacheDir, "updates/homefit.apk").apply { parentFile!!.mkdirs(); writeText("fixture") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        assertEquals("content", uri.scheme)
        assertTrue(uri.path!!.contains("homefit_updates"))
        val unrelated = File(context.filesDir, "private.json").apply { writeText("fixture") }
        assertTrue(runCatching { FileProvider.getUriForFile(context, "${context.packageName}.updates", unrelated) }.isFailure)
    }
}
