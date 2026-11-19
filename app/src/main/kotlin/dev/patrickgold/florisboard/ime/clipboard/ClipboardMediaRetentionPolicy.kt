/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

/** A deleted history row is not the sole owner of shared clipboard media. */
internal object ClipboardMediaRetentionPolicy {
    fun mayDispose(
        removedUri: String?,
        survivingUris: Collection<String>,
        activePrimaryUri: String?,
    ): Boolean = removedUri != null &&
        removedUri !in survivingUris &&
        removedUri != activePrimaryUri
}
