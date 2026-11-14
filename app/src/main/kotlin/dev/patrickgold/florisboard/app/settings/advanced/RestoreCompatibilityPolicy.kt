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

    fun isTsuzuriPackage(packageName: String): Boolean =
        packageName == APP_ID || packageName == APP_ID + ".debug"

    fun accepts(packageName: String, versionCode: Int): Boolean {
        if (versionCode < 1) return false
        if (isTsuzuriPackage(packageName)) return true
        return versionCode >= LEGACY_MIN_VERSION && trustedLegacyPackages.any { name ->
            packageName == name || packageName == name + ".debug"
        }
    }
}
