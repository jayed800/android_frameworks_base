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

package com.android.systemui.statusbar.featurepods.charging.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon as StatusBarIcon
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.charging.shared.model.ChargingPopupModel
import com.android.systemui.statusbar.featurepods.popups.ui.compose.PopupSurface

private val PopupShape = RoundedCornerShape(32.dp)
private val ChargingAccent = Color(0xFF6DD96A)
private val BatteryTrack = Color.White.copy(alpha = 0.14f)

/** Expanded charging card surfaced in the dynamic island. */
@Composable
fun ChargingPopup(
    model: ChargingPopupModel,
    modifier: Modifier = Modifier,
) {
    PopupSurface(
        shape = PopupShape,
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBarIcon(
                    icon =
                        Icon.Resource(
                            resId = R.drawable.ic_dynamic_island_bolt,
                            contentDescription =
                                ContentDescription.Resource(
                                    R.string.status_bar_charging_island_content_description
                                ),
                        ),
                    modifier = Modifier.size(24.dp),
                    tint = ChargingAccent,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text =
                            stringResource(
                                if (model.isWireless) {
                                    R.string.dynamic_island_wireless_charging_title
                                } else {
                                    R.string.dynamic_island_charging_title
                                }
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.dynamic_island_charging_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }

            ChargingBatteryGraphic(level = model.level)
        }
    }
}

/** Animated battery with an iOS-style segmented fill and a pulsing bolt. */
@Composable
private fun ChargingBatteryGraphic(
    level: Int,
    modifier: Modifier = Modifier,
) {
    val fillFraction = (level / 100f).coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "charging_pulse")
    val pulse by
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "charging_pulse",
        )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Battery capsule with terminal
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(12.dp)
                        .height(6.dp)
                        .background(color = Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(2.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(46.dp)
                        .background(color = BatteryTrack, shape = RoundedCornerShape(14.dp))
                        .padding(4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fillFraction)
                            .height(38.dp)
                            .background(
                                color = ChargingAccent.copy(alpha = pulse),
                                shape = RoundedCornerShape(10.dp),
                            ),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$level%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = ChargingAccent,
            )
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .alpha(pulse)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse },
                contentAlignment = Alignment.Center,
            ) {
                StatusBarIcon(
                    icon =
                        Icon.Resource(
                            resId = R.drawable.ic_dynamic_island_bolt,
                            contentDescription = null,
                        ),
                    modifier = Modifier.size(16.dp),
                    tint = ChargingAccent,
                )
            }
        }
    }
}