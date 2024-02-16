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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.SensorRotationManager
import com.pedro.library.util.sources.audio.AudioSource
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.library.util.sources.video.VideoSource
import com.pedro.streamer.R

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class StreamService: Service(), ConnectChecker {

  companion object {
    private const val TAG = "StreamService"
    private const val channelId = "rtpStreamChannel"
    private const val notifyId = 123456
    private var notificationManager: NotificationManager? = null
    val observer = MutableLiveData<StreamService?>()
  }

  private var rtmpCamera: RtmpStream? = null
  private var sensorRotationManager: SensorRotationManager? = null
  private var currentOrientation = -1
  private var prepared = false

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
    rtmpCamera = RtmpStream(applicationContext, this)

    sensorRotationManager = SensorRotationManager(applicationContext) {
      //0 = portrait, 90 = landscape, 180 = reverse portrait, 270 = reverse landscape
      if (currentOrientation != it) {
        rtmpCamera?.setOrientation(it)
        currentOrientation = it
      }
    }
    sensorRotationManager?.start()
    observer.postValue(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "RTP service destroy")
    stopRecord()
    stopStream()
    stopPreview()
    rtmpCamera?.release()
    sensorRotationManager?.stop()
    prepared = false
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
    if (!prepared) {
      prepared = rtmpCamera?.prepareVideo(640, 480, 1200 * 1000) ?: false &&
          rtmpCamera?.prepareAudio(44100, true, 128 * 1000) ?: false
    }
    return prepared
  }

  fun startPreview(surfaceView: SurfaceView) {
    rtmpCamera?.startPreview(surfaceView)
  }

  fun stopPreview() {
    rtmpCamera?.stopPreview()
  }

  fun switchCamera() {
    when (val source = rtmpCamera?.videoSource) {
      is Camera1Source -> {
        source.switchCamera()
      }
      is Camera2Source -> {
        source.switchCamera()
      }
      is CameraXSource -> {
        source.switchCamera()
      }
    }
  }

  fun isStreaming(): Boolean = rtmpCamera?.isStreaming ?: false

  fun isRecording(): Boolean = rtmpCamera?.isRecording ?: false

  fun isOnPreview(): Boolean = rtmpCamera?.isOnPreview ?: false

  fun startStream(endpoint: String) {
    rtmpCamera?.startStream(endpoint)
  }

  fun stopStream() {
    rtmpCamera?.stopStream()
  }

  fun startRecord(path: String) {
    rtmpCamera?.startRecord(path) {
      Log.i(TAG, "record state: ${it.name}")
    }
  }

  fun stopRecord() {
    rtmpCamera?.stopRecord()
  }

  fun changeVideoSource(source: VideoSource) {
    rtmpCamera?.changeVideoSource(source)
  }

  fun changeAudioSource(source: AudioSource) {
    rtmpCamera?.changeAudioSource(source)
  }

  override fun onConnectionStarted(url: String) {
    showNotification("Stream connection started")
  }

  override fun onConnectionSuccess() {
    showNotification("Stream started")
  }

  override fun onNewBitrate(bitrate: Long) {

  }

  override fun onConnectionFailed(reason: String) {
    showNotification("Stream connection failed")
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