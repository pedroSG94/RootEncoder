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
package com.pedro.streamer.screen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.library.base.recording.RecordController
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.streamer.R
import com.pedro.streamer.utils.fitAppPadding
import com.pedro.streamer.utils.toast
import com.pedro.streamer.utils.updateMenuColor

/**
 * Example code to stream the device screen.
 * Necessary API 21+
 *
 * More documentation see:
 * [com.pedro.library.base.DisplayBase]
 * Support RTMP, RTSP and SRT with commons features
 * [com.pedro.library.generic.GenericDisplay]
 * Support RTSP with all RTSP features
 * [com.pedro.library.rtsp.RtspDisplay]
 * Support RTMP with all RTMP features
 * [com.pedro.library.rtmp.RtmpDisplay]
 * Support SRT with all SRT features
 * [com.pedro.library.srt.SrtDisplay]
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class ScreenActivity : AppCompatActivity(), ConnectChecker {

  enum class Action {
    STREAM, RECORD, NONE
  }

  private lateinit var button: ImageView
  private lateinit var bRecord: ImageView
  private lateinit var etUrl: EditText
  private var currentAudioSource: MenuItem? = null
  private var action = Action.NONE

  private val activityResultContract = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val data = result.data
    if (data != null && result.resultCode == RESULT_OK) {
      val screenService = ScreenService.INSTANCE
      if (screenService != null) {
        if (screenService.prepareStream(result.resultCode, data)) {
          when (action) {
            Action.STREAM -> startStream()
            Action.RECORD -> toggleRecord()
            else -> {}
          }
        } else {
          toast("Prepare stream failed")
        }
      }
    } else {
      toast("No permissions available")
      button.setImageResource(R.drawable.stream_icon)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_display)
    fitAppPadding()
    button = findViewById(R.id.b_start_stop)
    etUrl = findViewById(R.id.et_rtp_url)
    bRecord = findViewById(R.id.b_record)
    val screenService = ScreenService.INSTANCE
    //No streaming/recording start service
    if (screenService == null) {
      startService(Intent(this, ScreenService::class.java))
    }
    if (screenService != null && screenService.isStreaming()) {
      button.setImageResource(R.drawable.stream_stop_icon)
    } else {
      button.setImageResource(R.drawable.stream_icon)
    }
    if (screenService != null && screenService.isRecording()) {
      bRecord.setImageResource(R.drawable.stop_icon)
    } else {
      bRecord.setImageResource(R.drawable.record_icon)
    }
    button.setOnClickListener {
      val service = ScreenService.INSTANCE
      if (service != null) {
        service.setCallback(this)
        if (!service.isStreaming() && !service.isRecording()) {
          action = Action.STREAM
          activityResultContract.launch(service.sendIntent())
        } else if (!service.isStreaming()) {
          startStream()
        } else {
          stopStream()
        }
      }
    }
    bRecord.setOnClickListener {
      val service = ScreenService.INSTANCE
      if (service != null) {
        service.setCallback(this)
        if (!service.isStreaming() && !service.isRecording()) {
          action = Action.RECORD
          activityResultContract.launch(service.sendIntent())
        } else toggleRecord()
      }
    }
  }

  private fun startStream() {
    button.setImageResource(R.drawable.stream_stop_icon)
    val endpoint = etUrl.text.toString()
    ScreenService.INSTANCE?.startStream(endpoint)
  }

  private fun stopStream() {
    button.setImageResource(R.drawable.stream_icon)
    ScreenService.INSTANCE?.stopStream()
  }

  private fun toggleRecord() {
    ScreenService.INSTANCE?.toggleRecord { state ->
      when (state) {
        RecordController.Status.STARTED -> {
          bRecord.setImageResource(R.drawable.pause_icon)
        }
        RecordController.Status.STOPPED -> {
          bRecord.setImageResource(R.drawable.record_icon)
        }
        RecordController.Status.RECORDING -> {
          bRecord.setImageResource(R.drawable.stop_icon)
        }
        else -> {}
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.screen_menu, menu)
    val defaultAudioSource = when (ScreenService.INSTANCE?.getCurrentAudioSource()) {
      is MicrophoneSource -> menu.findItem(R.id.audio_source_microphone)
      is InternalAudioSource -> menu.findItem(R.id.audio_source_internal)
      is MixAudioSource -> menu.findItem(R.id.audio_source_mix)
      else -> menu.findItem(R.id.audio_source_microphone)
    }
    currentAudioSource = defaultAudioSource.updateMenuColor(this, currentAudioSource)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    try {
      when (item.itemId) {
        R.id.audio_source_microphone, R.id.audio_source_internal, R.id.audio_source_mix -> {
          val service = ScreenService.INSTANCE
          if (service != null) {
            service.toggleAudioSource(item.itemId)
            currentAudioSource = item.updateMenuColor(this, currentAudioSource)
          }
        }
      }
    } catch (e: IllegalArgumentException) {
      toast("Change source error: ${e.message}")
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onDestroy() {
    super.onDestroy()
    val screenService = ScreenService.INSTANCE
    if (screenService != null && !screenService.isStreaming() && !screenService.isRecording()) {
      screenService.setCallback(null)
      activityResultContract.unregister()
      //stop service only if no streaming or recording
      stopService(Intent(this, ScreenService::class.java))
    }
  }

  override fun onConnectionStarted(url: String) {}

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    stopStream()
    toast("Failed: $reason")
  }

  override fun onNewBitrate(bitrate: Long) {}

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    stopStream()
    toast("Auth error")
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }
}
