/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.featurepods.popups.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.featurepods.alarm.ui.viewmodel.AlarmPopupChipViewModel
import com.android.systemui.statusbar.featurepods.calls.ui.viewmodel.CallPopupChipViewModel
import com.android.systemui.statusbar.featurepods.charging.ui.viewmodel.ChargingPopupChipViewModel
import com.android.systemui.statusbar.featurepods.flashlight.ui.viewmodel.FlashlightPopupChipViewModel
import com.android.systemui.statusbar.featurepods.livescore.ui.viewmodel.LiveScorePopupChipViewModel
import com.android.systemui.statusbar.featurepods.av.ui.viewmodel.AvControlsChipViewModel
import com.android.systemui.statusbar.featurepods.media.ui.viewmodel.MediaControlChipViewModel
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.screenrecord.ui.viewmodel.ScreenRecordPopupChipViewModel
import com.android.systemui.statusbar.featurepods.sharescreen.ui.viewmodel.ShareScreenPrivacyIndicatorViewModel
import com.android.systemui.statusbar.featurepods.stopwatch.ui.viewmodel.StopwatchPopupChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * View model deciding which system process chips to show in the status bar. Emits a list of
 * PopupChipModels.
 */
class StatusBarPopupChipsViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    mediaControlChipFactory: MediaControlChipViewModel.Factory,
    screenRecordChipFactory: ScreenRecordPopupChipViewModel.Factory,
    liveScoreChipFactory: LiveScorePopupChipViewModel.Factory,
    flashlightChipFactory: FlashlightPopupChipViewModel.Factory,
    chargingChipFactory: ChargingPopupChipViewModel.Factory,
    callChipFactory: CallPopupChipViewModel.Factory,
    stopwatchChipFactory: StopwatchPopupChipViewModel.Factory,
    alarmChipFactory: AlarmPopupChipViewModel.Factory,
    avControlsChipFactory: AvControlsChipViewModel.Factory,
    shareScreenPrivacyIndicatorFactory: ShareScreenPrivacyIndicatorViewModel.Factory,
) : ExclusiveActivatable() {

    private val mediaControlChip by lazy { mediaControlChipFactory.create() }
    private val screenRecordChip by lazy { screenRecordChipFactory.create() }
    private val liveScoreChip by lazy { liveScoreChipFactory.create() }
    private val flashlightChip by lazy { flashlightChipFactory.create() }
    private val chargingChip by lazy { chargingChipFactory.create() }
    private val callChip by lazy { callChipFactory.create() }
    private val stopwatchChip by lazy { stopwatchChipFactory.create() }
    private val alarmChip by lazy { alarmChipFactory.create() }
    private val avControlsChip by lazy { avControlsChipFactory.create() }
    private val shareScreenPrivacyIndicator by lazy { shareScreenPrivacyIndicatorFactory.create() }
    private var isDynamicIslandEnabled by mutableStateOf(readDynamicIslandEnabled())
    private val dynamicIslandObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isDynamicIslandEnabled = readDynamicIslandEnabled()
                if (!isDynamicIslandEnabled) {
                    currentShownPopupChipId = null
                }
            }
        }

    private var isOnLockscreen by mutableStateOf(false)
    /** The ID of the current chip that is showing its popup, or `null` if no chip is shown. */
    private var currentShownPopupChipId by mutableStateOf<PopupChipId?>(null)

    /**
     * Chips the user swiped away. Dismissed chips are hidden until their underlying event goes
     * inactive and becomes active again.
     */
    private var dismissedChipIds by mutableStateOf<Set<PopupChipId>>(emptySet())

    private val incomingPopupChipBundle: PopupChipBundle by derivedStateOf {
        PopupChipBundle(
            media = mediaControlChip.chip,
            screenRecord = screenRecordChip.chip,
            liveScore = liveScoreChip.chip,
            flashlight = flashlightChip.chip,
            charging = chargingChip.chip,
            call = callChip.chip,
            stopwatch = stopwatchChip.chip,
            alarm = alarmChip.chip,
            privacy = avControlsChip.chip,
            shareScreen = shareScreenPrivacyIndicator.chip,
        )
    }

    val shownPopupChips: List<PopupChipModel.Shown> by derivedStateOf {
        if (!isDynamicIslandEnabled || isOnLockscreen) {
            return@derivedStateOf emptyList()
        }

        val bundle = incomingPopupChipBundle
        val candidateChips =
            if (StatusBarPopupChips.isEnabled) {
                listOfNotNull(
                    bundle.media,
                    bundle.screenRecord,
                    bundle.liveScore,
                    bundle.stopwatch,
                    bundle.alarm,
                    bundle.flashlight,
                    bundle.charging,
                    bundle.call,
                    bundle.privacy,
                    bundle.shareScreen,
                )
            } else {
                // Keep media ticker available even when popup chips modernization is disabled.
                listOfNotNull(
                    bundle.media,
                    bundle.screenRecord,
                    bundle.liveScore,
                    bundle.stopwatch,
                    bundle.alarm,
                    bundle.flashlight,
                    bundle.charging,
                    bundle.call,
                )
            }

        candidateChips
            .filterIsInstance<PopupChipModel.Shown>()
            .filterNot { it.chipId in dismissedChipIds }
            .sortedBy { it.chipId.priority() }
            .map { chip ->
                chip.copy(
                    isPopupShown = chip.chipId == currentShownPopupChipId,
                    showPopup = { currentShownPopupChipId = chip.chipId },
                    hidePopup = { currentShownPopupChipId = null },
                    dismiss = {
                        currentShownPopupChipId = null
                        dismissedChipIds = dismissedChipIds + chip.chipId
                    },
                )
            }
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND
                ),
                false,
                dynamicIslandObserver,
                UserHandle.USER_ALL,
            )
            dynamicIslandObserver.onChange(false)
            launch {
                keyguardTransitionInteractor.isFinishedIn(KeyguardState.LOCKSCREEN)
                    .collectLatest { isOnLockscreen = it }
            }
            // Clear the dismiss for a chip as soon as its event goes inactive and starts again,
            // so it can appear in the island once more.
            launch {
                snapshotFlow { incomingPopupChipBundle }
                    .map { bundle ->
                        bundle
                            .chipsIndexed()
                            .associate { (chipId, chip) ->
                                chipId to (chip is PopupChipModel.Shown)
                            }
                    }
                    .distinctUntilChanged()
                    .collect { shownByChip ->
                        shownByChip.forEach { (chipId, isShown) ->
                            if (!isShown && dismissedChipIds.contains(chipId)) {
                                dismissedChipIds = dismissedChipIds - chipId
                            }
                        }
                    }
            }
            launch { avControlsChip.activate() }
            launch { mediaControlChip.activate() }
            launch { screenRecordChip.activate() }
            launch { liveScoreChip.activate() }
            launch { flashlightChip.activate() }
            launch { chargingChip.activate() }
            launch { callChip.activate() }
            launch { stopwatchChip.activate() }
            launch { alarmChip.activate() }
            launch { shareScreenPrivacyIndicator.activate() }
            try {
                awaitCancellation()
            } finally {
                context.contentResolver.unregisterContentObserver(dynamicIslandObserver)
            }
        }
    }

    private data class PopupChipBundle(
        val media: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.MediaControl),
        val screenRecord: PopupChipModel =
            PopupChipModel.Hidden(chipId = PopupChipId.ScreenRecord),
        val liveScore: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.LiveScore),
        val flashlight: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Flashlight),
        val charging: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Charging),
        val call: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Call),
        val stopwatch: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Stopwatch),
        val alarm: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.Alarm),
        val privacy: PopupChipModel =
            PopupChipModel.Hidden(chipId = PopupChipId.AvControlsIndicator),
        val shareScreen: PopupChipModel =
            PopupChipModel.Hidden(chipId = PopupChipId.ShareScreenPrivacyIndicator),
    ) {
        fun chipsIndexed(): List<Pair<PopupChipId, PopupChipModel>> =
            listOfNotNull(
                PopupChipId.MediaControl to media,
                PopupChipId.ScreenRecord to screenRecord,
                PopupChipId.LiveScore to liveScore,
                PopupChipId.Flashlight to flashlight,
                PopupChipId.Charging to charging,
                PopupChipId.Call to call,
                PopupChipId.Stopwatch to stopwatch,
                PopupChipId.Alarm to alarm,
                PopupChipId.AvControlsIndicator to privacy,
                PopupChipId.ShareScreenPrivacyIndicator to shareScreen,
            )
    }

    private fun readDynamicIslandEnabled(): Boolean {
        return Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND,
            0,
            UserHandle.USER_CURRENT,
        ) != 0
    }

    @AssistedFactory
    interface Factory {
        fun create(): StatusBarPopupChipsViewModel
    }
}

/**
 * Priority used for ordering chips in the island. Lower values take precedence, so more
 * time-critical events (calls, media) break in first.
 */
private fun PopupChipId.priority(): Int =
    when (this) {
        PopupChipId.Call -> 0
        PopupChipId.MediaControl -> 1
        PopupChipId.Charging -> 2
        PopupChipId.Alarm -> 3
        PopupChipId.Stopwatch -> 4
        PopupChipId.Flashlight -> 5
        PopupChipId.ScreenRecord -> 6
        PopupChipId.LiveScore -> 7
        PopupChipId.AvControlsIndicator -> 8
        PopupChipId.ShareScreenPrivacyIndicator -> 9
    }