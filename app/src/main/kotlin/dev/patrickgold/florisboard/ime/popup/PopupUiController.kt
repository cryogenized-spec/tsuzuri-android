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

package dev.patrickgold.florisboard.ime.popup

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.computeImageVector
import dev.patrickgold.florisboard.ime.keyboard.computeLabel
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSet
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyHintConfiguration
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.lib.FlorisRect
import dev.patrickgold.florisboard.lib.toIntOffset

@Composable
fun rememberPopupUiController(
    key1: Any?,
    key2: Any?,
    boundsProvider: (key: Key) -> FlorisRect,
    isSuitableForBasicPopup: (key: Key) -> Boolean,
    isSuitableForExtendedPopup: (key: Key) -> Boolean,
): PopupUiController {
    val context = LocalContext.current
    return remember(key1, key2) {
        PopupUiController(context, boundsProvider, isSuitableForBasicPopup, isSuitableForExtendedPopup)
    }
}

val ExceptionsForKeyCodes = listOf(
    KeyCode.ENTER,
    KeyCode.LANGUAGE_SWITCH,
    KeyCode.IME_UI_MODE_TEXT,
    KeyCode.IME_UI_MODE_MEDIA,
    KeyCode.IME_UI_MODE_CLIPBOARD,
    KeyCode.KANA_SWITCHER,
    KeyCode.CHAR_WIDTH_SWITCHER,
)

@Suppress("unused")
class PopupUiController(
    val context: Context,
    val boundsProvider: (key: Key) -> FlorisRect,
    val isSuitableForBasicPopup: (key: Key) -> Boolean,
    val isSuitableForExtendedPopup: (key: Key) -> Boolean,
) {
    private var baseRenderInfo by mutableStateOf<BaseRenderInfo?>(null)
    private var extRenderInfo by mutableStateOf<ExtRenderInfo?>(null)

    private var activeElementIndex by mutableIntStateOf(-1)
    var evaluator: ComputingEvaluator = DefaultComputingEvaluator
    var keyHintConfiguration: KeyHintConfiguration = KeyHintConfiguration.HINTS_DISABLED

    /** Is true if the preview popup is visible to the user, else false */
    val isShowingPopup: Boolean
        get() = baseRenderInfo != null
    /** Is true if the extended popup is visible to the user, else false */
    val isShowingExtendedPopup: Boolean
        get() = extRenderInfo != null

    fun isSuitableForPopups(key: Key): Boolean {
        return isSuitableForBasicPopup(key) || isSuitableForExtendedPopup(key)
    }

    /**
     * Shows a preview popup for the passed [key]. Ignores show requests for keys which
     * key code is equal to or less than [KeyCode.SPACE].
     *
     * @param key Reference to the key currently controlling the popup.
     */
    fun show(key: Key) {
        if (!isSuitableForBasicPopup(key)) return

        baseRenderInfo = BaseRenderInfo(
            key = key,
            bounds = boundsProvider(key),
            shouldIndicateExtendedPopups = when (key) {
                is TextKey -> key.computedPopups.getPopupKeys(keyHintConfiguration).isNotEmpty()
                //is EmojiKey -> key.computedPopups.getPopupKeys(keyHintConfiguration).isNotEmpty()
                else -> false
            },
        )
    }

    /**
     * Extends the currently showing key preview popup if there are popup keys defined in the
     * key data of the passed [key]. Ignores extend requests for key views which key code
     * is equal to or less than [KeyCode.SPACE]. An exception is made for the codes defined in
     * [ExceptionsForKeyCodes], as they most likely have special keys bound to them.
     *
     * Layout of the extended key popup: (n = key.computedPopups.size)
     *   when n <= 5: single line, row0 only
     *     _ _ _ _ _
     *     K K K K K
     *   when n > 5 && n % 2 == 1: multi line, row0 has 1 more key than row1, empty space position
     *     is depending on the current anchor
     *     anchorLeft           anchorRight
     *     K K ... K _         _ K ... K K
     *     K K ... K K         K K ... K K
     *   when n > 5 && n % 2 == 0: multi line, both same length
     *     K K ... K K
     *     K K ... K K
     *
     * @param key Reference to the key currently controlling the popup.
     */
    fun extend(key: Key, size: Size) {
        if (!isSuitableForExtendedPopup(key)) return

        val baseBounds = baseRenderInfo?.bounds ?: boundsProvider(key)

        // Anchor left if keyView is in left half of keyboardView, else anchor right
        val anchorLeft = key.visibleBounds.left < size.width / 2
        val anchorRight = !anchorLeft

        // Determine key counts for each row
        val n = when (key) {
            is TextKey -> key.computedPopups.getPopupKeys(keyHintConfiguration).size
            //is EmojiKey -> key.computedPopups.getPopupKeys(keyHintConfiguration).size
            else -> 0
        }
        val row1count: Int
        val row0count: Int
        when {
            n <= 0 -> return
            n <= 5 -> {
                row1count = 0
                row0count = n
            }
            n > 5 && n % 2 == 1 -> {
                row1count = (n - 1) / 2
                row0count = (n + 1) / 2
            }
            else -> {
                row1count = n / 2
                row0count = n / 2
            }
        }

        // The panel may have more symbols than fit at preview-key width on a phone.
        // Shrink candidate cells just enough to keep the whole panel on-screen.
        val elementWidth = minOf(baseBounds.width, size.width / row0count).coerceAtLeast(1.0f)

        // Calculate anchor offset (always positive int, direction depends on anchorLeft and
        // anchorRight state)
        val anchorOffset = when {
            row0count <= 1 -> 0
            else -> {
                var offset = when {
                    row0count % 2 == 1 -> (row0count - 1) / 2
                    row0count % 2 == 0 -> (row0count / 2) - 1
                    else -> 0
                }
                val availableSpace = when {
                    anchorLeft -> key.visibleBounds.left + (key.visibleBounds.width - elementWidth) / 2.0f
                    anchorRight -> size.width -
                        (key.visibleBounds.left + (key.visibleBounds.width + elementWidth) / 2.0f)
                    else -> 0.0f
                }
                while (offset > 0) {
                    if (availableSpace >= offset * elementWidth) {
                        break
                    } else {
                        offset -= 1
                    }
                }
                offset
            }
        }

        val initUiIndex = when {
            anchorLeft -> anchorOffset + row1count
            anchorRight -> row0count - 1 - anchorOffset + row1count
            else -> 0
        }
        val popupIndices: IntArray
        val uiIndices = IntRange(0, (n - 1).coerceAtLeast(0))
        if (key is TextKey) {
            popupIndices = IntArray(n) { 0 }
            val popupKeys = key.computedPopups.getPopupKeys(keyHintConfiguration)
            when (popupKeys.prioritized.size) {
                // only one key: use initial position
                1 -> {
                    popupIndices[initUiIndex] = PopupKeys.FIRST_PRIORITIZED
                }
                // two keys: use initial position and one to the right if available, otherwise one to the left
                2 -> {
                    popupIndices[initUiIndex] = PopupKeys.FIRST_PRIORITIZED
                    when {
                        initUiIndex + 1 < n -> popupIndices[initUiIndex + 1] = PopupKeys.SECOND_PRIORITIZED
                        initUiIndex - 1 >= 0 -> popupIndices[initUiIndex - 1] = PopupKeys.SECOND_PRIORITIZED
                    }
                }
                // two keys: use initial position and one to either sides if available
                // otherwise two to the right or two to the left with decreasing priority
                3 -> {
                    popupIndices[initUiIndex] = PopupKeys.FIRST_PRIORITIZED
                    when {
                        initUiIndex + 1 < n && initUiIndex - 1 >= 0 -> {
                            popupIndices[initUiIndex + 1] = PopupKeys.SECOND_PRIORITIZED
                            popupIndices[initUiIndex - 1] = PopupKeys.THIRD_PRIORITIZED
                        }
                        initUiIndex + 2 < n -> {
                            popupIndices[initUiIndex + 1] = PopupKeys.SECOND_PRIORITIZED
                            popupIndices[initUiIndex + 2] = PopupKeys.THIRD_PRIORITIZED
                        }
                        initUiIndex - 2 >= 0 -> {
                            popupIndices[initUiIndex - 1] = PopupKeys.SECOND_PRIORITIZED
                            popupIndices[initUiIndex - 2] = PopupKeys.THIRD_PRIORITIZED
                        }
                    }
                }
            }
            var offset = 0
            for (uiIndex in uiIndices) {
                if (popupIndices[uiIndex] < 0) {
                    offset++
                } else {
                    popupIndices[uiIndex] = uiIndex - offset
                }
            }
        } else {
            popupIndices = IntArray(n) { it }
        }

        val elements: List<MutableList<Element>> = if (row1count > 0) {
            listOf(mutableListOf(), mutableListOf())
        } else {
            listOf(mutableListOf())
        }
        for (uiIndex in uiIndices) {
            val rowIndex = if (uiIndex < row1count && row1count > 0) { 1 } else { 0 }
            val adjustedIndex = popupIndices[uiIndex]
            val keyData = when (key) {
                is TextKey -> key.computedPopups.getPopupKeys(keyHintConfiguration)[adjustedIndex]
                //is EmojiKey -> key.computedPopups.getPopupKeys(keyHintConfiguration)[adjustedIndex]
                else -> TextKeyData.UNSPECIFIED
            }
            elements[rowIndex].add(Element(
                data = keyData,
                label = evaluator.computeLabel(keyData),
                icon = evaluator.computeImageVector(keyData),
                orderedIndex = uiIndex,
                adjustedIndex = adjustedIndex,
            ))
        }

        // Calculate layout params
        val extWidth = row0count * elementWidth
        val extHeight = when {
            row1count > 0 -> baseBounds.height * 0.4f * 2.0f
            else -> baseBounds.height * 0.4f
        }
        // Keep the preferred candidate over the pressed finger when space permits,
        // but clamp the *actual rendered* panel to the keyboard viewport at Q/P edges.
        val initialColumn = if (anchorLeft) anchorOffset else row0count - 1 - anchorOffset
        val x = (key.visibleBounds.left + key.visibleBounds.width / 2.0f -
            (initialColumn + 0.5f) * elementWidth)
            .coerceIn(0.0f, (size.width - extWidth).coerceAtLeast(0.0f))
        val y = -baseBounds.height - when {
            row1count > 0 -> (baseBounds.height * 0.4f).toInt()
            else -> 0
        } + key.visibleBounds.bottom
        val extBounds = FlorisRect.new(
            left = x, top = y, right = x + extWidth, bottom = y + extHeight,
        )

        extRenderInfo = ExtRenderInfo(
            elements = elements,
            baseBounds = baseBounds,
            bounds = extBounds,
            anchorLeft = anchorLeft,
            anchorRight = anchorRight,
            anchorOffset = anchorOffset,
            row0count = row0count,
            row1count = row1count,
        )
        activeElementIndex = initUiIndex
    }

    /**
     * Updates the current selected key in extended popup according to the passed [xEvent] and [yEvent].
     * This function does nothing if the extended popup is not showing and will return false.
     *
     * @param key Reference to the key currently controlling the popup.
     * @param xEvent The x coordinate of the MotionEvent.
     * @param yEvent The y coordinate of the MotionEvent.
     *
     * @return True if the pointer movement is within the elements bounds, false otherwise.
     */
    fun propagateMotionEvent(key: Key, xEvent: Float, yEvent: Float): Boolean {
        val rendered = extRenderInfo ?: return false
        // Hit testing uses the exact same bounds and cell width used by PopupExtBox.
        // The older un-clamped anchor arithmetic could highlight a different symbol
        // from the one under the finger, especially at the screen edges.
        val hitIndex = popupHitIndex(
            // Motion events here are already in keyboard-local coordinates.
            pointerX = xEvent,
            pointerY = yEvent,
            boundsLeft = rendered.bounds.left,
            boundsTop = rendered.bounds.top,
            cellWidth = rendered.bounds.width / rendered.row0count,
            cellHeight = rendered.baseBounds.height * 0.4f,
            row0count = rendered.row0count,
            row1count = rendered.row1count,
            rightAligned = rendered.anchorRight,
            keyBottom = key.visibleBounds.bottom,
        ) ?: return false
        activeElementIndex = hitIndex
        return true
    }

    /**
     * Gets the [TextKeyData] of the currently active key. May be either the key of the popup preview
     * or one of the keys in extended popup, if shown. Returns null if [key] is not a subclass of [TextKey].
     *
     * @param key Reference to the key currently controlling the popup.
     *
     * @return The [TextKeyData] object of the currently active key or null.
     */
    fun getActiveKeyData(key: Key): KeyData? {
        return if (key is TextKey) {
            val extRenderInfo = extRenderInfo ?: return key.computedData
            val element = getElementOrNull(extRenderInfo.elements, activeElementIndex)
            element?.data ?: key.computedData
        } else {
            null
        }
    }

    /**
     * Gets the [EmojiSet] of the currently active key. May be either the key of the popup
     * preview or one of the keys in extended popup, if shown. Returns null if [key] is noz a subclass of [EmojiKey].
     *
     * @param key Reference to the key currently controlling the popup.
     * @return The [EmojiSet] object of the currently active key or null.
     */
    fun getActiveEmojiKeyData(key: Key): KeyData? {
        return null
        //return if (key is EmojiKey) {
        //    val extRenderInfo = extRenderInfo ?: return null
        //    val element = getElementOrNull(extRenderInfo.elements, activeElementIndex)
        //    if (element != null) {
        //        key.computedPopups.getPopupKeys(KeyHintConfiguration.HINTS_DISABLED).getOrNull(element.adjustedIndex) ?: key.computedData
        //    } else {
        //        key.computedData
        //    }
        //} else {
        //    null
        //}
    }

    fun hide() {
        baseRenderInfo = null
        extRenderInfo = null
        activeElementIndex = -1
    }

    private fun getElementOrNull(elements: List<List<Element>>, index: Int): Element? {
        if (index < 0) {
            return null
        }
        var cachedIndex = index
        elements.asReversed().forEach { row ->
            if (cachedIndex >= row.size) {
                cachedIndex -= row.size
            } else {
                return row[cachedIndex]
            }
        }
        return null
    }

    @Composable
    fun RenderPopups(): Unit = with(LocalDensity.current) {
        val attributes = mapOf(
            FlorisImeUi.Attr.Mode to evaluator.keyboard.mode.toString(),
            FlorisImeUi.Attr.ShiftState to evaluator.state.inputShiftState.toString(),
        )
        baseRenderInfo?.let { renderInfo ->
            PopupBaseBox(
                modifier = Modifier
                    .requiredSize(renderInfo.bounds.size.toDpSize())
                    .absoluteOffset { renderInfo.bounds.topLeft.toIntOffset() },
                attributes = attributes,
                key = renderInfo.key,
                shouldIndicateExtendedPopups = renderInfo.shouldIndicateExtendedPopups && extRenderInfo == null,
            )
        }
        extRenderInfo?.let { renderInfo ->
            val baseBounds = renderInfo.baseBounds
            val elemWidth = renderInfo.bounds.width / renderInfo.row0count
            val elemHeight = baseBounds.height * 0.4f
            PopupExtBox(
                modifier = Modifier
                    .requiredSize(renderInfo.bounds.size.toDpSize())
                    .absoluteOffset { renderInfo.bounds.topLeft.toIntOffset() },
                attributes = attributes,
                elements = renderInfo.elements,
                elemArrangement = if (renderInfo.anchorLeft) {
                    Arrangement.Start
                } else {
                    Arrangement.End
                },
                elemWidth = elemWidth.toDp(),
                elemHeight = elemHeight.toDp(),
                activeElementIndex = activeElementIndex,
            )
        }
    }

    data class BaseRenderInfo(
        val key: Key,
        val bounds: FlorisRect,
        val shouldIndicateExtendedPopups: Boolean,
    )

    data class ExtRenderInfo(
        val elements: List<List<Element>>,
        val baseBounds: FlorisRect,
        val bounds: FlorisRect,
        val anchorLeft: Boolean,
        val anchorRight: Boolean,
        val anchorOffset: Int,
        val row0count: Int,
        val row1count: Int,
    )

    data class Element(
        val data: KeyData,
        val label: String?,
        val icon: ImageVector?,
        val orderedIndex: Int,
        val adjustedIndex: Int,
    )
}
