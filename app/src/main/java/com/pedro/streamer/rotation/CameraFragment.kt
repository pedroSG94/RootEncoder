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

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.pedro.common.ConnectChecker
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
import com.pedro.streamer.utils.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by pedro on 27/2/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraFragment: Fragment(), ConnectChecker {

  companion object {
    fun getInstance(): CameraFragment = CameraFragment()
  }

  val genericStream: GenericStream by lazy { GenericStream(requireContext(), this) }
  private lateinit var surfaceView: SurfaceView
  private lateinit var bStartStop: ImageView
  private val width = 640
  private val height = 480
  private val vBitrate = 1200 * 1000
  private var rotation = 0
  private val sampleRate = 32000
  private val isStereo = true
  private val aBitrate = 128 * 1000
  private var recordPath = ""

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.activity_example, container, false)
    bStartStop = view.findViewById(R.id.b_start_stop)
    val bRecord = view.findViewById<ImageView>(R.id.b_record)
    val bSwitchCamera = view.findViewById<ImageView>(R.id.switch_camera)
    val etUrl = view.findViewById<EditText>(R.id.et_rtp_url)

    surfaceView = view.findViewById(R.id.surfaceView)
    (activity as? RotationActivity)?.let {
      surfaceView.setOnTouchListener(it)
    }
    surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {
        if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView)
      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        genericStream.getGlInterface().setPreviewResolution(width, height)
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (genericStream.isOnPreview) genericStream.stopPreview()
      }

    })

    bStartStop.setOnClickListener {
      if (!genericStream.isStreaming) {
        genericStream.startStream(etUrl.text.toString())
        bStartStop.setImageResource(R.drawable.stream_stop_icon)
      } else {
        genericStream.stopStream()
        bStartStop.setImageResource(R.drawable.stream_icon)
      }
    }
    bRecord.setOnClickListener {
      if (!genericStream.isRecording) {
        val folder = PathUtils.getRecordPath()
        if (!folder.exists()) folder.mkdir()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
        genericStream.startRecord(recordPath) { status ->
          if (status == RecordController.Status.RECORDING) {
            bRecord.setImageResource(R.drawable.stop_icon)
          }
        }
        bRecord.setImageResource(R.drawable.pause_icon)
      } else {
        genericStream.stopRecord()
        bRecord.setImageResource(R.drawable.record_icon)
        PathUtils.updateGallery(requireContext(), recordPath)
      }
    }
    bSwitchCamera.setOnClickListener {
      when (val source = genericStream.videoSource) {
        is Camera1Source -> source.switchCamera()
        is Camera2Source -> source.switchCamera()
        is CameraXSource -> source.switchCamera()
      }
    }
    return view
  }

  fun setOrientationMode(isVertical: Boolean) {
    val wasOnPreview = genericStream.isOnPreview
    genericStream.release()
    rotation = if (isVertical) 90 else 0
    prepare()
    if (wasOnPreview) genericStream.startPreview(surfaceView)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prepare()
    genericStream.getStreamClient().setReTries(10)
  }

  private fun prepare() {
    val prepared = genericStream.prepareVideo(width, height, vBitrate, rotation = rotation) &&
        genericStream.prepareAudio(sampleRate, isStereo, aBitrate)
    if (!prepared) {
      toast("Audio or Video configuration failed")
      activity?.finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    genericStream.release()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    genericStream.setConfig(newConfig)
  }

  override fun onConnectionStarted(url: String) {
  }

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    if (genericStream.getStreamClient().reTry(5000, reason, null)) {
      toast("Retry")
    } else {
      genericStream.stopStream()
      bStartStop.setImageResource(R.drawable.stream_icon)
      toast("Failed: $reason")
    }
  }

  override fun onNewBitrate(bitrate: Long) {
  }

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    genericStream.stopStream()
    bStartStop.setImageResource(R.drawable.stream_icon)
    toast("Auth error")
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }
}