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

package com.android.systemui.statusbar.featurepods.calls.shared.model

/** Popup content for the ongoing call page in the dynamic island. */
data class CallPopupModel(
    /** The caller's display name, falling back to the calling app's name. */
    val callerName: String?,
    /** The name of the app hosting the call (e.g. Phone). */
    val appName: String,
    /**
     * The wall-clock time ([System.currentTimeMillis]) the call started. Zero if unknown, in which
     * case no duration is shown.
     */
    val startTimeMs: Long,
    /** Opens the app hosting the call. */
    val openApp: (() -> Unit)?,
)