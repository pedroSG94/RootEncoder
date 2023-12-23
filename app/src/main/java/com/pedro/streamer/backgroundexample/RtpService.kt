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

package com.pedro.streamer.backgroundexample

import android.app.Notification
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
import androidx.lifecycle.MutableLiveData
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericCamera2
import com.pedro.library.view.OpenGlView
import com.pedro.streamer.R


/**
 * Basic RTMP/RTSP service streaming implementation with camera2
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtpService: Service(), ConnectChecker {

  companion object {
    private const val TAG = "RtpService"
    private const val channelId = "rtpStreamChannel"
    private const val notifyId = 123456
    private var notificationManager: NotificationManager? = null
    val observer = MutableLiveData<RtpService?>()
  }

  private lateinit var genericCamera2: GenericCamera2

  override fun onBind(p0: Intent?): IBinder? {
    return null
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(TAG, "RTP service started")
    return START_STICKY
  }

  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "$TAG create")
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
      notificationManager?.createNotificationChannel(channel)
    }
    keepAliveTrick()
    genericCamera2 = GenericCamera2(applicationContext, true, this)
    observer.postValue(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "RTP service destroy")
    stopRecord()
    stopStream()
    stopPreview()
    observer.postValue(null)
  }

  private fun keepAliveTrick() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
      val notification = NotificationCompat.Builder(this, channelId)
        .setOngoing(true)
        .setContentTitle("")
        .setContentText("").build()
      startForeground(1, notification)
    } else {
      startForeground(1, Notification())
    }
  }

  private fun showNotification(text: String) {
    val notification = NotificationCompat.Builder(applicationContext, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("RTP Stream")
      .setContentText(text).build()
    notificationManager?.notify(notifyId, notification)
  }

  fun prepare(): Boolean {
    return genericCamera2.prepareVideo() && genericCamera2.prepareAudio()
  }

  fun startPreview() {
    genericCamera2.startPreview()
  }

  fun stopPreview() {
    genericCamera2.stopPreview()
  }

  fun switchCamera() {
    genericCamera2.switchCamera()
  }

  fun isStreaming(): Boolean = genericCamera2.isStreaming

  fun isRecording(): Boolean = genericCamera2.isRecording

  fun isOnPreview(): Boolean = genericCamera2.isOnPreview

  fun startStream(endpoint: String) {
    genericCamera2.startStream(endpoint)
  }

  fun stopStream() {
    genericCamera2.stopStream()
  }

  fun startRecord(path: String) {
    genericCamera2.startRecord(path) {
      Log.i(TAG, "record state: ${it.name}")
    }
  }

  fun stopRecord() {
    genericCamera2.stopRecord()
  }

  fun setView(openGlView: OpenGlView) {
    genericCamera2.replaceView(openGlView)
  }

  fun setView(context: Context) {
    genericCamera2.replaceView(context)
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
