/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.popup

import kotlin.math.floor

/**
 * Maps a pointer to the actual rendered popup grid, not the un-clamped key anchor.
 *
 * Row 1 (if present) is above row 0. When anchored right, a shorter upper row
 * is right-aligned. A little outer tolerance lets a finger reach off the key
 * before snapping to the closest candidate, as with the pre-existing controller.
 */
internal fun popupHitIndex(
    pointerX: Float,
    pointerY: Float,
    boundsLeft: Float,
    boundsTop: Float,
    cellWidth: Float,
    cellHeight: Float,
    row0count: Int,
    row1count: Int,
    rightAligned: Boolean,
    keyBottom: Float,
): Int? {
    if (cellWidth <= 0f || cellHeight <= 0f || row0count <= 0 ||
        row1count < 0 || row1count > row0count
    ) return null

    val right = boundsLeft + row0count * cellWidth
    if (pointerX < boundsLeft - cellWidth || pointerX > right + cellWidth ||
        pointerY < boundsTop - cellHeight || pointerY > keyBottom + cellHeight
    ) return null

    val upper = row1count > 0 && pointerY < boundsTop + cellHeight
    val columns = if (upper) row1count else row0count
    // PopupExtBox renders a shorter top row aligned to the right of the full-width bottom row.
    val rowInset = if (upper && rightAligned) row0count - row1count else 0
    val physicalColumn = floor((pointerX - boundsLeft) / cellWidth).toInt()
    val column = (physicalColumn - rowInset).coerceIn(0, columns - 1)
    return (if (upper) 0 else row1count) + column
}
