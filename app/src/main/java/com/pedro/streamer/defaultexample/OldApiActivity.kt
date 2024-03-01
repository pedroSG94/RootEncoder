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
package com.pedro.streamer.defaultexample

import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericCamera1
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
import com.pedro.streamer.utils.ScreenOrientation.lockScreen
import com.pedro.streamer.utils.ScreenOrientation.unlockScreen
import com.pedro.streamer.utils.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * More documentation see:
 * [com.pedro.library.base.Camera1Base]
 * [com.pedro.library.rtmp.RtmpCamera1]
 */
class OldApiActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

  private lateinit var rtmpCamera1: GenericCamera1
  private lateinit var bStream: ImageView
  private lateinit var bRecord: ImageView
  private lateinit var etUrl: EditText
  private var recordPath = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_example)
    bStream = findViewById(R.id.b_start_stop)
    bRecord = findViewById(R.id.b_record)
    etUrl = findViewById(R.id.et_rtp_url)
    val switchCamera = findViewById<ImageView>(R.id.switch_camera)
    val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
    rtmpCamera1 = GenericCamera1(surfaceView, this)
    bStream.setOnClickListener {
      if (rtmpCamera1.isStreaming) {
        bStream.setImageResource(R.drawable.stream_icon)
        rtmpCamera1.stopStream()
        unlockScreen(this)
      } else if (rtmpCamera1.isRecording || prepare()) {
        bStream.setImageResource(R.drawable.stream_stop_icon)
        rtmpCamera1.startStream(etUrl.text.toString())
        lockScreen(this)
      } else {
        toast("Error preparing stream, This device cant do it")
      }
    }
    bRecord.setOnClickListener {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        if (rtmpCamera1.isRecording) {
          rtmpCamera1.stopRecord()
          bRecord.setImageResource(R.drawable.record_icon)
          PathUtils.updateGallery(this, recordPath)
        } else if (rtmpCamera1.isStreaming || prepare()) {
          val folder = PathUtils.getRecordPath()
          if (!folder.exists()) folder.mkdir()
          val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
          recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
          rtmpCamera1.startRecord(recordPath) { status ->
            if (status == RecordController.Status.RECORDING) {
              bRecord.setImageResource(R.drawable.stop_icon)
            }
          }
          bRecord.setImageResource(R.drawable.pause_icon)
        } else {
          toast("Error preparing stream, This device cant do it")
        }
      } else {
        toast("You need min JELLY_BEAN_MR2(API 18) for do it...")
      }
    }
    switchCamera.setOnClickListener {
      try {
        rtmpCamera1.switchCamera()
      } catch (e: CameraOpenException) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
      }
    }
    surfaceView.holder.addCallback(this)
  }

  private fun prepare(): Boolean {
    return rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()
  }

  override fun onConnectionStarted(url: String) {}

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    toast("Failed: $reason")
    rtmpCamera1.stopStream()
    unlockScreen(this)
    bStream.setImageResource(R.drawable.stream_icon)
  }

  override fun onNewBitrate(bitrate: Long) {}

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    toast("Auth error")
    rtmpCamera1.stopStream()
    bStream.setImageResource(R.drawable.stream_icon)
    unlockScreen(this)
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }

  override fun surfaceCreated(surfaceHolder: SurfaceHolder) {}

  override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
    if (!rtmpCamera1.isOnPreview) rtmpCamera1.startPreview()
  }

  override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && rtmpCamera1.isRecording) {
      rtmpCamera1.stopRecord()
      bRecord.setBackgroundResource(R.drawable.record_icon)
      PathUtils.updateGallery(this, recordPath)
    }
    if (rtmpCamera1.isStreaming) {
      rtmpCamera1.stopStream()
      bStream.setImageResource(R.drawable.stream_icon)
    }
    if (rtmpCamera1.isOnPreview) rtmpCamera1.stopPreview()
    unlockScreen(this)
  }
}
