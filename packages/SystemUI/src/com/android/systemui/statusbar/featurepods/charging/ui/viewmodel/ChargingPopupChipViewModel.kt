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

package com.android.systemui.statusbar.featurepods.charging.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.charging.shared.model.ChargingPopupModel
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.CHARGING
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.policy.BatteryController
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** ViewModel backing the charging page inside the dynamic island. */
class ChargingPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val batteryController: BatteryController,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("ChargingPopupChipViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.Charging),
            source =
                callbackFlow {
                        var currentLevel = 0
                        var isPluggedIn = false
                        var isCharging = false
                        var isWireless = false

                        fun emitState() {
                            trySend(
                                ChargingState(
                                    level = currentLevel,
                                    pluggedIn = isPluggedIn,
                                    charging = isCharging,
                                    isWireless = isWireless,
                                )
                            )
                        }

                        val callback =
                            object : BatteryController.BatteryStateChangeCallback {
                                override fun onBatteryLevelChanged(
                                    level: Int,
                                    pluggedIn: Boolean,
                                    charging: Boolean,
                                ) {
                                    currentLevel = level
                                    isPluggedIn = pluggedIn
                                    isCharging = charging
                                    emitState()
                                }

                                override fun onWirelessChargingChanged(
                                    isWirelessCharging: Boolean
                                ) {
                                    isWireless = isWirelessCharging
                                    emitState()
                                }
                            }

                        batteryController.addCallback(callback)
                        emitState()
                        awaitClose { batteryController.removeCallback(callback) }
                    }
                    .distinctUntilChanged()
                    .map(::toPopupChipModel)
                    .combine(
                        observeDynamicIslandFeatureEnabled(context, CHARGING)
                    ) { model, enabled ->
                        if (enabled) model else PopupChipModel.Hidden(PopupChipId.Charging)
                    },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(state: ChargingState): PopupChipModel {
        if (!state.charging) {
            return PopupChipModel.Hidden(PopupChipId.Charging)
        }

        val level = state.level.coerceIn(0, 100)
        val model = ChargingPopupModel(level = level, isWireless = state.isWireless)
        val contentDescription =
            ContentDescription.Resource(R.string.status_bar_charging_island_content_description)

        return PopupChipModel.Shown(
            chipId = PopupChipId.Charging,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_dynamic_island_bolt,
                                contentDescription = contentDescription,
                            ),
                    )
                ),
            chipText = context.getString(R.string.charging_island_percent_format, level),
            colors = ColorsModel.DynamicIsland,
            contentDescription = contentDescription.loadContentDescription(context),
            popupContent = PopupContentModel.Charging(model),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): ChargingPopupChipViewModel
    }
}

private data class ChargingState(
    val level: Int,
    val pluggedIn: Boolean,
    val charging: Boolean,
    val isWireless: Boolean,
)