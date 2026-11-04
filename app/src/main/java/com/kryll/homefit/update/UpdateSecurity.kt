package com.kryll.homefit.update

import java.net.URI

/** Origen fijo: ni enlaces arbitrarios de la API ni descargas a otro repositorio. */
object UpdateSecurity {
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    fun trustedApk(url: String, tag: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host == "github.com" && u.port == -1 && u.userInfo == null &&
            u.query == null && u.fragment == null &&
            Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(tag) &&
            u.rawPath.startsWith("/kryll/homefit/releases/download/$tag/") &&
            u.rawPath.removePrefix("/kryll/homefit/releases/download/$tag/").let {
                Regex("[A-Za-z0-9_.-]+\\.apk").matches(it) && !it.contains("..")
            }
    }.getOrDefault(false)

    fun trustedRedirect(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.port == -1 && u.userInfo == null &&
            u.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)

    fun acceptsPackage(installedName: String, candidateName: String?, installedVersion: Long, candidateVersion: Long,
                       installedSigners: Set<String>, candidateSigners: Set<String>): Boolean =
        installedName == candidateName && candidateVersion > installedVersion &&
            installedSigners.isNotEmpty() && installedSigners == candidateSigners
}
