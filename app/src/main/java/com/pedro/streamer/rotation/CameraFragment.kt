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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
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
  private val width = 640
  private val height = 480
  private val vBitrate = 1200 * 1000
  private var rotation = 0
  private val sampleRate = 32000
  private val isStereo = true
  private val aBitrate = 128 * 1000

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.activity_example, container, false)
    val bStartStop = view.findViewById<Button>(R.id.b_start_stop)
    val bRecord = view.findViewById<Button>(R.id.b_record)
    val bSwitchCamera = view.findViewById<Button>(R.id.switch_camera)
    val etUrl = view.findViewById<EditText>(R.id.et_rtp_url)
    etUrl.setHint(R.string.hint_protocol)

    surfaceView = view.findViewById(R.id.surfaceView)
    (activity as? RotationExampleActivity)?.let {
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
        bStartStop.setText(R.string.stop_button)
      } else {
        genericStream.stopStream()
        bStartStop.setText(R.string.start_button)
      }
    }
    bRecord.setOnClickListener {
      if (!genericStream.isRecording) {
        val folder = PathUtils.getRecordPath()
        if (!folder.exists()) folder.mkdir()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = sdf.format(Date())
        genericStream.startRecord("${folder.absolutePath}/$fileName.mp4") { listener ->

        }
        bRecord.setText(R.string.stop_record)
      } else {
        genericStream.stopRecord()
        bRecord.setText(R.string.start_record)
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
      Toast.makeText(requireContext(), "Audio or Video configuration failed", Toast.LENGTH_LONG).show()
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
    Toast.makeText(requireContext(), "Connected", Toast.LENGTH_SHORT).show()
  }

  override fun onConnectionFailed(reason: String) {
    Handler(Looper.getMainLooper()).post {
      if (genericStream.getStreamClient().reTry(5000, reason, null)) {
        Toast.makeText(requireContext(), "Retry", Toast.LENGTH_SHORT)
          .show()
      } else {
        genericStream.stopStream()
        Toast.makeText(requireContext(), "Failed: $reason", Toast.LENGTH_LONG).show()
      }
    }
  }

  override fun onNewBitrate(bitrate: Long) {
  }

  override fun onDisconnect() {
    Toast.makeText(requireContext(), "Disconnected", Toast.LENGTH_SHORT).show()
  }

  override fun onAuthError() {
  }

  override fun onAuthSuccess() {
    Toast.makeText(requireContext(), "Auth Failed", Toast.LENGTH_LONG).show()
  }
}