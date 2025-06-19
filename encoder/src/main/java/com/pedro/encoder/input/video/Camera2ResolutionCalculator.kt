/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.encoder.input.video

import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
object Camera2ResolutionCalculator {

  fun getOptimalResolution(actualResolution: Size, resolutionsSupported: Array<Size>): Size {
    val resolutionsSupportedFiltered = resolutionsSupported.filter { isValidResolution(it) }
    val resolution = if (actualResolution.width < actualResolution.height) Size(actualResolution.height, actualResolution.width) else actualResolution
    return if (resolutionsSupportedFiltered.find { it == resolution } != null) resolution
    else {
      val actualAspectRatio = resolution.width.toFloat() / resolution.height.toFloat()
      val validResolutions = resolutionsSupportedFiltered.filter { it.width.toFloat() / it.height.toFloat() == actualAspectRatio }
      if (validResolutions.isNotEmpty()) {
        val resolutions = validResolutions.toMutableList()
        resolutions.add(resolution)
        val resolutionsSorted = resolutions.sortedByDescending { it.height }
        val index = resolutionsSorted.indexOf(resolution)
        return resolutionsSorted[if (index > 0) index - 1 else 0]
      } else {
        actualResolution
      }
    }
  }

  private fun isValidResolution(size: Size): Boolean {
    if (Build.MODEL == "Pixel 8a" && Build.MANUFACTURER == "Google") {
      //This resolution produce bad quality and should be removed. The closest resolution will be use
      if (size.width == 1280 && size.height == 720) return false
    }
    return true
  }
}