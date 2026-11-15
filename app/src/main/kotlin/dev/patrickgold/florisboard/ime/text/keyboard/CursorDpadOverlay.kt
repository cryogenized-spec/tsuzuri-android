/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Only the original editor arrow actions are dispatched; no text is inserted. */
internal enum class CursorDpadDirection(val keyCode: Int, val glyph: String, val label: String) {
    UP(KeyCode.ARROW_UP, "↑", "Cursor up"),
    DOWN(KeyCode.ARROW_DOWN, "↓", "Cursor down"),
    LEFT(KeyCode.ARROW_LEFT, "←", "Cursor left"),
    RIGHT(KeyCode.ARROW_RIGHT, "→", "Cursor right"),
}

/**
 * Covers the existing keyboard with a dimmed surface and a reachable D-pad.
 * Short tap = one arrow action; press and hold = bounded repeats.
 * Outside tap or center X = dismiss and restore the original keyboard.
 */
@Composable
internal fun CursorDpadOverlay(
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClickLabel = "Close directional pad") { onDismiss() }
            .testTag("cursor-dpad-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(50.dp))
                DirectionButton(CursorDpadDirection.UP, onMove)
                Box(Modifier.size(50.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                DirectionButton(CursorDpadDirection.LEFT, onMove)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Close arrow controls") { onDismiss() }
                        .testTag("cursor-dpad-close"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
                DirectionButton(CursorDpadDirection.RIGHT, onMove)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(50.dp))
                DirectionButton(CursorDpadDirection.DOWN, onMove)
                Box(Modifier.size(50.dp))
            }
        }
    }
}

@Composable
private fun DirectionButton(
    direction: CursorDpadDirection,
    onMove: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .pointerInput(direction) {
                detectTapGestures(onPress = {
                    val gesture = this
                    coroutineScope {
                        var repeated = false
                        val repeating = launch {
                            delay(350L)
                            while (isActive) {
                                repeated = true
                                onMove(direction.keyCode)
                                delay(85L)
                            }
                        }
                        val released = try {
                            gesture.tryAwaitRelease()
                        } finally {
                            repeating.cancel()
                        }
                        if (released && !repeated) {
                            onMove(direction.keyCode)
                        }
                    }
                })
            }
            .semantics {
                role = Role.Button
                contentDescription = direction.label
                onClick {
                    onMove(direction.keyCode)
                    true
                }
            }
            .testTag("cursor-dpad-${direction.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(direction.glyph, style = MaterialTheme.typography.headlineSmall)
    }
}
