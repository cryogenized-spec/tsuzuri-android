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
    test("released beta builds and benchmark builds are recognised without prefix matching") {
        for (suffix in listOf("", ".debug", ".beta", ".bench")) {
            RestoreCompatibilityPolicy.accepts("io.github.cryogenizedspec.tsuzuri" + suffix, 1) shouldBe true
            RestoreCompatibilityPolicy.accepts("nl.bartvandermeeren.ownkey" + suffix, 127) shouldBe true
        }
        RestoreCompatibilityPolicy.accepts("nl.bartvandermeeren.ownkey.beta", 63) shouldBe false
        RestoreCompatibilityPolicy.accepts("nl.bartvandermeeren.ownkey.beta.evil", 127) shouldBe false
    }

    test("a validated Ownkey media URI is mapped to Tsuzuri's current provider") {
        val ref = RestoreCompatibilityPolicy.normalizeMediaReference(
            "content://nl.bartvandermeeren.ownkey.beta.provider.clipboard/clips/images/42",
            "nl.bartvandermeeren.ownkey.beta", 127, "images",
            "io.github.cryogenizedspec.tsuzuri.provider.clipboard",
        )
        ref shouldBe RestoreCompatibilityPolicy.MediaReference(
            42L, "content://io.github.cryogenizedspec.tsuzuri.provider.clipboard/clips/images/42",
        )
    }

    test("media restore rejects hostile authorities, paths, IDs and query strings") {
        val pkg = "nl.bartvandermeeren.ownkey"
        val base = "content://" + pkg + ".provider.clipboard/clips/"
        val invalid = listOf(
            "content://other.app/clips/images/42",
            base + "images/../42",
            base + "images/0",
            base + "images/not-a-number",
            base + "images/42?redirect=x",
            base + "images/42#secret",
            base + "images/%34%32",
            base + "videos/42",
            "https://" + pkg + ".provider.clipboard/clips/images/42",
        )
        for (uri in invalid) {
            RestoreCompatibilityPolicy.normalizeMediaReference(
                uri, pkg, 127, "images", "io.github.cryogenizedspec.tsuzuri.provider.clipboard",
            ) shouldBe null
        }
        RestoreCompatibilityPolicy.normalizeMediaReference(
            base + "images/42", "untrusted.app", 127, "images", "io.github.cryogenizedspec.tsuzuri.provider.clipboard",
        ) shouldBe null
    }

    test("unknown app IDs and misleading prefixes are rejected") {
        RestoreCompatibilityPolicy.accepts("unrelated.keyboard", 999) shouldBe false
        RestoreCompatibilityPolicy.accepts("io.github.cryogenizedspec.tsuzuri.evil", 1) shouldBe false
        RestoreCompatibilityPolicy.accepts("", 127) shouldBe false
    }
})
