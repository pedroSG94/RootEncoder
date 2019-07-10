package com.pedro.rtpstreamer.backgroundexample

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
import com.pedro.rtplibrary.base.Camera2Base
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.rtsp.RtspCamera2
import com.pedro.rtpstreamer.R


/**
 * Basic RTMP/RTSP service streaming implementation with camera2
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtpService : Service(), ConnectCheckerRtp {

  private val TAG = "RtpService"
  private val channelId = "rtpStreamChannel"
  private val notifyId = 123456
  private var notificationManager: NotificationManager? = null
  private var endpoint: String? = null
  private var camera2Base: Camera2Base? = null

  override fun onCreate() {
    super.onCreate()
    Log.e(TAG, "RTP service create")
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
      notificationManager?.createNotificationChannel(channel)
    }
    keepAliveTrick()
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

  override fun onBind(p0: Intent?): IBinder? {
    return null
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.e(TAG, "RTP service started")
    endpoint = intent?.extras?.getString("endpoint")
    if (endpoint != null) {
      prepareStreamRtp()
      startStreamRtp(endpoint!!)
    }
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.e(TAG, "RTP service destroy")
    stopStreamRtp()
  }

  private fun prepareStreamRtp() {
    if (endpoint!!.startsWith("rtmp")) {
      camera2Base = RtmpCamera2(baseContext, true, this)
    } else {
      camera2Base = RtspCamera2(baseContext, true, this)
    }
  }

  private fun startStreamRtp(endpoint: String) {
    if (!camera2Base!!.isStreaming) {
      if (camera2Base!!.prepareVideo() && camera2Base!!.prepareAudio()) {
        camera2Base!!.startStream(endpoint)
      }
    } else {
      showNotification("You are already streaming :(")
    }
  }

  private fun stopStreamRtp() {
    if (camera2Base != null) {
      if (camera2Base!!.isStreaming) {
        camera2Base!!.stopStream()
      }
    }
  }

  private fun showNotification(text: String) {
    val notification = NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("RTP Stream")
      .setContentText(text).build()
    notificationManager?.notify(notifyId, notification)
  }

  private fun stopNotification() {
    notificationManager?.cancel(notifyId)
  }

  override fun onConnectionSuccessRtp() {
    showNotification("Stream started")
    Log.e(TAG, "RTP service destroy")
  }

  override fun onNewBitrateRtp(bitrate: Long) {

  }

  override fun onConnectionFailedRtp(reason: String) {
    showNotification("Stream connection failed")
    Log.e(TAG, "RTP service destroy")
  }

  override fun onDisconnectRtp() {
    showNotification("Stream stopped")
  }

  override fun onAuthErrorRtp() {
    showNotification("Stream auth error")
  }

  override fun onAuthSuccessRtp() {
    showNotification("Stream auth success")
  }
}
