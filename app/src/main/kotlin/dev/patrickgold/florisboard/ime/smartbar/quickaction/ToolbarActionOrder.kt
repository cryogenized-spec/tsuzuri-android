/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.smartbar.quickaction

/**
 * Toolbar customization operates on the same persisted arrangement used by
 * the keyboard's drag-and-drop action editor. There is no second toolbar state.
 */
internal fun QuickActionArrangement.moveToolbarAction(from: Int, to: Int): QuickActionArrangement {
    if (from !in dynamicActions.indices || to !in dynamicActions.indices || from == to) return this
    val reordered = dynamicActions.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(dynamicActions = reordered)
}

/** Remove an action from the visible/overflow toolbar without deleting it. */
internal fun QuickActionArrangement.hideToolbarAction(index: Int): QuickActionArrangement {
    if (index !in dynamicActions.indices) return this
    val action = dynamicActions[index]
    return copy(
        dynamicActions = dynamicActions.filterIndexed { i, _ -> i != index },
        hiddenActions = hiddenActions + action,
    ).distinct()
}

/** Restored actions join the end of the visible ordering and may be moved to the front. */
internal fun QuickActionArrangement.showToolbarAction(index: Int): QuickActionArrangement {
    if (index !in hiddenActions.indices) return this
    val action = hiddenActions[index]
    return copy(
        dynamicActions = dynamicActions + action,
        hiddenActions = hiddenActions.filterIndexed { i, _ -> i != index },
    ).distinct()
}

/** The pinned shortcut is a separate, persistent slot on the keyboard. */
internal fun QuickActionArrangement.pinToolbarAction(index: Int): QuickActionArrangement {
    if (index !in dynamicActions.indices) return this
    val newPinned = dynamicActions[index]
    val remaining = dynamicActions.filterIndexed { i, _ -> i != index }
    return copy(
        stickyAction = newPinned,
        dynamicActions = if (stickyAction == null) remaining else listOf(stickyAction) + remaining,
    ).distinct()
}

internal fun QuickActionArrangement.unpinToolbarAction(): QuickActionArrangement {
    val pinned = stickyAction ?: return this
    return copy(stickyAction = null, dynamicActions = listOf(pinned) + dynamicActions).distinct()
}
