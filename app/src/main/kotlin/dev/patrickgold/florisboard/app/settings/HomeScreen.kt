/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.theme.AppearanceSummary
import dev.patrickgold.florisboard.app.settings.voxtral.AiStatusLine
import dev.patrickgold.florisboard.app.settings.voxtral.rememberAiStatus
import dev.patrickgold.florisboard.app.settings.voxtral.toLine
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyStatusDot
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.keyboard.SplitLayoutMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ownkeyFocusRing
import org.florisboard.lib.compose.stringRes
import androidx.compose.runtime.collectAsState as collectFlowAsState

private const val StackedLineFontScale = 1.5f

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        // Avoid outer FlorisScreen receiver shadowing inside option lambdas.
        val settingsPrefs = prefs
        val isOwnkeyEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isOwnkeySelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isOwnkeyEnabled) {
            HomeSetupNotice(
                title = stringRes(R.string.settings__home__off_title),
                body = stringRes(R.string.settings__home__off_body),
                actionLabel = stringRes(R.string.settings__home__off_action),
                onAction = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isOwnkeySelected) {
            HomeSetupNotice(
                title = stringRes(R.string.settings__home__not_selected_title),
                body = null,
                actionLabel = stringRes(R.string.settings__home__not_selected_action),
                onAction = { InputMethodUtils.showImePicker(context) },
            )
        }

        val subtypeManager by context.subtypeManager()
        val subtypes by subtypeManager.subtypesFlow.collectFlowAsState()
        val displayLanguageNamesIn by settingsPrefs.localization.displayLanguageNamesIn.collectAsState()
        val languages = subtypes
            .map { subtype ->
                when (displayLanguageNamesIn) {
                    DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayLanguage()
                    DisplayLanguageNamesIn.NATIVE_LOCALE -> subtype.primaryLocale.displayLanguage(subtype.primaryLocale)
                }
            }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

        val themeMode by settingsPrefs.theme.mode.collectAsState()
        val dayThemeId by settingsPrefs.theme.dayThemeId.collectAsState()
        val nightThemeId by settingsPrefs.theme.nightThemeId.collectAsState()
        val appearance = stringRes(
            when (AppearanceSummary.of(themeMode, dayThemeId, nightThemeId)) {
                AppearanceSummary.GRAPHITE -> R.string.settings__appearance__graphite
                AppearanceSummary.BLACK -> R.string.settings__appearance__black
                AppearanceSummary.LIGHT -> R.string.settings__appearance__light
                AppearanceSummary.AUTO_GRAPHITE -> R.string.settings__appearance__auto_graphite
                AppearanceSummary.AUTO_BLACK -> R.string.settings__appearance__auto_black
                AppearanceSummary.CUSTOM -> R.string.settings__appearance__custom
            }
        )

        // First-run evaluation takes two taps, rather than a tour through
        // 70+ themes or a language-subtype editor. Each option is already built in.
        val quickTryScope = rememberCoroutineScope()
        val activeSubtype by subtypeManager.activeSubtypeFlow.collectFlowAsState()
        val splitLayoutMode by settingsPrefs.keyboard.splitLayoutMode.collectAsState()
        val windowConfigs by settingsPrefs.keyboard.windowConfig.collectAsState()
        val configuration = LocalConfiguration.current
        val oneHand = QuickHandPreset.of(
            windowConfigs, configuration.screenWidthDp, configuration.screenHeightDp,
        )
        var showInputDemo by remember { mutableStateOf(false) }
        var quickInputDemo by remember { mutableStateOf(QuickInputDemo.TEXT) }
        var scratchInput by remember { mutableStateOf("") }

        OwnkeyGroup(title = "Try the keyboard") {
            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("quick-try-theme"),
                title = "Appearance",
                summary = "Choose a ready-made look. Try typing in the preview to see it immediately.",
                options = listOf(
                    ChoiceOption(QuickThemePreset.SYSTEM, "Follow my phone"),
                    ChoiceOption(QuickThemePreset.GRAPHITE, "Graphite"),
                    ChoiceOption(QuickThemePreset.BLACK, "OLED Black"),
                    ChoiceOption(QuickThemePreset.BONE, "Warm Light"),
                    ChoiceOption(QuickThemePreset.GLASS, "AMOLED Glass"),
                    ChoiceOption<QuickThemePreset?>(null, "Browse all themes"),
                ),
                selected = QuickThemePreset.of(themeMode, dayThemeId, nightThemeId),
                onSelect = { choice ->
                    if (choice == null) {
                        navController.navigate(Routes.Settings.Theme)
                    } else {
                        quickTryScope.launch {
                            val writes = choice.writes()
                            writes.mode?.let { settingsPrefs.theme.mode.set(it) }
                            writes.glassPreset?.let { settingsPrefs.theme.glassPreset.set(it) }
                            writes.dayThemeId?.let { settingsPrefs.theme.dayThemeId.set(it) }
                            writes.nightThemeId?.let { settingsPrefs.theme.nightThemeId.set(it) }
                        }
                    }
                },
            )

            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("quick-try-layout"),
                title = "Keyboard layout",
                summary = "Changes your current language's layout; other languages remain untouched.",
                options = listOf(
                    ChoiceOption(QuickLayoutPreset.QWERTY, "QWERTY"),
                    ChoiceOption(QuickLayoutPreset.QWERTZ, "QWERTZ"),
                    ChoiceOption(QuickLayoutPreset.AZERTY, "AZERTY"),
                    ChoiceOption(QuickLayoutPreset.DVORAK, "Dvorak"),
                    ChoiceOption(QuickLayoutPreset.COLEMAK, "Colemak"),
                    ChoiceOption(QuickLayoutPreset.BEPO, "BÉPO"),
                    ChoiceOption<QuickLayoutPreset?>(null, "All 77 character layouts"),
                ),
                selected = QuickLayoutPreset.of(activeSubtype.layoutMap.characters),
                onSelect = { choice ->
                    if (choice == null) {
                        navController.navigate(Routes.Settings.Localization)
                    } else {
                        val updated = activeSubtype.copy(
                            layoutMap = activeSubtype.layoutMap.copy(characters = choice.componentId),
                        )
                        if (subtypeManager.subtypes.any { it.id == activeSubtype.id }) {
                            subtypeManager.modifySubtypeWithSameId(updated)
                        } else {
                            subtypeManager.addSubtype(updated)
                        }
                    }
                },
            )

            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("quick-try-split"),
                title = "Typing width",
                summary = "Split keyboard for two thumbs, or keep a full-width keyboard.",
                options = listOf(
                    ChoiceOption(SplitLayoutMode.NEVER, "Full-width"),
                    ChoiceOption(SplitLayoutMode.AUTO, "Split on tablets and foldables"),
                    ChoiceOption(SplitLayoutMode.ALWAYS, "Split for two thumbs"),
                ),
                selected = splitLayoutMode,
                onSelect = { chosen ->
                    quickTryScope.launch {
                        // Split and one-handed are alternative geometry modes.
                        if (chosen != SplitLayoutMode.NEVER && oneHand != QuickHandPreset.FULL) {
                            settingsPrefs.keyboard.windowConfig.set(
                                QuickHandPreset.update(
                                    settingsPrefs.keyboard.windowConfig.get(),
                                    configuration.screenWidthDp,
                                    configuration.screenHeightDp,
                                    QuickHandPreset.FULL,
                                ),
                            )
                        }
                        settingsPrefs.keyboard.splitLayoutMode.set(chosen)
                    }
                },
            )

            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("quick-try-one-handed"),
                title = "One-handed typing",
                summary = "Shrink the keyboard so your thumb can reach every key.",
                options = listOf(
                    ChoiceOption(QuickHandPreset.FULL, "Full keyboard"),
                    ChoiceOption(QuickHandPreset.LEFT, "Left thumb"),
                    ChoiceOption(QuickHandPreset.RIGHT, "Right thumb"),
                ),
                selected = oneHand,
                onSelect = { choice ->
                    quickTryScope.launch {
                        if (choice != QuickHandPreset.FULL) {
                            settingsPrefs.keyboard.splitLayoutMode.set(SplitLayoutMode.NEVER)
                        }
                        settingsPrefs.keyboard.windowConfig.set(
                            QuickHandPreset.update(
                                settingsPrefs.keyboard.windowConfig.get(),
                                configuration.screenWidthDp,
                                configuration.screenHeightDp,
                                choice,
                            ),
                        )
                    }
                },
            )

            OwnkeyRow(
                modifier = Modifier.testTag("quick-try-input-modes"),
                title = "Test number and phone keypads",
                summary = "Try a real input field, not a static keyboard picture.",
                value = if (showInputDemo) "Hide" else "Show",
                onClick = {
                    showInputDemo = !showInputDemo
                    scratchInput = ""
                },
            )
            if (showInputDemo) {
                OwnkeyChoiceListRow(
                    modifier = Modifier.testTag("quick-try-input-kind"),
                    title = "Input field type",
                    options = listOf(
                        ChoiceOption(QuickInputDemo.TEXT, "Text"),
                        ChoiceOption(QuickInputDemo.NUMBER, "Numbers"),
                        ChoiceOption(QuickInputDemo.PHONE, "Phone dialpad"),
                    ),
                    selected = quickInputDemo,
                    onSelect = {
                        quickInputDemo = it
                        scratchInput = ""
                    },
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("quick-try-input"),
                    value = scratchInput,
                    onValueChange = { scratchInput = it.take(80) },
                    label = { Text("Try typing here") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (quickInputDemo) {
                            QuickInputDemo.TEXT -> KeyboardType.Text
                            QuickInputDemo.NUMBER -> KeyboardType.Number
                            QuickInputDemo.PHONE -> KeyboardType.Phone
                        },
                    ),
                )
            }
            OwnkeyRow(
                modifier = Modifier.testTag("quick-try-custom-symbols"),
                title = "Custom long-press symbols",
                summary = "Tap Q, E or any letter and choose your own accents, signs or emoji.",
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.SymbolShortcuts) },
            )
        }

        HomeAiCard(onClick = { navController.navigate(Routes.Settings.Voxtral) })

        OwnkeyGroup {
            OwnkeyRow(
                modifier = Modifier.testTag("home-languages"),
                title = stringRes(R.string.settings__home__languages),
                value = languages,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-appearance"),
                title = stringRes(R.string.settings__home__appearance),
                value = appearance,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-keyboard"),
                title = stringRes(R.string.settings__home__keyboard),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-typing"),
                title = stringRes(R.string.settings__home__typing),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-clipboard"),
                title = stringRes(R.string.settings__home__clipboard),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-privacy"),
                title = stringRes(R.string.settings__home__privacy),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Privacy) },
            )
        }

        OwnkeyGroup {
            OwnkeyRow(
                modifier = Modifier.testTag("home-about"),
                title = stringRes(R.string.settings__home__about),
                // The version without its build metadata ("+<commit>"); About shows the full string.
                value = BuildConfig.VERSION_NAME.substringBefore('+'),
                // Inter's tabular forms put wide gaps around the dots and the hyphen of "0.10.0-debug".
                tabularValue = false,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.About) },
            )
        }
    }
}

/** Shown while Ownkey can't type yet: what's wrong in a few words and one button that fixes it. */
@Composable
private fun HomeSetupNotice(
    title: String,
    body: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(bottom = OwnkeyTokens.GroupGap)
            .fillMaxWidth()
            .clip(OwnkeyTokens.CardShape)
            .background(OwnkeyTokens.Card)
            .testTag("home-setup")
            .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OwnkeyTokens.Label,
        )
        if (body != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnkeyTokens.Secondary,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OwnkeyButton(
            modifier = Modifier.testTag("home-setup-action"),
            label = actionLabel,
            onClick = onAction,
        )
    }
}

/** What dictation and rewrite use right now. The whole card opens AI. */
@Composable
private fun HomeAiCard(onClick: () -> Unit) {
    val status = rememberAiStatus()
    val dictation = status.dictation.toLine()
    val rewrite = status.rewrite.toLine()
    Column(
        modifier = Modifier
            .padding(bottom = OwnkeyTokens.GroupGap)
            .fillMaxWidth()
            .clip(OwnkeyTokens.CardShape)
            .background(OwnkeyTokens.Card)
            .ownkeyFocusRing(OwnkeyTokens.CardShape, inset = true)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .testTag("home-ai")
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.settings__home__ai),
                style = MaterialTheme.typography.titleMedium,
                color = OwnkeyTokens.Label,
            )
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OwnkeyTokens.Secondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AiStatusLineRow(label = stringRes(R.string.settings__home__ai_dictation), line = dictation)
        Spacer(modifier = Modifier.height(6.dp))
        AiStatusLineRow(label = stringRes(R.string.settings__home__ai_rewrite), line = rewrite)
    }
}

/** "Dictation    Cloud · Mistral": the label in Secondary, the value in Label, or Warning with a dot. */
@Composable
private fun AiStatusLineRow(label: String, line: AiStatusLine) {
    val typography = MaterialTheme.typography
    val labelText: @Composable () -> Unit = {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = OwnkeyTokens.Secondary,
        )
    }
    // Stacked under its label at large font scales, the value starts at the label's edge and may take two lines,
    // like a stacked row value.
    val stacked = LocalDensity.current.fontScale >= StackedLineFontScale
    val valueText: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier = modifier,
            horizontalArrangement = if (stacked) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (line.needsSetup) {
                OwnkeyStatusDot(tone = OwnkeyTone.Warning)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = line.text,
                style = typography.bodyMedium,
                color = if (line.needsSetup) OwnkeyTokens.Warning else OwnkeyTokens.Label,
                textAlign = if (stacked) TextAlign.Start else TextAlign.End,
                maxLines = if (stacked) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (stacked) {
        Column {
            labelText()
            valueText(Modifier)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labelText()
            Spacer(modifier = Modifier.width(16.dp))
            valueText(Modifier.weight(1f))
        }
    }
}
