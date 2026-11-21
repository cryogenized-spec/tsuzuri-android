/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class TsuzuriKeyHintDefaultsTest : FunSpec({
    test("fresh keyboard installs show symbol legends with symbol priority") {
        val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val prefs by store
        store.init(LoadStrategy.Disabled, PersistStrategy.Disabled).getOrThrow()
        prefs.keyboard.hintedSymbolsEnabled.get() shouldBe true
        prefs.keyboard.hintedSymbolsMode.get() shouldBe KeyHintMode.HINT_PRIORITY
    }

    test("existing explicit preference to hide keycap legends remains respected") {
        val dir = Files.createTempDirectory("tsuzuri-symbol-hints-")
        val file = dir.resolve("preferences.jetpref")
        try {
            file.toFile().writeText("b;keyboard__hinted_symbols_enabled;false\r\n")
            val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val prefs by store
            store.init(LoadStrategy.UseReader(FileBasedStorage(file.toString())), PersistStrategy.Disabled).getOrThrow()
            prefs.keyboard.hintedSymbolsEnabled.get() shouldBe false
        } finally {
            Files.deleteIfExists(file.resolveSibling("preferences.jetpref.tmp"))
            Files.deleteIfExists(file)
            Files.deleteIfExists(dir)
        }
    }
})
