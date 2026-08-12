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

package com.android.systemui.statusbar.featurepods.calls.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.calls.shared.model.CallPopupModel
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.CALLS
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.phone.ongoingcall.domain.interactor.OngoingCallInteractor
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** ViewModel backing the ongoing call page inside the dynamic island. */
class CallPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val ongoingCallInteractor: OngoingCallInteractor,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("CallPopupChipViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.Call),
            source =
                ongoingCallInteractor.ongoingCallState.map(::toPopupChipModel).combine(
                    observeDynamicIslandFeatureEnabled(context, CALLS)
                ) { model, enabled ->
                    if (enabled) model else PopupChipModel.Hidden(PopupChipId.Call)
                },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(callState: OngoingCallModel): PopupChipModel {
        if (callState !is OngoingCallModel.InCall) {
            return PopupChipModel.Hidden(PopupChipId.Call)
        }

        val promoted = callState.promotedContent
        val callerName =
            promoted?.privateVersion?.title
                ?: promoted?.publicVersion?.title
                ?: callState.appName
        val model =
            CallPopupModel(
                callerName = callerName?.toString(),
                appName = callState.appName,
                startTimeMs = callState.startTimeMs,
                openApp = callState.intent?.let { intent -> { launchPendingIntent(intent) } },
            )
        val contentDescription =
            ContentDescription.Resource(R.string.status_bar_call_island_content_description)

        return PopupChipModel.Shown(
            chipId = PopupChipId.Call,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_call,
                                contentDescription = contentDescription,
                            ),
                    )
                ),
            chipText = callerName?.toString() ?: callState.appName,
            colors = ColorsModel.DynamicIslandCall,
            contentDescription = contentDescription.loadContentDescription(context),
            popupContent = PopupContentModel.Call(model),
        )
    }

    private fun launchPendingIntent(intent: android.app.PendingIntent) {
        try {
            intent.send(context, 0, Intent())
        } catch (_: android.app.PendingIntent.CanceledException) {
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): CallPopupChipViewModel
    }
}