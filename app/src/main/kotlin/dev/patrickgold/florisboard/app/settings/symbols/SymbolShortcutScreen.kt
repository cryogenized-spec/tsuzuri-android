/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefsAsState
import kotlinx.coroutines.launch

/**
 * Per-letter Unicode editor for everyday users. No JSON or developer syntax
 * required. Advanced users can enter U+1F43B or paste a multi-codepoint glyph.
 */
@Composable
fun SymbolShortcutScreen() = FlorisScreen {
    title = "Long-press symbols"
    previewFieldVisible = true

    val context = LocalContext.current
    val navController = LocalNavController.current
    val uriHandler = LocalUriHandler.current

    content {
        val shortcutScope = rememberCoroutineScope()
        LaunchedEffect(context) { SymbolShortcutStore.ensureLoaded(context) }
        val revision by SymbolShortcutStore.revision.collectAsState()
        var selectedKey by rememberSaveable { mutableStateOf("E") }
        var selectedCategory by remember { mutableStateOf(SymbolCategory.ACCENTS) }
        var customInput by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        val assigned = remember(revision, selectedKey) {
            SymbolShortcutStore.forKey(selectedKey)
        }
        val longPressDelay by prefs.keyboard.longPressDelay.collectPrefsAsState()

        OwnkeyGroup(title = "1. Choose a key") {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "Tap a letter. Its regular accents remain available; your choices are added to the long-press menu.",
            )
            for ((index, rowKeys) in "ABCDEFGHIJKLMNOPQRSTUVWXYZ".chunked(6).withIndex()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (key in rowKeys) {
                        val letter = key.toString()
                        val modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("unicode-key-$letter")
                        if (selectedKey == letter) {
                            Button(
                                onClick = { selectedKey = letter; message = "" },
                                modifier = modifier,
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(letter) }
                        } else {
                            OutlinedButton(
                                onClick = { selectedKey = letter; message = "" },
                                modifier = modifier,
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(letter) }
                        }
                    }
                    // Keep the last row aligned to the same six-column grid.
                    repeat(6 - rowKeys.length) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        OwnkeyGroup(title = "2. Symbols for $selectedKey") {
            val count = SymbolShortcutStore.MAX_SHORTCUTS_PER_KEY
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "Custom alternatives: ${assigned.size}/$count. Tap an existing symbol to remove it.",
            )
            if (assigned.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (value in assigned) {
                        OutlinedButton(
                            onClick = {
                                val key = selectedKey
                                shortcutScope.launch {
                                    message = if (SymbolShortcutStore.remove(context, key, value)) {
                                        "Removed $value from $key."
                                    } else {
                                        "This symbol is no longer assigned."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("unicode-remove-$value"),
                        ) { Text(value) }
                    }
                }
            }
            OwnkeyChoiceListRow(
                title = "Browse symbols",
                options = SymbolCategory.entries.map { ChoiceOption(it, it.title) },
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
            )
            for (rowSymbols in selectedCategory.symbols.chunked(5)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (symbol in rowSymbols) {
                        OutlinedButton(
                            onClick = {
                                val key = selectedKey
                                shortcutScope.launch {
                                    message = if (SymbolShortcutStore.add(context, key, symbol)) {
                                        "Added $symbol to $key."
                                    } else {
                                        "Already present, or the key has four custom alternatives."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(symbol) }
                    }
                    repeat(5 - rowSymbols.size) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("unicode-custom-input"),
                value = customInput,
                onValueChange = { customInput = it.take(32) },
                label = { Text("Paste a symbol or enter U+ code") },
                placeholder = { Text("e.g. ∑ or U+1F43B") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.padding(horizontal = 16.dp).testTag("unicode-save"),
                onClick = {
                    val key = selectedKey
                    val input = customInput
                    shortcutScope.launch {
                        message = if (SymbolShortcutStore.add(context, key, input)) {
                            customInput = ""
                            "Saved to $key."
                        } else {
                            "Not saved. Use a symbol or U+ hexadecimal value (up to four per key)."
                        }
                    }
                },
            ) { Text("Add symbol") }
            if (message.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = message,
                )
            }
        }

        OwnkeyGroup(title = "3. How it works") {
            OwnkeyRow(
                title = "Long-press delay",
                value = "$longPressDelay ms",
                summary = "Adjust when the character menu opens.",
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            OwnkeyRow(
                title = "Complete Unicode catalogue",
                summary = "Browse Unicode character charts; paste your selection above.",
                chevron = true,
                onClick = { uriHandler.openUri("https://www.unicode.org/charts/") },
            )
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "These are Unicode characters, not ASCII. Symbols are stored only on your device. Custom alternatives are disabled in password, email and URL fields.",
            )
        }
    }
}
