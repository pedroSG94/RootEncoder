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

package com.pedro.streamer.utils

import android.app.Activity
import android.app.Service
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment


/**
 * Created by pedro on 1/3/24.
 */

fun Activity.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, message, duration).show()
}

fun Fragment.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(requireContext(), message, duration).show()
}

fun Service.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, message, duration).show()
}

fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, message, duration).show()
}

fun MenuItem.setColor(context: Context, @ColorRes color: Int) {
  val spannableString = SpannableString(title.toString())
  spannableString.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, color)), 0, spannableString.length, 0)
  title = spannableString
}

@Suppress("DEPRECATION")
fun Drawable.setColorFilter(@ColorInt color: Int) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
  } else {
    setColorFilter(color, PorterDuff.Mode.SRC_IN)
  }
}