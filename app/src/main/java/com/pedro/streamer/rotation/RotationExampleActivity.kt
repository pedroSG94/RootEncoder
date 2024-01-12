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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.library.util.sources.VideoManager
import com.pedro.library.util.sources.audio.InternalSource
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.library.util.sources.video.ScreenSource
import com.pedro.streamer.R
import com.pedro.streamer.databinding.ActivityExampleBinding
import com.pedro.streamer.utils.PathUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RotationExampleActivity: AppCompatActivity(), SurfaceHolder.Callback {

  private val REQUEST_CODE_SCREEN_VIDEO = 1
  private val REQUEST_CODE_INTERNAL_AUDIO = 2
  private var askingMediaProjection = false
  private lateinit var binding: ActivityExampleBinding
  private var service: StreamService? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    binding = ActivityExampleBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.etRtpUrl.setHint(R.string.hint_rtmp)
    binding.surfaceView.holder.addCallback(this)
    StreamService.observer.observe(this) {
      if (it != null) {
        service = if (!it.prepare()) {
          Toast.makeText(this, "Prepare method failed", Toast.LENGTH_SHORT).show()
          null
        } else it
      }
      startPreview()
    }

    binding.bStartStop.setOnClickListener {
      if (service?.isStreaming() != true) {
        service?.startStream(binding.etRtpUrl.text.toString())
        binding.bStartStop.setText(R.string.stop_button)
      } else {
        service?.stopStream()
        binding.bStartStop.setText(R.string.start_button)
      }
    }
    binding.bRecord.setOnClickListener {
      if (service?.isRecording() != true) {
        val folder = PathUtils.getRecordPath()
        if (!folder.exists()) folder.mkdir()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = sdf.format(Date())
        service?.startRecord("${folder.absolutePath}/$fileName.mp4")
        binding.bRecord.setText(R.string.stop_record)
      } else {
        service?.stopRecord()
        binding.bRecord.setText(R.string.start_record)
      }
    }
    binding.switchCamera.setOnClickListener {
      service?.switchCamera()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.rotation_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.video_source_camera1 -> {
        service?.changeVideoSource(Camera1Source(applicationContext))
      }
      R.id.video_source_camera2 -> {
        service?.changeVideoSource(Camera2Source(applicationContext))
      }
      R.id.video_source_screen -> {
        askingMediaProjection = true
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE_SCREEN_VIDEO)
      }
      R.id.audio_source_microphone -> {
        service?.changeAudioSource(MicrophoneSource())
      }
      R.id.audio_source_internal -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          askingMediaProjection = true
          val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
          val intent = mediaProjectionManager.createScreenCaptureIntent()
          startActivityForResult(intent, REQUEST_CODE_INTERNAL_AUDIO)
        } else {
          Toast.makeText(this, "Android 10+ required", Toast.LENGTH_SHORT).show()
        }
      }
      else -> return false
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (data != null && resultCode == RESULT_OK) {
      val mediaProjectionManager = applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
      if (requestCode == REQUEST_CODE_SCREEN_VIDEO) {
        askingMediaProjection = false
        service?.changeVideoSource(ScreenSource(applicationContext, mediaProjection))
      } else if (requestCode == REQUEST_CODE_INTERNAL_AUDIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        askingMediaProjection = false
        service?.changeAudioSource(InternalSource(mediaProjection))
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (!isMyServiceRunning(StreamService::class.java)) {
      val intent = Intent(applicationContext, StreamService::class.java)
      startService(intent)
    }
    if (service?.isStreaming() == true) {
      binding.bStartStop.setText(R.string.stop_button)
    } else {
      binding.bStartStop.setText(R.string.start_button)
    }
    if (service?.isRecording() == true) {
      binding.bRecord.setText(R.string.stop_record)
    } else {
      binding.bRecord.setText(R.string.start_record)
    }
  }

  override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations && !askingMediaProjection) { //stop if no rotation activity
      if (service?.isStreaming() == true) service?.stopStream()
      if (service?.isOnPreview() == true) service?.stopPreview()
      service = null
      stopService(Intent(applicationContext, StreamService::class.java))
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    startPreview()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    if (service?.isOnPreview() == true) service?.stopPreview()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {

  }

  private fun startPreview() {
    //check if onPreview and if surface is valid
    if (service?.isOnPreview() != true && binding.surfaceView.holder.surface.isValid) {
      service?.startPreview(binding.surfaceView)
    }
  }

  @Suppress("DEPRECATION")
  private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.name == service.service.className) {
        return true
      }
    }
    return false
  }
}