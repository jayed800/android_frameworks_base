/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.featurepods.popups.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.screenrecord.shared.model.ScreenRecordPopupModel
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Phone-only centered dynamic island that pages through active popup chips.
 *
 * Chips are ordered by the [priority] of their event; time-critical events (calls, media) break
 * into the island first. Transient events auto-collapse back to the compact pill after
 * [AutoCollapseTimeoutMs] of inactivity.
 */
@Composable
fun StatusBarDynamicIslandContainer(
    chips: List<PopupChipModel.Shown>,
    onMediaControlPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cutoutSpec = rememberDynamicIslandCutoutSpec()
    var selectedChipId by remember { mutableStateOf<PopupChipId?>(null) }
    var popupAnchorChip by remember { mutableStateOf<PopupChipModel.Shown?>(null) }
    var popupVisible by remember { mutableStateOf(false) }
    var knownChipIds by remember { mutableStateOf<List<PopupChipId>>(emptyList()) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val view = LocalView.current

    LaunchedEffect(chips) {
        val currentChipIds = chips.map { it.chipId }
        val previousPriority = selectedChipId?.let { id -> chips.indexOfFirst { it.chipId == id } }
        // The list is priority-ordered, so the first previously-unknown chip is the newest
        // event. If it is more important than the currently selected chip, let it break in and
        // expand with the iOS-style spring animation.
        val newestChipId = currentChipIds.firstOrNull { it !in knownChipIds }
        selectedChipId =
            when {
                newestChipId != null && previousPriority != null &&
                    currentChipIds.indexOf(newestChipId) < previousPriority ->
                    newestChipId
                newestChipId != null -> newestChipId
                chips.any { it.chipId == selectedChipId } -> selectedChipId
                else -> chips.firstOrNull()?.chipId
            }
        newestChipId?.let { id ->
            chips.firstOrNull { it.chipId == id }?.let { chip ->
                // New events expand automatically, like iOS Live Activities.
                chip.showPopup()
            }
        }
        knownChipIds = currentChipIds
    }

    val selectedIndex = chips.indexOfFirst { it.chipId == selectedChipId }.coerceAtLeast(0)
    val selectedChip = chips.getOrNull(selectedIndex)
    val shownChip = chips.firstOrNull { it.isPopupShown }

    LaunchedEffect(shownChip) {
        if (shownChip != null) {
            selectedChipId = shownChip.chipId
            popupAnchorChip = shownChip
            popupVisible = true
        } else if (popupAnchorChip != null) {
            popupVisible = false
            delay(220)
            popupAnchorChip = null
        }
    }

    // Auto-collapse the expanded card back to the compact pill once the user stops interacting
    // with transient events.
    LaunchedEffect(selectedChipId, popupVisible) {
        val chip = chips.firstOrNull { it.chipId == selectedChipId }
        if (popupVisible && chip != null && chip.isAutoCollapsing()) {
            delay(AutoCollapseTimeoutMs)
            chips.firstOrNull { it.chipId == selectedChipId }?.hidePopup()
        }
    }

    LaunchedEffect(chips) {
        onMediaControlPopupVisibilityChanged(
            chips.any { it.chipId == PopupChipId.MediaControl && it.isPopupShown }
        )
    }

    if (selectedChip == null) {
        return
    }

    fun selectRelative(direction: Int) {
        if (chips.size <= 1) return
        val newIndex = (selectedIndex + direction).mod(chips.size)
        val newChip = chips[newIndex]
        selectedChipId = newChip.chipId
        if (popupVisible) {
            newChip.showPopup()
        }
    }

    Box(
        modifier =
            modifier
                .padding(horizontal = 8.dp)
                .offset(x = cutoutSpec.horizontalOffset),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selectedChip.chipId,
            transitionSpec = {
                if (targetState == initialState) {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                } else {
                    val slideDirection =
                        if (
                            chips.indexOfFirst { it.chipId == targetState } >
                                chips.indexOfFirst { it.chipId == initialState }
                        ) {
                            1
                        } else {
                            -1
                        }
                    (slideInHorizontally(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        initialOffsetX = { fullWidth -> slideDirection * fullWidth / 2 },
                    ) + scaleIn(
                        initialScale = 0.86f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                    ) + fadeIn(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                    )) togetherWith
                        (slideOutHorizontally(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            targetOffsetX = { fullWidth -> -slideDirection * fullWidth / 3 },
                        ) + scaleOut(
                            targetScale = 0.9f,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        ) + fadeOut(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        ))
                }
            },
            label = "dynamic_island_chip",
        ) { chipId ->
            val chip = chips.firstOrNull { it.chipId == chipId } ?: return@AnimatedContent
            var horizontalDragPx by remember(chipId, chips.size) { mutableFloatStateOf(0f) }
            var verticalDragPx by remember(chipId, chips.size) { mutableFloatStateOf(0f) }
            val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
            val dismissThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }

            AnimatedVisibility(
                visible = !popupVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
            ) {
                StatusBarDynamicIslandChip(
                viewModel = chip,
                pageCount = chips.size,
                cutoutSpec = cutoutSpec,
                onChipBoundsChanged = { bounds -> anchorBounds = bounds },
                modifier =
                    Modifier
                        .pointerInput(chips.size, chip.chipId) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        horizontalDragPx <= -thresholdPx -> selectRelative(1)
                                        horizontalDragPx >= thresholdPx -> selectRelative(-1)
                                    }
                                    horizontalDragPx = 0f
                                },
                                onDragCancel = { horizontalDragPx = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    horizontalDragPx += dragAmount
                                    if (chips.size > 1 && abs(horizontalDragPx) > 8f) {
                                        change.consume()
                                    }
                                },
                            )
                        }
                        // Swiping up only minimizes the island back to the compact pill. It must
                        // never dismiss the event (which would hide the island until the event
                        // resets).
                        .pointerInput(chipId, chips.size) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (verticalDragPx <= -dismissThresholdPx) {
                                        chip.hidePopup()
                                    }
                                    verticalDragPx = 0f
                                },
                                onDragCancel = { verticalDragPx = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    verticalDragPx += dragAmount
                                    if (abs(verticalDragPx) > 8f) {
                                        change.consume()
                                    }
                                },
                            )
                        },
                onTap = {
                    if (chip.isPopupShown) chip.hidePopup() else chip.showPopup()
                },
                )
            }
        }

        popupAnchorChip?.let { anchoredChip ->
            StatusBarPopup(
                viewModel = anchoredChip,
                isVisible = popupVisible,
                chipBoundsInScreen = anchorBounds,
            )
        }
    }
}

/** Whether the expanded card should auto-collapse after [AutoCollapseTimeoutMs]. */
private fun PopupChipModel.Shown.isAutoCollapsing(): Boolean =
    when (val content = popupContent) {
        is PopupContentModel.Media -> !content.model.isPlaying
        is PopupContentModel.Charging -> true
        is PopupContentModel.Flashlight -> true
        is PopupContentModel.LiveScore -> true
        is PopupContentModel.ScreenRecord ->
            when (val model = content.model) {
                is ScreenRecordPopupModel.Starting -> true
                is ScreenRecordPopupModel.Recording -> false
            }
        is PopupContentModel.Alarm -> false
        is PopupContentModel.Call -> false
        is PopupContentModel.Stopwatch -> false
        is PopupContentModel.None -> false
    }

private const val AutoCollapseTimeoutMs = 5_000L

private fun androidx.compose.ui.layout.LayoutCoordinates.boundsInScreen(
    view: android.view.View
): Rect {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    return boundsInRoot().translate(Offset(location[0].toFloat(), location[1].toFloat()))
}