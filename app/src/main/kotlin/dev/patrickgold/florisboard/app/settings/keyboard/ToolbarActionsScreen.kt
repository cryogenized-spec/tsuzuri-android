/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.computeDisplayName
import dev.patrickgold.florisboard.ime.smartbar.quickaction.hideToolbarAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.moveToolbarAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.pinToolbarAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.showToolbarAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.unpinToolbarAction
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Accessible, discoverable alternative to the keyboard's existing long-press drag editor.
 * Both editors read and write the same actionArrangement preference; edits apply immediately.
 */
@Composable
fun ToolbarActionsScreen() = FlorisScreen {
    title = "Arrange toolbar"
    previewFieldVisible = true

    content {
        val scope = rememberCoroutineScope()
        val saveMutex = remember { Mutex() }
        val context = LocalContext.current
        val keyboardManager by context.keyboardManager()
        val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
        val arrangement by prefs.smartbar.actionArrangement.collectAsState()

        // Always transform the latest persisted value: quick consecutive taps cannot overwrite
        // a previous movement using an obsolete composable snapshot.
        fun update(transform: (QuickActionArrangement) -> QuickActionArrangement) {
            scope.launch {
                saveMutex.withLock {
                    val current = prefs.smartbar.actionArrangement.get()
                    val updated = transform(current)
                    if (updated != current) {
                        prefs.smartbar.actionArrangement.set(updated)
                    }
                }
            }
        }

        OwnkeyGroup(title = "Order of shortcuts") {
            Text(
                "The first shortcuts appear in the keyboard's top toolbar; the rest are in " +
                    "its overflow. Use the arrows to change their left-to-right order. " +
                    "Changes are saved immediately.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OwnkeyGroup(title = "Pinned shortcut") {
            val pinned = arrangement.stickyAction
            if (pinned == null) {
                Text(
                    "No pinned shortcut. Pin a tool below to keep it in this separate slot.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pinned.computeDisplayName(evaluator),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { update { it.unpinToolbarAction() } },
                        modifier = Modifier.testTag("toolbar-unpin"),
                    ) { Text("Unpin") }
                }
            }
        }

        OwnkeyGroup(title = "Top toolbar and overflow") {
            if (arrangement.dynamicActions.isEmpty()) {
                Text(
                    "No active shortcuts. Restore shortcuts from the hidden list below.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            arrangement.dynamicActions.forEachIndexed { index, action ->
                val label = action.computeDisplayName(evaluator)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("toolbar-item-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(
                        text = "${index + 1}. $label",
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(
                        onClick = { update { it.moveToolbarAction(index, index - 1) } },
                        enabled = index > 0,
                        modifier = Modifier.testTag("toolbar-move-up-$index"),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $label earlier")
                    }
                    IconButton(
                        onClick = { update { it.moveToolbarAction(index, index + 1) } },
                        enabled = index < arrangement.dynamicActions.lastIndex,
                        modifier = Modifier.testTag("toolbar-move-down-$index"),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $label later")
                    }
                    IconButton(
                        onClick = { update { it.pinToolbarAction(index) } },
                        modifier = Modifier.testTag("toolbar-pin-$index"),
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = "Pin $label")
                    }
                    IconButton(
                        onClick = { update { it.hideToolbarAction(index) } },
                        modifier = Modifier.testTag("toolbar-hide-$index"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Hide $label")
                    }
                }
            }
        }

        OwnkeyGroup(title = "Hidden shortcuts") {
            if (arrangement.hiddenActions.isEmpty()) {
                Text(
                    "Nothing hidden.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            arrangement.hiddenActions.forEachIndexed { index, action ->
                val label = action.computeDisplayName(evaluator)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { update { it.showToolbarAction(index) } },
                        modifier = Modifier.testTag("toolbar-show-$index"),
                    ) { Text("Show") }
                }
            }
        }
    }
}
