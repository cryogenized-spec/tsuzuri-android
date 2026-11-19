/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CursorDpadDirectionTest : FunSpec({
    test("all cursor pad buttons use the inherited navigation actions") {
        CursorDpadDirection.UP.keyCode shouldBe KeyCode.ARROW_UP
        CursorDpadDirection.DOWN.keyCode shouldBe KeyCode.ARROW_DOWN
        CursorDpadDirection.LEFT.keyCode shouldBe KeyCode.ARROW_LEFT
        CursorDpadDirection.RIGHT.keyCode shouldBe KeyCode.ARROW_RIGHT
    }
    test("each direction is unique and independently accessible") {
        CursorDpadDirection.entries.map { it.keyCode }.distinct().size shouldBe 4
        CursorDpadDirection.entries.map { it.label }.distinct().size shouldBe 4
    }
})
