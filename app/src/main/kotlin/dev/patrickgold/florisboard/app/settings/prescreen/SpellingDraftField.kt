/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.prescreen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Native Compose editor with on-device, non-text-mutating red zigzag spelling
 * marks. Unlike a simple underlined annotated string, marks follow measured
 * glyph and wrapped-line boundaries. They never become part of pasted content.
 */
@Composable
internal fun SpellingDraftField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    issues: List<PreScreenSpellingIssue>,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        minLines = 5,
        maxLines = 12,
        singleLine = false,
        interactionSource = interactions,
        visualTransformation = VisualTransformation.None,
        onTextLayout = { layout = it },
        modifier = modifier.fillMaxWidth().testTag("prescreen-draft"),
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = {
                    // This box is precisely where BasicTextField places its glyphs.
                    // Drawing a wave here avoids guessing the decoration's padding.
                    Box(Modifier.drawWithContent {
                        drawContent()
                        val current = layout
                        if (current != null && current.layoutInput.text.text == value.text) {
                            drawSpellWaves(current, issues)
                        }
                    }) { inner() }
                },
                enabled = true,
                singleLine = false,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactions,
                label = { Text("Local draft / paste text") },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            )
        },
    )
}

private fun DrawScope.drawSpellWaves(
    layout: TextLayoutResult,
    issues: List<PreScreenSpellingIssue>,
) {
    val length = layout.layoutInput.text.length
    val halfWave = 2.dp.toPx()
    val amplitude = 1.2.dp.toPx()
    val color = Color(0xFFD32F2F)
    for (issue in issues) {
        val start = issue.word.start
        val end = issue.word.end
        if (start < 0 || end > length || end <= start) continue
        val firstLine = layout.getLineForOffset(start)
        val finalLine = layout.getLineForOffset(end - 1)
        for (line in firstLine..finalLine) {
            val startX = if (line == firstLine) {
                layout.getHorizontalPosition(start, usePrimaryDirection = true)
            } else layout.getLineLeft(line)
            val endX = if (line == finalLine) {
                layout.getHorizontalPosition(end, usePrimaryDirection = true)
            } else layout.getLineRight(line)
            val left = min(startX, endX)
            val right = max(startX, endX)
            if (right - left <= halfWave) continue
            val baseline = layout.getLineBaseline(line)
            val y = min(baseline + 3.dp.toPx(), layout.getLineBottom(line) - 1.dp.toPx())
            val wave = Path()
            wave.moveTo(left, y)
            var x = left
            var upwards = true
            while (x < right) {
                x = min(x + halfWave, right)
                wave.lineTo(x, y + if (upwards) -amplitude else amplitude)
                upwards = !upwards
            }
            drawPath(
                path = wave,
                color = color,
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
