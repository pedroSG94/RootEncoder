package com.pedro.encoder.utils

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class ViewPresentation(
  context: Context, display: Display
): Presentation(context, display) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window?.setFormat(PixelFormat.TRANSLUCENT)
  }

  override fun setContentView(view: View) {
    (view.parent as? ViewGroup)?.removeView(view)
    super.setContentView(view)
  }

  override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
    (view.parent as? ViewGroup)?.removeView(view)
    super.setContentView(view, params)
  }
}