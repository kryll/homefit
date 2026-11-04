package com.kryll.homefit

import com.kryll.homefit.update.UpdateSecurity
import org.junit.Assert.*
import org.junit.Test

class UpdateSecurityTest {
    private val url = "https://github.com/kryll/homefit/releases/download/v1.2.0/homefit.apk"
    @Test fun acceptsOnlyExactRepositoryTagAndApk() {
        assertTrue(UpdateSecurity.trustedApk(url, "v1.2.0"))
        listOf(url.replace("https", "http"), url.replace("github.com", "github.com.evil.test"),
            url.replace("kryll", "other"), "$url?redirect=x", url.replace("homefit.apk", "../evil.apk"),
            url.replace("homefit.apk", "x%2fhomefit.apk"), url.replace("homefit.apk", "x.aab"),
            url.replace("github.com", "evil@github.com")).forEach { assertFalse(it, UpdateSecurity.trustedApk(it, "v1.2.0")) }
        assertFalse(UpdateSecurity.trustedApk(url, "v1.3.0"))
    }
    @Test fun rejectsUnrelatedRedirects() {
        assertTrue(UpdateSecurity.trustedRedirect("https://release-assets.githubusercontent.com/x?signature=example"))
        assertFalse(UpdateSecurity.trustedRedirect("https://evil.test/asset.apk"))
        assertFalse(UpdateSecurity.trustedRedirect("http://objects.githubusercontent.com/a"))
    }
    @Test fun requiresSamePackageStrictlyNewerCodeAndSameNonEmptySignerSet() {
        fun valid(name: String? = "com.kryll.homefit", code: Long = 2L, signers: Set<String> = setOf("a")) =
            UpdateSecurity.acceptsPackage("com.kryll.homefit", name, 1L, code, setOf("a"), signers)
        assertTrue(valid()); assertFalse(valid("other")); assertFalse(valid(code = 1)); assertFalse(valid(code = 0))
        assertFalse(valid(signers = emptySet())); assertFalse(valid(signers = setOf("b")))
        assertFalse(valid(signers = setOf("a", "b")))
        assertFalse(UpdateSecurity.acceptsPackage("a", "a", 1, 2, emptySet(), emptySet()))
    }
}
