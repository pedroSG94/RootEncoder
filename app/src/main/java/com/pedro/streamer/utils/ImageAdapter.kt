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

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.pedro.streamer.R

class ImageAdapter(private val links: List<ActivityLink>) : BaseAdapter() {
  override fun getCount(): Int {
    return links.size
  }

  override fun getItem(position: Int): ActivityLink {
    return links[position]
  }

  override fun getItemId(position: Int): Long {
    return position.toLong()
  }

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    var myConvertView = convertView
    val button: TextView
    val resources = parent.resources
    val fontSize = resources.getDimension(R.dimen.menu_font)
    val paddingH = resources.getDimensionPixelSize(R.dimen.grid_2)
    val paddingV = resources.getDimensionPixelSize(R.dimen.grid_5)
    if (myConvertView == null) {
      button = TextView(parent.context)
      button.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
      button.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null))
      button.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.appColor, null))
      button.layoutParams = AbsListView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      button.setPadding(paddingH, paddingV, paddingH, paddingV)
      button.gravity = Gravity.CENTER
      myConvertView = button
    } else {
      button = myConvertView as TextView
    }
    button.text = links[position].label
    return myConvertView
  }
}
