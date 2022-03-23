package com.pedro.rtpstreamer.rotation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.rtpstreamer.R
import kotlinx.android.synthetic.main.activity_example.*

/**
 * Created by pedro on 22/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RotationExampleActivity: AppCompatActivity(), SurfaceHolder.Callback {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_example)
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
    surfaceView.holder.addCallback(this)
  }

  override fun onResume() {
    super.onResume()
    if (StreamService.isStreaming()) {
      b_start_stop.setText(R.string.stop_button)
    } else {
      b_start_stop.setText(R.string.start_button)
    }
  }

  override fun onBackPressed() {
    super.onBackPressed()
    if (StreamService.isStreaming()) StreamService.stopStream()
    if (StreamService.isOnPreview()) StreamService.stopPreview()
    stopService(Intent(applicationContext, StreamService::class.java))
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