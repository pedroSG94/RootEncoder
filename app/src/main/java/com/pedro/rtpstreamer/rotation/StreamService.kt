package com.pedro.rtpstreamer.rotation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpStream
import com.pedro.rtplibrary.util.SensorRotationManager
import com.pedro.rtplibrary.util.sources.VideoManager
import com.pedro.rtpstreamer.R

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class StreamService: Service(), ConnectCheckerRtmp {

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
    rtmpCamera?.switchCamera()
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

  fun changeVideoSourceCamera(source: VideoManager.Source) {
    rtmpCamera?.changeVideoSourceCamera(source)
  }

  fun changeVideoSourceScreen(context: Context, resultCode: Int, data: Intent) {
    val mediaProjectionManager = context.applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
    rtmpCamera?.changeVideoSourceScreen(mediaProjection)
  }

  fun changeAudioSourceMicrophone() {
    rtmpCamera?.changeAudioSourceMicrophone()
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  fun changeAudioSourceInternal(context: Context, resultCode: Int, data: Intent) {
    val mediaProjectionManager = context.applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
    rtmpCamera?.changeAudioSourceInternal(mediaProjection)
  }

  override fun onConnectionStartedRtmp(rtmpUrl: String) {
    showNotification("Stream connection started")
  }

  override fun onConnectionSuccessRtmp() {
    showNotification("Stream started")
  }

  override fun onNewBitrateRtmp(bitrate: Long) {

  }

  override fun onConnectionFailedRtmp(reason: String) {
    showNotification("Stream connection failed")
  }

  override fun onDisconnectRtmp() {
    showNotification("Stream stopped")
  }

  override fun onAuthErrorRtmp() {
    showNotification("Stream auth error")
  }

  override fun onAuthSuccessRtmp() {
    showNotification("Stream auth success")
  }
}