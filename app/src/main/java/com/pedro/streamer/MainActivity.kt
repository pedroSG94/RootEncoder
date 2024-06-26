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
package com.pedro.streamer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.GridView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.streamer.oldapi.OldApiActivity
import com.pedro.streamer.file.FromFileActivity
import com.pedro.streamer.rotation.RotationActivity
import com.pedro.streamer.screen.ScreenActivity
import com.pedro.streamer.utils.ActivityLink
import com.pedro.streamer.utils.ImageAdapter
import com.pedro.streamer.utils.toast

class MainActivity : AppCompatActivity() {

  private lateinit var list: GridView
  private val activities: MutableList<ActivityLink> = mutableListOf()

  private val permissions = mutableListOf(
    Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
  ).apply {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
      this.add(Manifest.permission.POST_NOTIFICATIONS)
    }
  }.toTypedArray()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    transitionAnim(true)
    val tvVersion = findViewById<TextView>(R.id.tv_version)
    tvVersion.text = getString(R.string.version, BuildConfig.VERSION_NAME)
    list = findViewById(R.id.list)
    createList()
    setListAdapter(activities)
    requestPermissions()
  }

  @Suppress("DEPRECATION")
  private fun transitionAnim(isOpen: Boolean) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val type = if (isOpen) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE
      overrideActivityTransition(type, R.anim.slide_in, R.anim.slide_out)
    } else {
      overridePendingTransition(R.anim.slide_in, R.anim.slide_out)
    }
  }

  private fun requestPermissions() {
    if (!hasPermissions(this)) {
      ActivityCompat.requestPermissions(this, permissions, 1)
    }
  }

  @SuppressLint("NewApi")
  private fun createList() {
    activities.add(
      ActivityLink(
        Intent(this, OldApiActivity::class.java),
        getString(R.string.old_api), VERSION_CODES.JELLY_BEAN
      )
    )
    activities.add(
      ActivityLink(
        Intent(this, FromFileActivity::class.java),
        getString(R.string.from_file), VERSION_CODES.JELLY_BEAN_MR2
      )
    )
    activities.add(
      ActivityLink(
        Intent(this, ScreenActivity::class.java),
        getString(R.string.display), VERSION_CODES.LOLLIPOP
      )
    )
    activities.add(
      ActivityLink(
        Intent(this, RotationActivity::class.java),
        getString(R.string.rotation_rtmp), VERSION_CODES.LOLLIPOP
      )
    )
  }

  private fun setListAdapter(activities: List<ActivityLink>) {
    list.adapter = ImageAdapter(activities)
    list.onItemClickListener =
      OnItemClickListener { _, _, position, _ ->
        if (hasPermissions(this)) {
          val link = activities[position]
          val minSdk = link.minSdk
          if (Build.VERSION.SDK_INT >= minSdk) {
            startActivity(link.intent)
            transitionAnim(false)
          } else {
            showMinSdkError(minSdk)
          }
        } else {
          showPermissionsErrorAndRequest()
        }
      }
  }

  private fun showMinSdkError(minSdk: Int) {
    val named: String = when (minSdk) {
      VERSION_CODES.JELLY_BEAN_MR2 -> "JELLY_BEAN_MR2"
      VERSION_CODES.LOLLIPOP -> "LOLLIPOP"
      else -> "JELLY_BEAN"
    }
    toast("You need min Android $named (API $minSdk)")
  }

  private fun showPermissionsErrorAndRequest() {
    toast("You need permissions before")
    requestPermissions()
  }

  private fun hasPermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
      for (permission in permissions) {
        if (ActivityCompat.checkSelfPermission(context, permission)
          != PackageManager.PERMISSION_GRANTED
        ) {
          return false
        }
      }
    }
    return true
  }
}