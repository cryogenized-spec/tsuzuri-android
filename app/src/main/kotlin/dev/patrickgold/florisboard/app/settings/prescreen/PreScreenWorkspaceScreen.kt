/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.prescreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenAction
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenContract
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenDiff
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenDiffKind
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenFormat
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenFormatting
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenKind
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenPreferenceMemory
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenProposal
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenSyntax
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteClient
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteSecretsStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Local prototype. It intentionally does not read from or write to a foreign app
 * InputConnection: that bridge needs an editor-identity and content snapshot gate.
 * Only an explicit tap copies APPROVED text to the Android clipboard.
 */
@Composable
fun PreScreenWorkspaceScreen() = FlorisScreen {
    title = "Pre-screen workspace"
    previewFieldVisible = false

    val context = LocalContext.current
    val nav = LocalNavController.current
    content {
        val prefsRef = prefs
        val scope = rememberCoroutineScope()
        val countStored by prefsRef.preScreen.suggestionCount.collectAsState()
        val learnEnabled by prefsRef.preScreen.learningEnabled.collectAsState()
        val learnedKinds by prefsRef.preScreen.learnedKinds.collectAsState()
        val colorStored by prefsRef.preScreen.diffColor.collectAsState()
        val count = countStored.coerceIn(PreScreenContract.MIN_SUGGESTIONS, PreScreenContract.MAX_SUGGESTIONS)
        var colourInput by remember { mutableStateOf(colorStored) }
        LaunchedEffect(colorStored) { colourInput = colorStored }
        var countPreview by remember { mutableFloatStateOf(count.toFloat()) }
        LaunchedEffect(countStored) { countPreview = count.toFloat() }
        var syntax by remember { mutableStateOf(PreScreenSyntax.MARKDOWN) }
        var draft by remember { mutableStateOf(TextFieldValue("")) }
        var instruction by remember { mutableStateOf("") }
        var proposal by remember { mutableStateOf<PreScreenProposal?>(null) }
        var proposalSource by remember { mutableStateOf<String?>(null) }
        var selectedCategory by remember { mutableStateOf<PreScreenKind?>(null) }
        var approvedText by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var activeJob by remember { mutableStateOf<Job?>(null) }
        var requestGeneration by remember { mutableIntStateOf(0) }
        var feedback by remember { mutableStateOf<String?>(null) }

        fun requestEdit(editInstruction: String, category: PreScreenKind? = null) {
            if (busy || editInstruction.isBlank()) return
            val originalSnapshot = draft.text
            val proposalText = proposal?.revisedText ?: originalSnapshot
            if (proposalText.isBlank() || proposalText.length > PreScreenContract.MAX_SOURCE_CHARS) {
                feedback = "Enter a draft of up to 6,000 characters."
                return
            }
            // Explicit user action only. Never send text on keystroke, launch or focus.
            requestGeneration += 1
            val thisRequest = requestGeneration
            busy = true
            feedback = null
            activeJob = scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        val secrets = LlmRewriteSecretsStore(context)
                        val client = LlmRewriteClient(
                            apiKeyProvider = { secrets.getApiKey() },
                            endpointUrlProvider = { prefsRef.voxtral.postProcessingEndpointUrl.get() },
                            modelProvider = { prefsRef.voxtral.postProcessingModel.get() },
                            providerIdProvider = { prefsRef.voxtral.postProcessingProvider.get() },
                        )
                        client.rewriteStructured(
                            input = proposalText,
                            instruction = editInstruction,
                            contract = PreScreenContract.systemInstruction(
                                count,
                                if (learnEnabled) PreScreenPreferenceMemory.ranked(learnedKinds) else emptyList(),
                            ),
                        )
                    }
                    if (requestGeneration != thisRequest || draft.text != originalSnapshot) {
                        feedback = "Draft changed during the request. The stale proposal was discarded."
                        return@launch
                    }
                    val validated = response.fold(
                        onSuccess = { PreScreenContract.parse(it, count) },
                        onFailure = { Result.failure(it) },
                    )
                    validated.onSuccess {
                        proposalSource = originalSnapshot
                        proposal = it
                        selectedCategory = category
                        feedback = null
                    }.onFailure {
                        // Do not display provider responses, which may contain user text or secrets.
                        feedback = "AI proposal was unavailable or invalid. Original draft is unchanged."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    feedback = "Could not contact the configured AI provider. Draft is unchanged."
                } finally {
                    if (requestGeneration == thisRequest) {
                        busy = false
                        activeJob = null
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Experimental local draft workspace. Normal typing still uses the target app. " +
                    "No AI request is made until you ask for one.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { updated ->
                    if (updated.text.length <= PreScreenContract.MAX_SOURCE_CHARS) {
                        if (updated.text != draft.text) {
                            requestGeneration += 1
                            activeJob?.cancel()
                            activeJob = null
                            busy = false
                            proposal = null
                            proposalSource = null
                            selectedCategory = null
                            approvedText = null
                        }
                        draft = updated
                    }
                },
                label = { Text("Local draft / paste text") },
                modifier = Modifier.fillMaxWidth().testTag("prescreen-draft"),
                minLines = 5,
                maxLines = 12,
            )
            OutlinedButton(
                onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Tsuzuri draft", draft.text))
                    feedback = "Local draft copied. Paste it into the destination app."
                },
                enabled = draft.text.isNotBlank(),
                modifier = Modifier.testTag("prescreen-copy-draft"),
            ) { Text("Copy current draft") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { syntax = PreScreenSyntax.MARKDOWN },
                    enabled = syntax != PreScreenSyntax.MARKDOWN,
                ) { Text("Markdown") }
                OutlinedButton(
                    onClick = { syntax = PreScreenSyntax.WHATSAPP },
                    enabled = syntax != PreScreenSyntax.WHATSAPP,
                ) { Text("WhatsApp") }
            }
            Text("Format selected text, or the word at the cursor", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PreScreenFormat.entries.forEach { format ->
                    OutlinedButton(
                        onClick = {
                            val replacement = PreScreenFormatting.apply(
                                draft.text, draft.selection.start, draft.selection.end, format, syntax,
                            )
                            if (replacement != null) {
                                draft = TextFieldValue(
                                    replacement.text,
                                    selection = TextRange(replacement.selectionStart, replacement.selectionEnd),
                                )
                                requestGeneration += 1
                                activeJob?.cancel()
                                activeJob = null
                                busy = false
                                proposal = null
                                proposalSource = null
                                approvedText = null
                            }
                        },
                    ) { Text(format.label) }
                }
            }
            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it.take(350) },
                label = { Text("Describe the change for AI") },
                placeholder = { Text("Make this warmer but keep the technical details") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { requestEdit(instruction) },
                    enabled = !busy && instruction.isNotBlank() && draft.text.isNotBlank(),
                    modifier = Modifier.testTag("prescreen-generate"),
                ) { Text(if (busy) "Working…" else "Propose edit") }
                if (busy) {
                    TextButton(onClick = {
                        requestGeneration += 1
                        activeJob?.cancel()
                        activeJob = null
                        busy = false
                        feedback = "Request cancelled; draft unchanged."
                    }) { Text("Cancel") }
                }
            }
            TextButton(onClick = { nav.navigate(Routes.Settings.Voxtral) }) {
                Text("Configure BYOK AI provider")
            }
            if (feedback != null) Text(feedback.orEmpty(), color = MaterialTheme.colorScheme.error)

            val current = proposal
            if (current != null && proposalSource == draft.text) {
                Text("Review proposed changes", style = MaterialTheme.typography.titleMedium)
                val difference = remember(draft.text, current.revisedText) {
                    PreScreenDiff.between(draft.text, current.revisedText)
                }
                val changedColour = remember(colorStored) {
                    val rgb = colorStored.toLongOrNull(16)
                    if (colorStored.matches(Regex("[0-9A-Fa-f]{6}")) && rgb != null) {
                        Color((0xFF000000L or rgb).toInt())
                    } else Color(0xFF43A047)
                }
                val removedColour = MaterialTheme.colorScheme.error
                Text(
                    buildAnnotatedString {
                        difference.forEach { segment ->
                            when (segment.kind) {
                                PreScreenDiffKind.UNCHANGED -> append(segment.text)
                                PreScreenDiffKind.ADDED -> {
                                    pushStyle(SpanStyle(color = changedColour))
                                    append(segment.text)
                                    pop()
                                }
                                PreScreenDiffKind.REMOVED -> {
                                    pushStyle(
                                        SpanStyle(
                                            color = removedColour,
                                            textDecoration = TextDecoration.LineThrough,
                                        ),
                                    )
                                    append(segment.text)
                                    pop()
                                }
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().testTag("prescreen-diff"),
                )
                Text("Coloured text is added; struck-through text is removed.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val approved = current.revisedText
                            draft = TextFieldValue(approved, selection = TextRange(approved.length))
                            approvedText = approved
                            if (learnEnabled && selectedCategory != null) {
                                val category = requireNotNull(selectedCategory)
                                scope.launch {
                                    prefsRef.preScreen.learnedKinds.set(
                                        PreScreenPreferenceMemory.approved(learnedKinds, category),
                                    )
                                }
                            }
                            proposal = null
                            proposalSource = null
                            selectedCategory = null
                            feedback = "Approved locally. Copy to transfer it to another app."
                        },
                        enabled = !busy,
                        modifier = Modifier.testTag("prescreen-approve"),
                    ) { Text("✅ Approve") }
                    OutlinedButton(
                        onClick = {
                            proposal = null
                            proposalSource = null
                            selectedCategory = null
                            feedback = "Rejected. Your original draft is unchanged."
                        },
                        enabled = !busy,
                        modifier = Modifier.testTag("prescreen-reject"),
                    ) { Text("❌ Reject") }
                }
                current.followUp?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Optional next edits", style = MaterialTheme.typography.titleSmall)
                PreScreenPreferenceMemory.rankActions(
                    current.suggestions,
                    if (learnEnabled) learnedKinds else "{}",
                ).forEach { action: PreScreenAction ->
                    OutlinedButton(
                        onClick = { requestEdit(action.instruction, action.kind) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(action.label) }
                }
            }
            if (approvedText == draft.text && approvedText != null) {
                Button(
                    onClick = {
                        val clip = ClipData.newPlainText("Tsuzuri approved draft", approvedText.orEmpty())
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(clip)
                        feedback = "Approved text copied. Paste it into your destination app."
                    },
                    modifier = Modifier.testTag("prescreen-copy-approved"),
                ) { Text("Copy approved text") }
            }

            Text("Preferences", style = MaterialTheme.typography.titleMedium)
            Text("AI suggestions per proposal: ${countPreview.roundToInt()}")
            Slider(
                value = countPreview,
                onValueChange = { countPreview = it },
                onValueChangeFinished = {
                    scope.launch {
                        prefsRef.preScreen.suggestionCount.set(
                            countPreview.roundToInt().coerceIn(
                                PreScreenContract.MIN_SUGGESTIONS, PreScreenContract.MAX_SUGGESTIONS,
                            ),
                        )
                    }
                },
                valueRange = 3f..5f,
                steps = 1,
            )
            OutlinedTextField(
                value = colourInput,
                onValueChange = { entered ->
                    val normalized = entered.removePrefix("#").filter { it in '0'..'9' || it.uppercaseChar() in 'A'..'F' }
                        .take(6).uppercase()
                    colourInput = normalized
                    if (normalized.length == 6) {
                        scope.launch { prefsRef.preScreen.diffColor.set(normalized) }
                    }
                },
                label = { Text("Diff highlight colour (6 hex characters)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Learn from approved suggestion categories", modifier = Modifier.weight(1f))
                Switch(
                    checked = learnEnabled,
                    onCheckedChange = { value ->
                        scope.launch { prefsRef.preScreen.learningEnabled.set(value) }
                    },
                )
            }
            Text(
                "Learning is off by default. When enabled, only category counts are stored on this device, " +
                    "not drafts or message contents.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
                scope.launch { prefsRef.preScreen.learnedKinds.set("{}") }
            }) { Text("Clear learned preferences") }
        }
    }
}
