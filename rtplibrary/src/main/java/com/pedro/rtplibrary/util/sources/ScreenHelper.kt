package com.pedro.rtplibrary.util.sources

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.CameraHelper

/**
 * Created by pedro on 31/3/22.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenHelper(private val context: Context) {

  fun getScreenDpi(): Int {
    return (context.resources.displayMetrics.density * 160f).toInt()
  }

  fun getScreenResolution(): Size {
    val displayMetrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager.defaultDisplay.getRealMetrics(displayMetrics)
    val screenWidth = if (CameraHelper.isPortrait(context)) {
      displayMetrics.widthPixels
    } else displayMetrics.heightPixels
    val screenHeight = if (CameraHelper.isPortrait(context)) {
      displayMetrics.heightPixels
    } else displayMetrics.widthPixels
    return Size(screenWidth, screenHeight)
  }

  fun calculateMediaProjectionResolution(streamWidth: Int, streamHeight: Int): Size {
    val resolution = getScreenResolution()
    val factorStream = if (streamWidth >= streamHeight) {
      streamWidth.toFloat() / streamHeight.toFloat()
    } else {
      streamHeight.toFloat() / streamWidth.toFloat()
    }
    return Size((resolution.width * factorStream).toInt(), (resolution.height * factorStream).toInt())
  }
}