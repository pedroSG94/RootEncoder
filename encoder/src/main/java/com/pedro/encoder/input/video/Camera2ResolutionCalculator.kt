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
    return if (resolutionsSupported.find { it == actualResolution } != null) actualResolution
    else {
      val actualAspectRatio = actualResolution.width.toFloat() / actualResolution.height.toFloat()
      val validResolutions = resolutionsSupported.filter { it.width.toFloat() / it.height.toFloat() == actualAspectRatio }
      if (validResolutions.isNotEmpty()) {
        val resolutions = validResolutions.toMutableList()
        resolutions.add(actualResolution)
        val resolutionsSorted = resolutions.sortedByDescending { it.height }
        val index = resolutionsSorted.indexOf(actualResolution)
        if (index > 0) {
          return resolutionsSorted[index - 1]
        } else return resolutionsSorted[index + 1]
      } else {
        actualResolution
      }
    }
  }
}