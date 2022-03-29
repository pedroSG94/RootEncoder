package com.pedro.rtpstreamer.rotation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.rtplibrary.rtmp.RtmpStream
import com.pedro.rtplibrary.util.SensorRotationManager
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
    private var rtmpCamera: RtmpStream? = null
    private var context: Context? = null
    private var sensorRotationManager: SensorRotationManager? = null
    private var currentOrientation = -1

    private fun setImageToStream() {
      context?.let {
        val imageObjectFilterRender = ImageObjectFilterRender()
        rtmpCamera?.getGlInterface()?.setFilter(imageObjectFilterRender)
        imageObjectFilterRender.setImage(BitmapFactory.decodeResource(it.resources, R.mipmap.ic_launcher))
        imageObjectFilterRender.setScale(30f, 30f)
        imageObjectFilterRender.setPosition(TranslateTo.RIGHT)
      }
    }

    fun init(context: Context) {
      this.context = context
      rtmpCamera = RtmpStream(context, connectCheckerRtp)
      sensorRotationManager = SensorRotationManager(context) {
        //0 = portrait, 90 = landscape, 180 = reverse portrait, 270 = reverse landscape
        if (currentOrientation != it) {
          rtmpCamera?.setOrientation(it)
          currentOrientation = it
        }
      }
      sensorRotationManager?.start()
      rtmpCamera?.prepareVideo(640, 480, 1200 * 1000)
      rtmpCamera?.prepareAudio(44100, true, 128 * 1000)
    }

    fun startPreview(surfaceView: SurfaceView) {
      rtmpCamera?.startPreview(surfaceView)
      setImageToStream()
    }

    fun stopPreview() {
      rtmpCamera?.stopPreview()
    }

    fun isStreaming(): Boolean = rtmpCamera?.isStreaming ?: false

    fun isOnPreview(): Boolean = rtmpCamera?.isOnPreview ?: false

    fun startStream() {
      rtmpCamera?.startStream("rtmp://192.168.1.132/live/pedro")
    }

    fun changeVideoSourceScreen(context: Context, resultCode: Int, data: Intent) {
      val mediaProjectionManager = context.applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
      rtmpCamera?.changeVideoSourceScreen(mediaProjection)
    }

    fun stopStream() {
      rtmpCamera?.stopStream()
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
    sensorRotationManager?.stop()
  }
}