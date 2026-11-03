package com.kryll.homefit

import com.kryll.homefit.domain.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val sample = """
        {"tag_name":"v1.2.0","html_url":"https://github.com/kryll/homefit/releases/tag/v1.2.0",
         "assets":[{"browser_download_url":"https://github.com/kryll/homefit/releases/download/v1.2.0/homefit.apk"},
                   {"browser_download_url":"https://github.com/kryll/homefit/releases/download/v1.2.0/homefit.aab"}]}
    """.trimIndent()

    @Test fun parsesTagVersionApkAndPage() {
        val r = UpdateCheck.parseRelease(sample)!!
        assertEquals("v1.2.0", r.tag)
        assertEquals("1.2.0", r.version)
        assertEquals("https://github.com/kryll/homefit/releases/download/v1.2.0/homefit.apk", r.apkUrl)
        assertTrue(r.pageUrl.endsWith("/v1.2.0"))
    }

    @Test fun toleratesMissingAssetsAndBadJson() {
        val r = UpdateCheck.parseRelease("""{"tag_name":"v0.1.0","html_url":"u"}""")!!
        assertNull(r.apkUrl)
        assertNull(UpdateCheck.parseRelease("no json"))
        assertNull(UpdateCheck.parseRelease("""{"name":"sin tag"}"""))
    }

    @Test fun comparesVersions() {
        assertTrue(UpdateCheck.isNewer("1.0.0", "0.9.9"))
        assertTrue(UpdateCheck.isNewer("1.0.1", "1.0.0-dev"))
        assertTrue(UpdateCheck.isNewer("v2.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0.1"))
        assertFalse(UpdateCheck.isNewer("0.9.0", "1.0.0"))
    }
}
