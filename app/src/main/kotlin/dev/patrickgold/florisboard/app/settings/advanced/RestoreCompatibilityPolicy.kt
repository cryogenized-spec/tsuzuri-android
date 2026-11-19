/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.advanced

/**
 * Tsuzuri intentionally restarts versionCode at one.
 * Existing Ownkey / FlorisBoard backups use a different version sequence.
 */
internal object RestoreCompatibilityPolicy {
    private const val APP_ID = "io.github.cryogenizedspec.tsuzuri"
    private const val LEGACY_MIN_VERSION = 64
    private val trustedLegacyPackages = setOf(
        "nl.bartvandermeeren.ownkey",
        "dev.patrickgold.florisboard",
    )

    // Exact, explicit variant list; never accept arbitrary package-name prefixes.
    private val supportedVariants = setOf("", ".debug", ".beta", ".bench")

    fun isTsuzuriPackage(packageName: String): Boolean =
        supportedVariants.any { suffix -> packageName == APP_ID + suffix }

    fun accepts(packageName: String, versionCode: Int): Boolean {
        if (versionCode < 1) return false
        if (isTsuzuriPackage(packageName)) return true
        return versionCode >= LEGACY_MIN_VERSION && trustedLegacyPackages.any { name ->
            supportedVariants.any { suffix -> packageName == name + suffix }
        }
    }

    data class MediaReference(val archiveId: Long, val targetUri: String)

    /**
     * Reject foreign providers, path traversal, unknown types and nonnumeric IDs
     * before looking up any media file in an untrusted backup archive.
     */
    fun normalizeMediaReference(
        source: String?,
        archivePackage: String,
        archiveVersion: Int,
        kind: String,
        targetProviderAuthority: String,
    ): MediaReference? {
        if (!accepts(archivePackage, archiveVersion)) return null
        if (kind != "images" && kind != "videos") return null
        if (source.isNullOrBlank()) return null
        val uri = runCatching { java.net.URI(source) }.getOrNull() ?: return null
        if (uri.scheme != "content" ||
            uri.rawAuthority != archivePackage + ".provider.clipboard" ||
            uri.rawUserInfo != null ||
            uri.rawQuery != null ||
            uri.rawFragment != null
        ) return null
        val rawPath = uri.rawPath ?: return null
        val pieces = rawPath.split('/')
        if (pieces.size != 4 || pieces[0] != "" || pieces[1] != "clips" || pieces[2] != kind) return null
        val rawId = pieces[3]
        if (rawId.isEmpty() || rawId.any { it !in '0'..'9' }) return null
        val id = rawId.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return MediaReference(id, "content://" + targetProviderAuthority + "/clips/" + kind + "/" + id)
    }
}
