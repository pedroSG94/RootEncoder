package com.pedro.rtpstreamer.rotation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.rtplibrary.util.sources.VideoManager
import com.pedro.rtpstreamer.R
import kotlinx.android.synthetic.main.activity_example.*

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RotationExampleActivity: AppCompatActivity(), SurfaceHolder.Callback {

  private val REQUEST_CODE_SCREEN_VIDEO = 1
  private val REQUEST_CODE_INTERNAL_AUDIO = 2
  private var askingMediaProjection = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_example)
    surfaceView.holder.addCallback(this)

    if (!isMyServiceRunning(StreamService::class.java)) {
      val intent = Intent(applicationContext, StreamService::class.java)
      startService(intent)
      StreamService.init(this)
    }

    b_start_stop.setOnClickListener {
      if (!StreamService.isStreaming()) {
        StreamService.startStream()
        b_start_stop.setText(R.string.stop_button)
      } else {
        StreamService.stopStream()
        b_start_stop.setText(R.string.start_button)
      }
    }
    b_record.setOnClickListener {

    }
    switch_camera.setOnClickListener {
      StreamService.switchCamera()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.rotation_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.video_source_camera1 -> {
        StreamService.changeVideoSourceCamera(VideoManager.Source.CAMERA1)
      }
      R.id.video_source_camera2 -> {
        StreamService.changeVideoSourceCamera(VideoManager.Source.CAMERA2)
      }
      R.id.video_source_screen -> {
        askingMediaProjection = true
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE_SCREEN_VIDEO)
      }
      R.id.audio_source_microphone -> {
        StreamService.changeAudioSourceMicrophone()
      }
      R.id.audio_source_internal -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          askingMediaProjection = true
          val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
          val intent = mediaProjectionManager.createScreenCaptureIntent()
          startActivityForResult(intent, REQUEST_CODE_INTERNAL_AUDIO)
        } else {
          Toast.makeText(this, "Android 10+ required", Toast.LENGTH_SHORT).show()
        }
      }
      else -> return false
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (data != null && resultCode == RESULT_OK) {
      if (requestCode == REQUEST_CODE_SCREEN_VIDEO) {
        askingMediaProjection = false
        StreamService.changeVideoSourceScreen(this, resultCode, data)
      } else if (requestCode == REQUEST_CODE_INTERNAL_AUDIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        askingMediaProjection = false
        StreamService.changeAudioSourceInternal(this, resultCode, data)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (StreamService.isStreaming()) {
      b_start_stop.setText(R.string.stop_button)
    } else {
      b_start_stop.setText(R.string.start_button)
    }
  }

  override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations && !askingMediaProjection) { //stop if no rotation activity
      if (StreamService.isStreaming()) StreamService.stopStream()
      if (StreamService.isOnPreview()) StreamService.stopPreview()
      stopService(Intent(applicationContext, StreamService::class.java))
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    if (!StreamService.isOnPreview()) StreamService.startPreview(surfaceView)
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    if (StreamService.isOnPreview()) StreamService.stopPreview()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {

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
}