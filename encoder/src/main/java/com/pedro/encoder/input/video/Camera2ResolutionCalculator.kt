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