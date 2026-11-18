/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteClient
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.subtypeManager
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
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()

    content {
        val shortcutScope = rememberCoroutineScope()
        LaunchedEffect(context) { SymbolShortcutStore.ensureLoaded(context) }
        val revision by SymbolShortcutStore.revision.collectAsState()
        val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
        val activeLayout = activeSubtype.layoutMap.characters.componentId
        // Reuse the extension-aware layout used by the IME, once per subtype.
        val layoutRows by produceState<List<List<String>>>(
            initialValue = emptyList(), key1 = activeSubtype.layoutMap.characters,
        ) {
            value = runCatching {
                val keyboard = keyboardManager.layoutManager
                    .computeKeyboardAsync(KeyboardMode.CHARACTERS, activeSubtype).await()
                SymbolKeyboardRows.fromLabels(keyboard.arrangement.map { row ->
                    row.mapNotNull { key ->
                        key.data.compute(DefaultComputingEvaluator)?.label
                    }
                })
            }.getOrDefault(emptyList())
        }
        var chosenPreset by rememberSaveable { mutableStateOf(SymbolPreset.WORK) }
        var pendingPreset by remember { mutableStateOf<SymbolPreset?>(null) }
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

        OwnkeyGroup(title = "Symbol presets") {
            OwnkeyChoiceListRow(
                title = "Default set",
                options = SymbolPreset.entries.map { preset ->
                    ChoiceOption(preset, "${preset.title} — ${preset.description}")
                },
                selected = chosenPreset,
                onSelect = { chosenPreset = it },
            )
            Button(
                onClick = { pendingPreset = chosenPreset },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("unicode-apply-preset"),
            ) { Text("Apply 3 symbols per letter") }
        }

        if (pendingPreset != null) {
            val preset = pendingPreset!!
            OwnkeyDialog(
                title = "Apply ${preset.title} symbols?",
                text = "Replace the custom symbols on all 26 letter keys with the three ${preset.title.lowercase()} defaults? Existing assignments will be overwritten.",
                onDismiss = { pendingPreset = null },
                confirmLabel = "Apply",
                onConfirm = {
                    pendingPreset = null
                    val expected = ('A'..'Z').associate { letter ->
                        letter.toString() to SymbolShortcutStore.forKey(letter.toString())
                    }
                    shortcutScope.launch {
                        message = if (SymbolShortcutStore.replaceApproved(
                                context, preset.assignments, expected = expected,
                            )
                        ) "${preset.title} symbols applied." else
                            "Could not apply the preset because symbols changed or storage failed."
                    }
                },
                dismissLabel = "Cancel",
            )
        }

        OwnkeyGroup(title = "Key layout · ${activeLayout.uppercase()}") {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                text = "Choose a letter to edit its long-press symbols.",
            )
            if (layoutRows.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = "No Latin letter keys available for this layout.",
                )
            }
            for (rowKeys in layoutRows) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (letter in rowKeys) {
                        val modifier = Modifier.width(44.dp).height(48.dp)
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
                }
            }
        }

        OwnkeyGroup(title = "Long-press options") {
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
            val editingEnabled by prefs.keyboard.editingShortcutsEnabled.collectPrefsAsState()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("A · Select All | X · Cut | C · Copy | V · Paste", modifier = Modifier.weight(1f))
                Switch(
                    checked = editingEnabled,
                    onCheckedChange = { enabled ->
                        shortcutScope.launch { prefs.keyboard.editingShortcutsEnabled.set(enabled) }
                    },
                )
            }
            Text(
                "Existing symbols are retained. Hiding accents only hides inherited accents; " +
                    "an accented character you assign manually remains available.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        OwnkeyGroup(title = "Symbols for $selectedKey") {
            val count = SymbolShortcutStore.MAX_SHORTCUTS_PER_KEY
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "Custom alternatives: ${assigned.size}/$count. Tap an existing symbol to remove it.",
            )
            for ((slot, value) in assigned.withIndex()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${slot + 1}. $value", modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            val key = selectedKey
                            shortcutScope.launch { SymbolShortcutStore.move(context, key, value, -1) }
                        },
                        enabled = slot > 0,
                        modifier = Modifier.width(48.dp).height(44.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("↑") }
                    OutlinedButton(
                        onClick = {
                            val key = selectedKey
                            shortcutScope.launch { SymbolShortcutStore.move(context, key, value, 1) }
                        },
                        enabled = slot < assigned.lastIndex,
                        modifier = Modifier.width(48.dp).height(44.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("↓") }
                    TextButton(
                        onClick = {
                            val key = selectedKey
                            shortcutScope.launch {
                                message = if (SymbolShortcutStore.remove(context, key, value)) {
                                    "Removed $value from $key."
                                } else "This symbol is no longer assigned."
                            }
                        },
                        modifier = Modifier.testTag("unicode-remove-$value"),
                    ) { Text("Remove") }
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

        OwnkeyGroup(title = "AI symbol assistant (optional)") {
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
                                        apiKeyProvider = { secrets.getApiKey() },
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

        OwnkeyGroup(title = "How it works") {
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
