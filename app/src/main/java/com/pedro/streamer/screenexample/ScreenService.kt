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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericDisplay
import com.pedro.streamer.R


/**
 * Basic RTMP/RTSP service streaming implementation with camera2
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class ScreenService: Service(), ConnectChecker {

  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "RTP Display service create")
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
      notificationManager?.createNotificationChannel(channel)
    }
    genericDisplay = GenericDisplay(baseContext, true, this)
    genericDisplay.glInterface?.setForceRender(true)
    INSTANCE = this
  }

  private fun keepAliveTrick() {
    val notification = NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.drawable.notification_icon)
      .setSilent(true)
      .setOngoing(false)
      .build()
    startForeground(1, notification)
  }

  override fun onBind(p0: Intent?): IBinder? {
    return null
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "RTP Display service started")
    return START_STICKY
  }

  companion object {
    private const val TAG = "DisplayService"
    private const val channelId = "rtpDisplayStreamChannel"
    const val notifyId = 123456
    var INSTANCE: ScreenService? = null
  }

  private var notificationManager: NotificationManager? = null
  private lateinit var genericDisplay: GenericDisplay

  fun sendIntent(): Intent? {
    return genericDisplay.sendIntent()
  }

  fun isStreaming(): Boolean {
    return genericDisplay.isStreaming
  }

  fun isRecording(): Boolean {
    return genericDisplay.isRecording
  }

  fun stopStream() {
    if (genericDisplay.isStreaming) {
      genericDisplay.stopStream()
      notificationManager?.cancel(notifyId)
    }
  }

  private fun showNotification(text: String) {
    val notification = NotificationCompat.Builder(baseContext, channelId)
      .setSmallIcon(R.drawable.notification_icon)
      .setContentTitle("RTP Display Stream")
      .setContentText(text)
      .setOngoing(false)
      .build()
    notificationManager?.notify(notifyId, notification)
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "RTP Display service destroy")
    stopStream()
    INSTANCE = null
  }

  fun prepareStreamRtp(resultCode: Int, data: Intent) {
    keepAliveTrick()
    stopStream()
    genericDisplay.setIntentResult(resultCode, data)
  }

  fun startStreamRtp(endpoint: String) {
    if (!genericDisplay.isStreaming) {
      if (genericDisplay.prepareVideo() && genericDisplay.prepareAudio()) {
        genericDisplay.startStream(endpoint)
      }
    } else {
      showNotification("You are already streaming :(")
    }
  }

  override fun onConnectionStarted(url: String) {
    showNotification("Stream connection started")
  }

  override fun onConnectionSuccess() {
    showNotification("Stream started")
    Log.e(TAG, "RTP service destroy")
  }

  override fun onNewBitrate(bitrate: Long) {

  }

  override fun onConnectionFailed(reason: String) {
    showNotification("Stream connection failed")
    Log.e(TAG, "RTP service destroy")
  }

  override fun onDisconnect() {
    showNotification("Stream stopped")
  }

  override fun onAuthError() {
    showNotification("Stream auth error")
  }

  override fun onAuthSuccess() {
    showNotification("Stream auth success")
  }
}