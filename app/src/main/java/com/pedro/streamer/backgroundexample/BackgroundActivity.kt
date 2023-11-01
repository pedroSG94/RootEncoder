/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.streamer.backgroundexample

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.streamer.R
import com.pedro.streamer.databinding.ActivityBackgroundBinding

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class BackgroundActivity : AppCompatActivity(), SurfaceHolder.Callback {

  private lateinit var binding: ActivityBackgroundBinding
  private var service: RtpService? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityBackgroundBinding.inflate(layoutInflater)
    setContentView(binding.root)
    RtpService.observer.observe(this) {
      service = it
      startPreview()
    }

    binding.bStartStop.setOnClickListener {
      if (service?.isStreaming() != true) {
        service?.stopPreview()
        val endpoint = binding.etRtpUrl.text.toString()
        if (service?.prepare(endpoint) == true) {
          startPreview()
          service?.startStream(endpoint)
          binding.bStartStop.setText(R.string.stop_button)
        }
      } else {
        service?.stopStream()
        binding.bStartStop.setText(R.string.start_button)
      }
    }
    binding.surfaceView.holder.addCallback(this)
  }

  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    startPreview()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    service?.setView(this)
    if (service?.isOnPreview() == true) service?.stopPreview()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {

  }

  override fun onResume() {
    super.onResume()
    if (!isMyServiceRunning(RtpService::class.java)) {
      val intent = Intent(applicationContext, RtpService::class.java)
      startService(intent)
    }
    if (service?.isStreaming() == true) {
      binding.bStartStop.setText(R.string.stop_button)
    } else {
      binding.bStartStop.setText(R.string.start_button)
    }
  }

  override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations) { //stop if no rotation activity
      if (service?.isOnPreview() == true) service?.stopPreview()
      if (service?.isStreaming() != true) {
        service = null
        stopService(Intent(applicationContext, RtpService::class.java))
      }
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

  private fun startPreview() {
    if (binding.surfaceView.holder.surface.isValid) {
      service?.setView(binding.surfaceView)
    }
    //check if onPreview and if surface is valid
    if (service?.isOnPreview() != true && binding.surfaceView.holder.surface.isValid) {
      service?.startPreview()
    }
  }
}
