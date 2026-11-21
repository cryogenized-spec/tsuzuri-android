/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.SnyggRow

internal val ToggleOverflowPanelAction = QuickAction.InsertKey(TextKeyData.TOGGLE_ACTIONS_OVERFLOW)

/**
 * Maximum number of actions shown in the secondary (extended) action pill, regardless of available width.
 * The reference design shows six actions plus the overflow toggle; everything else lives in the overflow
 * panel. Without this cap, wide screens (landscape, unfolded foldables, tablets) cram a dozen or more
 * icons into the pill, which reads as cluttered.
 */
private const val MaxSecondaryRowActionCount = 6

// An explicit inner gutter keeps the first and last icon out of a themed pill's rounded ends.
// The width calculation below runs *after* this inset, so the overflow target is never clipped.
private val SecondaryRowHorizontalInset = 6.dp

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun QuickActionsRow(
    elementName: String,
    modifier: Modifier = Modifier,
) = with(LocalDensity.current) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val flipToggles by prefs.smartbar.flipToggles.collectAsState()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    val smartbarLayout by prefs.smartbar.layout.collectAsState()
    val actionArrangement by prefs.smartbar.actionArrangement.collectAsState()
    val sharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.collectAsState()

    val dynamicActions = remember(smartbarLayout, actionArrangement) {
        if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
            buildList {
                add(actionArrangement.stickyAction!!)
                addAll(actionArrangement.dynamicActions)
            }
        } else {
            actionArrangement.dynamicActions
        }
    }
    val showOverflowAction = actionArrangement.stickyAction != null ||
        smartbarLayout != SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED || !sharedActionsExpanded

    // SnyggRow consumes theme margins/borders/padding before measuring its children.
    // Measure *inside* that surface: using outer keyboard width here can squeeze the
    // last overflow action to a tiny target on narrow glass/Signal themes.
    SnyggRow(
        elementName = elementName,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().then(
                if (elementName == FlorisImeUi.SmartbarExtendedActionsRow.elementName) {
                    Modifier.padding(horizontal = SecondaryRowHorizontalInset)
                } else {
                    Modifier
                }
            ),
        ) {
            val contentWidth = constraints.maxWidth.toDp()
            val contentHeight = constraints.maxHeight.toDp()
            val isExtendedActionsRow = elementName == FlorisImeUi.SmartbarExtendedActionsRow.elementName
            val actionButtonAspectRatio = if (isExtendedActionsRow) {
                SecondaryQuickActionButtonAspectRatio
            } else {
                QuickActionButtonAspectRatio
            }
            val iconSizePercent by prefs.theme.toolbarIconSizePercent.collectAsState()
            val actionIconSize = (if (isExtendedActionsRow) {
                SecondaryQuickActionButtonIconSize
            } else {
                QuickActionButtonIconSize
            }) * (iconSizePercent.coerceIn(50, 200) / 100f)
            val actionWidth = contentHeight * actionButtonAspectRatio
            val maxActionsToShow = if (isExtendedActionsRow) MaxSecondaryRowActionCount else Int.MAX_VALUE
            val numActionsToShow = visibleToolbarActionCount(
                contentWidth = contentWidth,
                actionWidth = actionWidth,
                hasOverflow = showOverflowAction,
                maxActions = maxActionsToShow,
            )
            val visibleActions = dynamicActions.take(numActionsToShow)

            SideEffect {
                keyboardManager.smartbarVisibleDynamicActionsCount =
                    if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
                        numActionsToShow - 1
                    } else {
                        numActionsToShow
                    }.coerceAtLeast(0)
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isExtendedActionsRow) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            ) {
                val actionModifier = Modifier.width(actionWidth).fillMaxHeight()
                if (showOverflowAction && flipToggles) {
                    QuickActionButton(
                        ToggleOverflowPanelAction,
                        evaluator,
                        modifier = actionModifier,
                        aspectRatio = actionButtonAspectRatio,
                        fillContainer = true,
                        iconSize = actionIconSize,
                    )
                }
                for (action in visibleActions) {
                    QuickActionButton(
                        action,
                        evaluator,
                        modifier = actionModifier,
                        aspectRatio = actionButtonAspectRatio,
                        fillContainer = true,
                        iconSize = actionIconSize,
                    )
                }
                if (showOverflowAction && !flipToggles) {
                    QuickActionButton(
                        ToggleOverflowPanelAction,
                        evaluator,
                        modifier = actionModifier,
                        aspectRatio = actionButtonAspectRatio,
                        fillContainer = true,
                        iconSize = actionIconSize,
                    )
                }
            }
        }
    }
}

/**
 * Count shortcuts from the actual content bounds, *after* theme margins/padding.
 * Reserving one full-width slot for overflow prevents it becoming a 17dp sliver.
 */
internal fun visibleToolbarActionCount(
    contentWidth: androidx.compose.ui.unit.Dp,
    actionWidth: androidx.compose.ui.unit.Dp,
    hasOverflow: Boolean,
    maxActions: Int,
): Int {
    if (contentWidth.value <= 0f || actionWidth.value <= 0f) return 0
    val totalFit = (contentWidth / actionWidth).toInt().coerceAtLeast(0)
    return (totalFit - if (hasOverflow) 1 else 0).coerceIn(0, maxActions)
}
