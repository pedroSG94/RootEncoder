package com.pedro.rtpstreamer.rotation

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
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtplibrary.rtmp.RtmpCamera
import com.pedro.rtpstreamer.R
import com.pedro.rtpstreamer.backgroundexample.ConnectCheckerRtp

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class StreamService: Service() {

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
    return START_STICKY
  }

  companion object {
    private const val TAG = "RtpService"
    private const val channelId = "rtpStreamChannel"
    private const val notifyId = 123456
    private var notificationManager: NotificationManager? = null
    private var rtmpCamera: RtmpCamera? = null
    private var context: Context? = null

    fun init(context: Context) {
      this.context = context
      rtmpCamera = RtmpCamera(context, connectCheckerRtp)
      rtmpCamera?.prepareVideo(640, 480, 1200 * 1000)
      rtmpCamera?.prepareAudio(44100, true, 128 * 1000)
    }

    fun startPreview(surfaceView: SurfaceView) {
      rtmpCamera?.startPreview(surfaceView)
      if (CameraHelper.isPortrait(context)) {
        rtmpCamera?.setPreviewOrientation(0)
        rtmpCamera?.setStreamOrientation(0)
      } else {
        rtmpCamera?.setPreviewOrientation(270)
        rtmpCamera?.setStreamOrientation(270)
      }
    }

    fun stopPreview() {
      rtmpCamera?.stopPreview()
    }

    fun isStreaming(): Boolean = rtmpCamera?.isStreaming ?: false

    fun isOnPreview(): Boolean = rtmpCamera?.isOnPreview ?: false

    fun startStream() {
      rtmpCamera?.startStream("rtmp://192.168.1.132/live/pedro")
    }

    fun stopStream() {
      rtmpCamera?.stopStream()
      rtmpCamera?.prepareVideo(640, 480, 1200 * 1000)
      rtmpCamera?.prepareAudio(44100, true, 128 * 1000)
    }

    private val connectCheckerRtp = object : ConnectCheckerRtp {
      override fun onConnectionStartedRtp(rtpUrl: String) {
        showNotification("Stream connection started")
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

    private fun showNotification(text: String) {
      context?.let {
        val notification = NotificationCompat.Builder(it, channelId)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle("RTP Stream")
          .setContentText(text).build()
        notificationManager?.notify(notifyId, notification)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.e(TAG, "RTP service destroy")
    stopStream()
  }
}