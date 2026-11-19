/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

/**
 * Bound the allocation size of clipboard history previews. Full-resolution
 * source media remains untouched and is used when pasting.
 */
internal object ClipboardThumbnailSizing {
    const val MAX_DIMENSION_PX = 384

    /**
     * BitmapFactory uses power-of-two sampling on older Android decoders.
     * Keep the decoded longest edge at or below [MAX_DIMENSION_PX] while
     * avoiding integer overflow for unusual or malformed source dimensions.
     */
    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val longestEdge = maxOf(width, height)
        var sample = 1
        while (longestEdge / sample > MAX_DIMENSION_PX && sample <= Int.MAX_VALUE / 2) {
            sample *= 2
        }
        return sample
    }
}
