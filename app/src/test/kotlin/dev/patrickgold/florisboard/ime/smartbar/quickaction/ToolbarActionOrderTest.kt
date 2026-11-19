/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.smartbar.quickaction

import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ToolbarActionOrderTest : FunSpec({
    val copy = QuickAction.InsertKey(TextKeyData.CLIPBOARD_COPY)
    val paste = QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE)
    val undo = QuickAction.InsertKey(TextKeyData.UNDO)
    val settings = QuickAction.InsertKey(TextKeyData.SETTINGS)
    val original = QuickActionArrangement(
        stickyAction = settings,
        dynamicActions = listOf(copy, paste, undo),
        hiddenActions = emptyList(),
    )

    test("reordering follows the toolbar's left-to-right order without touching the pinned action") {
        val changed = original.moveToolbarAction(2, 0)
        changed.dynamicActions shouldBe listOf(undo, copy, paste)
        changed.stickyAction shouldBe settings
        changed.hiddenActions shouldBe emptyList()
        changed.moveToolbarAction(0, 2).dynamicActions shouldBe original.dynamicActions
    }

    test("out-of-range and no-op moves preserve the arrangement") {
        original.moveToolbarAction(-1, 1) shouldBe original
        original.moveToolbarAction(0, 9) shouldBe original
        original.moveToolbarAction(1, 1) shouldBe original
    }

    test("hide and restore keep shortcuts available without losing their identity") {
        val hidden = original.hideToolbarAction(1)
        hidden.dynamicActions shouldBe listOf(copy, undo)
        hidden.hiddenActions shouldBe listOf(paste)
        val restored = hidden.showToolbarAction(0)
        restored.dynamicActions shouldBe listOf(copy, undo, paste)
        restored.hiddenActions shouldBe emptyList()
    }

    test("pinning one action demotes the old pinned action, and unpinning preserves all tools") {
        val pinned = original.pinToolbarAction(1)
        pinned.stickyAction shouldBe paste
        pinned.dynamicActions shouldBe listOf(settings, copy, undo)
        val unpinned = pinned.unpinToolbarAction()
        unpinned.stickyAction shouldBe null
        unpinned.dynamicActions shouldBe listOf(paste, settings, copy, undo)
        unpinned.distinct() shouldBe unpinned
    }

    test("empty and invalid hidden indexes are safe") {
        original.hideToolbarAction(100) shouldBe original
        original.showToolbarAction(0) shouldBe original
        original.copy(stickyAction = null).unpinToolbarAction() shouldBe original.copy(stickyAction = null)
    }
    test("queued moves resolve the original action against each latest arrangement") {
        // Simulates two queued clicks on the same Compose arrow before recomposition.
        val actionFromOldFrame = copy
        val first = original.moveToolbarAction(actionFromOldFrame, 1)
        val second = first.moveToolbarAction(actionFromOldFrame, 1)
        first.dynamicActions shouldBe listOf(paste, copy, undo)
        second.dynamicActions shouldBe listOf(paste, undo, copy)
        // The old index based implementation would have returned to the original order.
        second.moveToolbarAction(actionFromOldFrame, 1) shouldBe second
    }

    test("queued hide and pin never target a different shortcut after a prior change") {
        val first = original.hideToolbarAction(copy)
        first.hideToolbarAction(copy) shouldBe first
        first.pinToolbarAction(paste).stickyAction shouldBe paste
        first.hideToolbarAction(undo).dynamicActions shouldBe listOf(paste)
        // A stale UI tap on an already-hidden button is safe.
        first.pinToolbarAction(copy) shouldBe first
    }

    test("restore, pin and unpin use identity even when the list has shifted") {
        val hidden = original.hideToolbarAction(paste)
        hidden.showToolbarAction(paste).dynamicActions shouldBe listOf(copy, undo, paste)
        hidden.showToolbarAction(paste).showToolbarAction(paste) shouldBe hidden.showToolbarAction(paste)
        val changed = original.pinToolbarAction(undo)
        changed.unpinToolbarAction(settings) shouldBe changed
        changed.unpinToolbarAction(undo).stickyAction shouldBe null
    }

})
