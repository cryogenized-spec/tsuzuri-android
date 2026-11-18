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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteClient
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteSecretsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val popupOptions = remember(revision) { SymbolShortcutStore.options() }
        val settingsPrefs = prefs
        var aiInstruction by remember { mutableStateOf("") }
        var aiProposal by remember { mutableStateOf<SymbolAssignmentProposal?>(null) }
        var aiBaseline by remember { mutableStateOf<Map<String, List<String>>?>(null) }
        var aiBusy by remember { mutableStateOf(false) }
        var aiMessage by remember { mutableStateOf("") }

        OwnkeyGroup(title = "1. Choose a key") {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "Assign up to six custom Unicode alternatives per letter. Keep the original Ownkey symbols and choose whether inherited accents appear or come first.",
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

        OwnkeyGroup(title = "2. Long-press menu options") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Show inherited accents", modifier = Modifier.weight(1f))
                Switch(
                    checked = popupOptions.showAccents,
                    onCheckedChange = { enabled ->
                        shortcutScope.launch {
                            SymbolShortcutStore.setOptions(
                                context, popupOptions.copy(showAccents = enabled),
                            )
                        }
                    },
                )
            }
            OwnkeyChoiceListRow(
                title = "First to reach",
                options = listOf(
                    ChoiceOption(SymbolPopupPriority.ORIGINAL, "Original Ownkey order"),
                    ChoiceOption(SymbolPopupPriority.ACCENTS_FIRST, "Accented letters first"),
                    ChoiceOption(SymbolPopupPriority.CUSTOM_FIRST, "My symbols first"),
                ),
                selected = popupOptions.priority,
                onSelect = { chosen ->
                    shortcutScope.launch {
                        SymbolShortcutStore.setOptions(context, popupOptions.copy(priority = chosen))
                    }
                },
            )
            Text(
                "Existing symbols are retained. Hiding accents only hides inherited accents; " +
                    "an accented character you assign manually remains available.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        OwnkeyGroup(title = "3. Symbols for $selectedKey") {
            val count = SymbolShortcutStore.MAX_SHORTCUTS_PER_KEY
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "Custom alternatives: ${assigned.size}/$count. Tap an existing symbol to remove it.",
            )
            if (assigned.isNotEmpty()) {
                for (group in assigned.withIndex().toList().chunked(3)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((slot, value) in group) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = {
                                        val key = selectedKey
                                        shortcutScope.launch {
                                            message = if (SymbolShortcutStore.remove(context, key, value)) {
                                                "Removed $value from $key."
                                            } else "This symbol is no longer assigned."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("unicode-remove-$value"),
                                ) { Text("${slot + 1}: $value ×") }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val key = selectedKey
                                            shortcutScope.launch { SymbolShortcutStore.move(context, key, value, -1) }
                                        },
                                        enabled = slot > 0,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp),
                                    ) { Text("←") }
                                    OutlinedButton(
                                        onClick = {
                                            val key = selectedKey
                                            shortcutScope.launch { SymbolShortcutStore.move(context, key, value, 1) }
                                        },
                                        enabled = slot < assigned.lastIndex,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp),
                                    ) { Text("→") }
                                }
                            }
                        }
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
                                        "Already present, or the key has six custom alternatives."
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
                placeholder = { Text("e.g. ∑, U+1F43B, or U+2764 U+FE0F") },
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
                            "Not saved. Use a symbol, U+ code or sequence (up to six per key)."
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

        OwnkeyGroup(title = "4. AI symbol assistant (optional)") {
            Text(
                "The assistant can suggest symbols or propose multiple key assignments. " +
                    "No changes are made without your approval, and each AI request uses your own API key.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = aiInstruction,
                onValueChange = { aiInstruction = it.take(800) },
                label = { Text("Ask for symbols or key assignments") },
                placeholder = {
                    Text("Give Q ∑ and §, put ☠️ on X, and suggest mathematical symbols.")
                },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .testTag("symbols-ai-instruction"),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = !aiBusy && aiInstruction.isNotBlank(),
                    onClick = {
                        val current = ('A'..'Z').associate { letter ->
                            letter.toString() to SymbolShortcutStore.forKey(letter.toString())
                        }
                        val userRequest = aiInstruction
                        aiBusy = true
                        aiProposal = null
                        aiBaseline = null
                        aiMessage = ""
                        shortcutScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val secrets = LlmRewriteSecretsStore(context)
                                    val client = LlmRewriteClient(
                                        apiKeyProvider = { "" },
                                        apiKeyForEndpointProvider = { endpoint -> secrets.getApiKeyForEndpoint(endpoint) },
                                        endpointUrlProvider = {
                                            settingsPrefs.voxtral.postProcessingEndpointUrl.get()
                                        },
                                        modelProvider = {
                                            settingsPrefs.voxtral.postProcessingModel.get()
                                        },
                                        providerIdProvider = {
                                            settingsPrefs.voxtral.postProcessingProvider.get()
                                        },
                                    )
                                    val snapshot = current.entries.joinToString("\n") { (key, values) ->
                                        "$key: ${values.joinToString(", ")}"
                                    }
                                    client.rewriteStructured(
                                        input = "Current per-letter custom Unicode alternatives:\n$snapshot",
                                        instruction = userRequest,
                                        contract = SymbolAssignmentContract.prompt(),
                                    )
                                }
                                val parsed = result.fold(
                                    onSuccess = { SymbolAssignmentContract.parse(it) },
                                    onFailure = { Result.failure(it) },
                                )
                                val unchanged = current.all { (key, old) ->
                                    SymbolShortcutStore.forKey(key) == old
                                }
                                if (!unchanged) {
                                    aiMessage = "Assignments changed during the request. Discarded stale proposal."
                                } else {
                                    parsed.onSuccess { proposal ->
                                        aiProposal = proposal
                                        aiBaseline = current
                                    }.onFailure {
                                        aiMessage = "No valid AI proposal returned. Nothing was changed."
                                    }
                                }
                            } catch (_: Exception) {
                                aiMessage = "Provider request failed. Your key assignments are unchanged."
                            } finally {
                                aiBusy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("symbols-ai-generate"),
                ) { Text(if (aiBusy) "Generating…" else "Suggest changes") }
                OutlinedButton(
                    onClick = { navController.navigate(Routes.Settings.Voxtral) },
                    modifier = Modifier.weight(1f),
                ) { Text("BYOK settings") }
            }
            if (aiMessage.isNotBlank()) {
                Text(aiMessage, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val proposal = aiProposal
            if (proposal != null && aiBaseline != null) {
                Text(proposal.summary, modifier = Modifier.padding(horizontal = 16.dp))
                if (proposal.changes.isNotEmpty()) {
                    Text(
                        "Proposed assignments (original → proposed):",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                for ((key, proposedValues) in proposal.changes) {
                    val original = aiBaseline?.get(key).orEmpty()
                    Text(
                        "$key  ${original.joinToString("  ")}  →  ${proposedValues.joinToString("  ")}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
                if (proposal.recommendations.isNotEmpty()) {
                    Text(
                        "Tap a recommendation to stage it for the currently selected letter ($selectedKey).",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    for (recommended in proposal.recommendations) {
                        OutlinedButton(
                            onClick = {
                                val oldValues = proposal.changes[selectedKey]
                                    ?: SymbolShortcutStore.forKey(selectedKey)
                                if (recommended.symbol in oldValues) {
                                    aiMessage = "Already assigned to $selectedKey."
                                } else if (oldValues.size >= SymbolShortcutStore.MAX_SHORTCUTS_PER_KEY) {
                                    aiMessage = "$selectedKey already has six alternatives; remove one first."
                                } else {
                                    aiProposal = proposal.copy(
                                        changes = proposal.changes + (
                                            selectedKey to (oldValues + recommended.symbol)
                                        ),
                                    )
                                    aiMessage = "Staged ${recommended.symbol} for $selectedKey; approve to apply."
                                }
                            },
                            enabled = !aiBusy,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                .fillMaxWidth(),
                        ) { Text("${recommended.symbol} — ${recommended.label}") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = !aiBusy && proposal.changes.isNotEmpty(),
                        onClick = {
                            val baseline = aiBaseline ?: return@Button
                            val staged = aiProposal ?: return@Button
                            aiBusy = true
                            shortcutScope.launch {
                                try {
                                    val unchanged = baseline.all { (key, old) ->
                                        SymbolShortcutStore.forKey(key) == old
                                    }
                                    if (!unchanged) {
                                        aiMessage = "Keyboard assignments changed. Reject and generate again."
                                    } else if (SymbolShortcutStore.replaceApproved(context, staged.changes, expected = baseline)) {
                                        aiMessage = "Approved and saved. Changes are live on the keyboard."
                                        aiProposal = null
                                        aiBaseline = null
                                    } else {
                                        aiMessage = "Could not apply proposal. Original keys are unchanged."
                                    }
                                } finally {
                                    aiBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("symbols-ai-approve"),
                    ) { Text("✅ Approve") }
                    OutlinedButton(
                        onClick = {
                            aiProposal = null
                            aiBaseline = null
                            aiMessage = "Rejected. Your existing assignments were preserved."
                        },
                        enabled = !aiBusy,
                        modifier = Modifier.weight(1f).testTag("symbols-ai-reject"),
                    ) { Text("❌ Reject") }
                }
            }
        }

        OwnkeyGroup(title = "5. How it works") {
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
