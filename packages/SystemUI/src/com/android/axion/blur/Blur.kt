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

package com.android.axion.blur

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Fallback placeholder for the Axion blur API. The real implementation lives in
 * the Axion ROM framework; this shim keeps Dynamic Island popups working by
 * falling back to a translucent overlay when blur is not available.
 */
class AxBlurBackgroundRenderer(private val view: View) {
    fun onAttachedToWindow() {}

    fun onDetachedFromWindow() {}

    fun onVisibilityAggregated(isVisible: Boolean) {}

    fun verifyDrawable(who: Drawable): Boolean = false

    fun drawBackgroundWithOverlayColor(
        canvas: Canvas,
        bgDrawable: Drawable,
        overlayColor: Int,
    ): Boolean = false
}

object AxBlurColors {
    fun surfaceLightTint(context: Context): Int = 0xCC000000.toInt()
}
