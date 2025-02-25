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
package com.pedro.streamer.oldapi

import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.TextureView
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericCamera1
import com.pedro.library.view.AutoFitTextureView
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
import com.pedro.streamer.utils.ScreenOrientation
import com.pedro.streamer.utils.fitAppPadding
import com.pedro.streamer.utils.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Example code for devices using API 16-20.
 * If you are using API 18+ you can replace AutoFitTextureView to OpenGlView and use OpenGl features
 *
 * More documentation see:
 * [com.pedro.library.base.Camera1Base]
 * Support RTMP, RTSP and SRT with commons features
 * [com.pedro.library.generic.GenericCamera1]
 * Support RTSP with all RTSP features
 * [com.pedro.library.rtsp.RtspCamera1]
 * Support RTMP with all RTMP features
 * [com.pedro.library.rtmp.RtmpCamera1]
 * Support SRT with all SRT features
 * [com.pedro.library.srt.SrtCamera1]
 */
class OldApiActivity : AppCompatActivity(), ConnectChecker, TextureView.SurfaceTextureListener {

  private lateinit var genericCamera1: GenericCamera1
  private lateinit var bStream: ImageView
  private lateinit var bRecord: ImageView
  private lateinit var etUrl: EditText
  private lateinit var autoFitTextureView: AutoFitTextureView
  private var recordPath = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_old_api)
    fitAppPadding()
    bStream = findViewById(R.id.b_start_stop)
    bRecord = findViewById(R.id.b_record)
    etUrl = findViewById(R.id.et_rtp_url)
    autoFitTextureView = findViewById(R.id.surfaceView)
    val switchCamera = findViewById<ImageView>(R.id.switch_camera)
    genericCamera1 = GenericCamera1(autoFitTextureView, this)
    bStream.setOnClickListener {
      if (genericCamera1.isStreaming) {
        bStream.setImageResource(R.drawable.stream_icon)
        genericCamera1.stopStream()
        if (!genericCamera1.isRecording) ScreenOrientation.unlockScreen(this)
      } else if (genericCamera1.isRecording || prepare()) {
        bStream.setImageResource(R.drawable.stream_stop_icon)
        genericCamera1.startStream(etUrl.text.toString())
        ScreenOrientation.lockScreen(this)
      } else {
        toast("Error preparing stream, This device cant do it")
      }
    }
    bRecord.setOnClickListener {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        if (genericCamera1.isRecording) {
          genericCamera1.stopRecord()
          bRecord.setImageResource(R.drawable.record_icon)
          PathUtils.updateGallery(this, recordPath)
          if (!genericCamera1.isStreaming) ScreenOrientation.unlockScreen(this)
        } else if (genericCamera1.isStreaming || prepare()) {
          val folder = PathUtils.getRecordPath()
          if (!folder.exists()) folder.mkdir()
          val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
          recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
          bRecord.setImageResource(R.drawable.pause_icon)
          genericCamera1.startRecord(recordPath) { status ->
            if (status == RecordController.Status.RECORDING) {
              bRecord.setImageResource(R.drawable.stop_icon)
            }
          }
          ScreenOrientation.lockScreen(this)
        } else {
          toast("Error preparing stream, This device cant do it")
        }
      } else {
        toast("You need min JELLY_BEAN_MR2(API 18) for do it...")
      }
    }
    switchCamera.setOnClickListener {
      try {
        genericCamera1.switchCamera()
      } catch (e: CameraOpenException) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
      }
    }
    autoFitTextureView.surfaceTextureListener = this
  }

  private fun prepare(): Boolean {
    val prepared = genericCamera1.prepareAudio() && genericCamera1.prepareVideo()
    adaptPreview()
    return prepared
  }

  private fun adaptPreview() {
    val isPortrait = CameraHelper.isPortrait(this)
    val w = if (isPortrait) genericCamera1.streamHeight else genericCamera1.streamWidth
    val h = if (isPortrait) genericCamera1.streamWidth else genericCamera1.streamHeight
    autoFitTextureView.setAspectRatio(w, h)
  }

  override fun onConnectionStarted(url: String) {}

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    toast("Failed: $reason")
    genericCamera1.stopStream()
    if (!genericCamera1.isRecording) ScreenOrientation.unlockScreen(this)
    bStream.setImageResource(R.drawable.stream_icon)
  }

  override fun onNewBitrate(bitrate: Long) {}

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    toast("Auth error")
    genericCamera1.stopStream()
    bStream.setImageResource(R.drawable.stream_icon)
    if (!genericCamera1.isRecording) ScreenOrientation.unlockScreen(this)
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }

  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    if (!genericCamera1.isOnPreview) {
      genericCamera1.startPreview()
      adaptPreview()
    }
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {

  }

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && genericCamera1.isRecording) {
      genericCamera1.stopRecord()
      bRecord.setBackgroundResource(R.drawable.record_icon)
      PathUtils.updateGallery(this, recordPath)
    }
    if (genericCamera1.isStreaming) {
      genericCamera1.stopStream()
      bStream.setImageResource(R.drawable.stream_icon)
    }
    if (genericCamera1.isOnPreview) genericCamera1.stopPreview()
    ScreenOrientation.unlockScreen(this)
    return true
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
