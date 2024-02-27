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
package com.pedro.streamer.screenexample

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.streamer.R
import com.pedro.streamer.screenexample.ScreenService.Companion.INSTANCE

/**
 * More documentation see:
 * [com.pedro.library.base.DisplayBase]
 * [com.pedro.library.rtmp.RtmpDisplay]
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class ScreenActivity : AppCompatActivity(), ConnectChecker, View.OnClickListener {

  private lateinit var button: ImageView
  private lateinit var etUrl: EditText

  private val activityResultContract = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val data = result.data
    if (data != null && result.resultCode == RESULT_OK) {
      val displayService = INSTANCE
      if (displayService != null) {
        val endpoint = etUrl.text.toString()
        displayService.prepareStreamRtp(result.resultCode, data)
        displayService.startStreamRtp(endpoint)
      }
    } else {
      Toast.makeText(this, "No permissions available", Toast.LENGTH_SHORT).show()
      button.setImageResource(R.drawable.stream_icon)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_display)
    button = findViewById(R.id.b_start_stop)
    button.setOnClickListener(this)
    etUrl = findViewById(R.id.et_rtp_url)
    val displayService = INSTANCE
    //No streaming/recording start service
    if (displayService == null) {
      startService(Intent(this, ScreenService::class.java))
    }
    if (displayService != null && displayService.isStreaming()) {
      button.setImageResource(R.drawable.stream_stop_icon)
    } else {
      button.setImageResource(R.drawable.stream_icon)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    val displayService = INSTANCE
    if (displayService != null && !displayService.isStreaming() && !displayService.isRecording()) {
      activityResultContract.unregister()
      //stop service only if no streaming or recording
      stopService(Intent(this, ScreenService::class.java))
    }
  }

  override fun onConnectionStarted(url: String) {}
  override fun onConnectionSuccess() {
    Toast.makeText(this@ScreenActivity, "Connection success", Toast.LENGTH_SHORT).show()
  }

  override fun onConnectionFailed(reason: String) {
    Toast.makeText(this@ScreenActivity, "Connection failed. $reason", Toast.LENGTH_SHORT)
      .show()
    val displayService = INSTANCE
    displayService?.stopStream()
    button.setImageResource(R.drawable.stream_icon)
  }

  override fun onNewBitrate(bitrate: Long) {}
  override fun onDisconnect() {
    Toast.makeText(this@ScreenActivity, "Disconnected", Toast.LENGTH_SHORT).show()
  }

  override fun onAuthError() {
    Toast.makeText(this@ScreenActivity, "Auth error", Toast.LENGTH_SHORT).show()
  }

  override fun onAuthSuccess() {
    Toast.makeText(this@ScreenActivity, "Auth success", Toast.LENGTH_SHORT).show()
  }

  override fun onClick(view: View) {
    val displayService = INSTANCE
    if (displayService != null) {
      if (view.id == R.id.b_start_stop) {
        if (!displayService.isStreaming()) {
          displayService.sendIntent()?.let { intent ->
            button.setImageResource(R.drawable.stream_stop_icon)
            activityResultContract.launch(intent)
          }
        } else {
          button.setImageResource(R.drawable.stream_icon)
          displayService.stopStream()
        }
      }
    }
  }
}
