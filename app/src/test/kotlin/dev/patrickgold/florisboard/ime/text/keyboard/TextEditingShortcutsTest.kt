/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TextEditingShortcutsTest : FunSpec({
    fun action(key: String, variation: KeyVariation = KeyVariation.NORMAL,
               rich: Boolean = true, enabled: Boolean = true,
               hasSelection: Boolean = true): Int? =
        TextEditingShortcuts.dataFor(key, variation, rich, enabled, hasSelection)?.code

    test("Windows-style long-press keys invoke existing editing actions") {
        action("a") shouldBe KeyCode.CLIPBOARD_SELECT_ALL
        action("x") shouldBe KeyCode.CLIPBOARD_CUT
        action("c") shouldBe KeyCode.CLIPBOARD_COPY
        action("v") shouldBe KeyCode.CLIPBOARD_PASTE
        action("q") shouldBe null
    }
    test("shortcuts are not injected into password, email or URL fields") {
        for (variation in listOf(KeyVariation.PASSWORD, KeyVariation.EMAIL_ADDRESS, KeyVariation.URI)) {
            action("c", variation) shouldBe null
            action("v", variation) shouldBe null
        }
    }
    test("shortcuts can be disabled and require a text editor") {
        action("a", enabled = false) shouldBe null
        action("x", rich = false) shouldBe null
    }
})
