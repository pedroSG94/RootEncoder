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
package com.pedro.streamer.file

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericFromFile
import com.pedro.library.view.OpenGlView
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
import com.pedro.streamer.utils.ScreenOrientation
import com.pedro.streamer.utils.fitAppPadding
import com.pedro.streamer.utils.setColorFilter
import com.pedro.streamer.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Example code to stream using a file.
 * Necessary API 18+
 *
 * More documentation see:
 * [com.pedro.library.base.FromFileBase]
 * Support RTMP, RTSP and SRT with commons features
 * [com.pedro.library.generic.GenericFromFile]
 * Support RTSP with all RTSP features
 * [com.pedro.library.rtsp.RtspFromFile]
 * Support RTMP with all RTMP features
 * [com.pedro.library.rtmp.RtmpFromFile]
 * Support SRT with all SRT features
 * [com.pedro.library.srt.SrtFromFile]
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class FromFileActivity : AppCompatActivity(), ConnectChecker,
  VideoDecoderInterface, AudioDecoderInterface, OnSeekBarChangeListener {

  private lateinit var genericFromFile: GenericFromFile
  private lateinit var bStream: ImageView
  private lateinit var bSelectFile: ImageView
  private lateinit var bReSync: ImageView
  private lateinit var bRecord: ImageView
  private lateinit var seekBar: SeekBar
  private lateinit var etUrl: EditText
  private lateinit var tvFileName: TextView
  private lateinit var openGlView: OpenGlView

  private var filePath: Uri? = null
  private var recordPath = ""
  private var touching = false

  private val activityResult = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    filePath = uri
    tvFileName.text = (uri?.path ?: "").split("/").last()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_from_file)
    fitAppPadding()
    bStream = findViewById(R.id.b_start_stop)
    bSelectFile = findViewById(R.id.select_file)
    bReSync = findViewById(R.id.b_re_sync)
    bRecord = findViewById(R.id.b_record)
    etUrl = findViewById(R.id.et_rtp_url)
    seekBar = findViewById(R.id.seek_bar)
    tvFileName = findViewById(R.id.tv_file_name)
    openGlView = findViewById(R.id.surfaceView)
    genericFromFile = GenericFromFile(openGlView, this, this, this)
    genericFromFile.setLoopMode(true)

    bStream.setOnClickListener {
      if (genericFromFile.isStreaming) {
        genericFromFile.stopStream()
        bStream.setImageResource(R.drawable.stream_icon)
        if (!genericFromFile.isRecording) ScreenOrientation.unlockScreen(this)
      } else if (genericFromFile.isRecording || prepare()) {
        if (!genericFromFile.isAudioDeviceEnabled) genericFromFile.playAudioDevice()
        genericFromFile.startStream(etUrl.text.toString())
        bStream.setImageResource(R.drawable.stream_stop_icon)
        ScreenOrientation.lockScreen(this)
        updateProgress()
      } else {
        toast("Error preparing stream, This device cant do it")
      }
    }

    bRecord.setOnClickListener {
      if (genericFromFile.isRecording) {
        genericFromFile.stopRecord()
        bRecord.setImageResource(R.drawable.record_icon)
        PathUtils.updateGallery(this, recordPath)
        if (!genericFromFile.isStreaming) ScreenOrientation.unlockScreen(this)
      } else if (genericFromFile.isStreaming || prepare()) {
        if (!genericFromFile.isAudioDeviceEnabled) genericFromFile.playAudioDevice()
        val folder = PathUtils.getRecordPath()
        if (!folder.exists()) folder.mkdir()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
        bRecord.setImageResource(R.drawable.pause_icon)
        genericFromFile.startRecord(recordPath) { status ->
          if (status == RecordController.Status.RECORDING) {
            bRecord.setImageResource(R.drawable.stop_icon)
          }
        }
        ScreenOrientation.lockScreen(this)
        updateProgress()
      } else {
        toast("Error preparing stream, This device cant do it")
      }
    }

    bSelectFile.setOnClickListener {
      activityResult.launch("*/*")
    }

    bReSync.setOnClickListener {
      genericFromFile.reSyncFile()
    }

    seekBar.progressDrawable.setColorFilter(Color.RED)
    seekBar.setOnSeekBarChangeListener(this)
  }

  override fun onPause() {
    super.onPause()
    genericFromFile.stopAudioDevice()
    if (genericFromFile.isRecording) {
      genericFromFile.stopRecord()
      bRecord.setImageResource(R.drawable.record_icon)
    }
    if (genericFromFile.isStreaming) {
      genericFromFile.stopStream()
      bStream.setImageResource(R.drawable.stream_icon)
    }
    ScreenOrientation.unlockScreen(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    activityResult.unregister()
  }

  override fun onConnectionStarted(url: String) {}

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    toast("Failed: $reason")
    genericFromFile.stopStream()
    bStream.setImageResource(R.drawable.stream_icon)
    if (!genericFromFile.isRecording) ScreenOrientation.unlockScreen(this)
  }

  override fun onNewBitrate(bitrate: Long) {}

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    toast("Auth error")
    genericFromFile.stopStream()
    bStream.setImageResource(R.drawable.stream_icon)
    if (!genericFromFile.isRecording) ScreenOrientation.unlockScreen(this)
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }

  @Throws(IOException::class)
  private fun prepare(): Boolean {
    if (filePath == null) return false
    var result = genericFromFile.prepareVideo(applicationContext, filePath)
    result = result or genericFromFile.prepareAudio(applicationContext, filePath)
    return result
  }

  private fun updateProgress() {
    seekBar.max = max(
      genericFromFile.videoDuration.toInt(),
      genericFromFile.audioDuration.toInt()
    )
    CoroutineScope(Dispatchers.IO).launch {
      while (genericFromFile.isStreaming || genericFromFile.isRecording) {
        delay(1000)
        if (!touching) {
          withContext(Dispatchers.Main) {
            seekBar.progress =
              max(genericFromFile.videoTime.toInt(), genericFromFile.audioTime.toInt())
          }
        }
      }
    }
  }

  override fun onVideoDecoderFinished() {}
  override fun onAudioDecoderFinished() {}
  override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {}
  override fun onStartTrackingTouch(seekBar: SeekBar) {
    touching = true
  }

  override fun onStopTrackingTouch(seekBar: SeekBar) {
    if (genericFromFile.isStreaming || genericFromFile.isRecording) {
      genericFromFile.moveTo(seekBar.progress.toDouble())
      //re sync after move to avoid async
      Handler(Looper.getMainLooper()).postDelayed({ genericFromFile.reSyncFile() }, 500)
    }
    touching = false
  }
}
