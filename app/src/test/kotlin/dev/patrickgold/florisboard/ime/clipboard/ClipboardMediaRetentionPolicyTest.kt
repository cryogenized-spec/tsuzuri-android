/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardMediaRetentionPolicyTest : FunSpec({
    val shared = "content://io.github.cryogenizedspec.tsuzuri.provider.clipboard/clips/images/42"
    val other = "content://io.github.cryogenizedspec.tsuzuri.provider.clipboard/clips/images/43"

    test("expiry of duplicate media row does not remove a pinned survivor file") {
        ClipboardMediaRetentionPolicy.mayDispose(shared, listOf(shared), null) shouldBe false
    }
    test("explicitly deleting final row can release unreferenced media") {
        ClipboardMediaRetentionPolicy.mayDispose(shared, listOf(other), null) shouldBe true
    }
    test("active clipboard URI remains usable after history clear") {
        ClipboardMediaRetentionPolicy.mayDispose(shared, emptyList(), shared) shouldBe false
    }
    test("null media reference is never disposed") {
        ClipboardMediaRetentionPolicy.mayDispose(null, emptyList(), null) shouldBe false
    }
})
