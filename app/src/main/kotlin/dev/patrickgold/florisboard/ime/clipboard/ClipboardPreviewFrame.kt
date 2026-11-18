/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

/**
 * Carries the origin of an asynchronously decoded preview so a recycled
 * clipboard tile can never display content belonging to another clip.
 */
internal data class ClipboardPreviewFrame<T>(
    val sourceUri: String,
    val image: T,
) {
    fun visibleFor(currentUri: String?): T? =
        image.takeIf { currentUri == sourceUri }
}
