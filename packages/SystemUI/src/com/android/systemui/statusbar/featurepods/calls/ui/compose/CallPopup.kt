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

package com.android.systemui.statusbar.featurepods.calls.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon as StatusBarIcon
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.calls.shared.model.CallPopupModel
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupActionModel
import com.android.systemui.statusbar.featurepods.popups.ui.compose.PopupActionChips
import com.android.systemui.statusbar.featurepods.popups.ui.compose.PopupSurface
import kotlinx.coroutines.delay
import java.util.Locale

private val PopupShape = RoundedCornerShape(32.dp)
private val CallAccent = Color(0xFF30D158)

/** Expanded ongoing call card surfaced in the dynamic island. */
@Composable
fun CallPopup(
    model: CallPopupModel,
    modifier: Modifier = Modifier,
) {
    PopupSurface(
        shape = PopupShape,
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(color = CallAccent.copy(alpha = 0.18f), shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusBarIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_call,
                                contentDescription = null,
                            ),
                        modifier = Modifier.size(24.dp),
                        tint = CallAccent,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = model.callerName ?: model.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = model.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (model.startTimeMs > 0) {
                    CallDurationText(startTimeMs = model.startTimeMs)
                }
            }

            PopupActionChips(
                actions =
                    model.openApp?.let { openApp ->
                        listOf(
                            PopupActionModel(
                                label = stringResource(R.string.dynamic_island_open_action),
                                onClick = openApp,
                                emphasized = true,
                            )
                        )
                    }.orEmpty(),
                accent = CallAccent,
            )
        }
    }
}

/** Displays the running call duration as mm:ss, updating every second. */
@Composable
private fun CallDurationText(
    startTimeMs: Long,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startTimeMs) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Text(
        text = formatDuration(elapsedSeconds),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = CallAccent,
        modifier = modifier,
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}