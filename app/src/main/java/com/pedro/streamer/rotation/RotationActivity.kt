/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.streamer.rotation

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.streamer.R
import com.pedro.streamer.utils.FilterMenu
import com.pedro.streamer.utils.setColor
import com.pedro.streamer.utils.toast


/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RotationActivity : AppCompatActivity(), OnTouchListener {

  private val cameraFragment = CameraFragment.getInstance()
  private val filterMenu: FilterMenu by lazy { FilterMenu(this) }
  private var currentVideoSource: MenuItem? = null
  private var currentAudioSource: MenuItem? = null
  private var currentOrientation: MenuItem? = null
  private var currentFilter: MenuItem? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.rotation_activity)
    supportFragmentManager.beginTransaction().add(R.id.container, cameraFragment).commit()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.rotation_menu, menu)
    val defaultVideoSource = menu.findItem(R.id.video_source_camera2)
    val defaultAudioSource = menu.findItem(R.id.audio_source_microphone)
    val defaultOrientation = menu.findItem(R.id.orientation_horizontal)
    val defaultFilter = menu.findItem(R.id.no_filter)
    currentVideoSource = updateMenuColor(currentVideoSource, defaultVideoSource)
    currentAudioSource = updateMenuColor(currentAudioSource, defaultAudioSource)
    currentOrientation = updateMenuColor(currentOrientation, defaultOrientation)
    currentFilter = updateMenuColor(currentFilter, defaultFilter)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    try {
      when (item.itemId) {
        R.id.video_source_camera1 -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          cameraFragment.genericStream.changeVideoSource(Camera1Source(applicationContext))
        }
        R.id.video_source_camera2 -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          cameraFragment.genericStream.changeVideoSource(Camera2Source(applicationContext))
        }
        R.id.video_source_camerax -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          cameraFragment.genericStream.changeVideoSource(CameraXSource(applicationContext))
        }
        R.id.video_source_bitmap -> {
          currentVideoSource = updateMenuColor(currentVideoSource, item)
          val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
          cameraFragment.genericStream.changeVideoSource(BitmapSource(bitmap))
        }
        R.id.audio_source_microphone -> {
          currentAudioSource = updateMenuColor(currentAudioSource, item)
          cameraFragment.genericStream.changeAudioSource(MicrophoneSource())
        }
        R.id.orientation_horizontal -> {
          currentOrientation = updateMenuColor(currentOrientation, item)
          cameraFragment.setOrientationMode(false)
        }
        R.id.orientation_vertical -> {
          currentOrientation = updateMenuColor(currentOrientation, item)
          cameraFragment.setOrientationMode(true)
        }
        else -> {
          val result = filterMenu.onOptionsItemSelected(item, cameraFragment.genericStream.getGlInterface())
          if (result) currentFilter = updateMenuColor(currentFilter, item)
          return result
        }
      }
    } catch (e: IllegalArgumentException) {
      toast("Change source error: ${e.message}")
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
    if (filterMenu.spriteGestureController.spriteTouched(view, motionEvent)) {
      filterMenu.spriteGestureController.moveSprite(view, motionEvent)
      filterMenu.spriteGestureController.scaleSprite(motionEvent)
      return true
    }
    return false
  }

  private fun updateMenuColor(currentItem: MenuItem?, item: MenuItem): MenuItem {
    currentItem?.setColor(this, R.color.black)
    item.setColor(this, R.color.appColor)
    return item
  }
}