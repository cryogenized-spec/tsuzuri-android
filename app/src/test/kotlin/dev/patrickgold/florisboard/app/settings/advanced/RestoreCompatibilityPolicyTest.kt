/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.advanced

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RestoreCompatibilityPolicyTest : FunSpec({
    test("a Tsuzuri 0.1 backup can be restored regardless of old version floor") {
        RestoreCompatibilityPolicy.accepts("io.github.cryogenizedspec.tsuzuri", 1) shouldBe true
        RestoreCompatibilityPolicy.accepts("io.github.cryogenizedspec.tsuzuri.debug", 1) shouldBe true
        RestoreCompatibilityPolicy.accepts("io.github.cryogenizedspec.tsuzuri", 0) shouldBe false
    }
    test("Ownkey and Floris backups require historic minimum version") {
        RestoreCompatibilityPolicy.accepts("nl.bartvandermeeren.ownkey", 127) shouldBe true
        RestoreCompatibilityPolicy.accepts("dev.patrickgold.florisboard", 64) shouldBe true
        RestoreCompatibilityPolicy.accepts("nl.bartvandermeeren.ownkey", 63) shouldBe false
        RestoreCompatibilityPolicy.accepts("nl.bartvandermeeren.ownkey.debug", 127) shouldBe true
    }
    test("unknown app IDs and misleading prefixes are rejected") {
        RestoreCompatibilityPolicy.accepts("unrelated.keyboard", 999) shouldBe false
        RestoreCompatibilityPolicy.accepts("io.github.cryogenizedspec.tsuzuri.evil", 1) shouldBe false
        RestoreCompatibilityPolicy.accepts("", 127) shouldBe false
    }
})
