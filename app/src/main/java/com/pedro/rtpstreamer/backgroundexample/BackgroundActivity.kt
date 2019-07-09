package com.pedro.rtpstreamer.backgroundexample

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pedro.rtpstreamer.R
import kotlinx.android.synthetic.main.activity_background.*

class BackgroundActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_background)
    b_start_stop.setOnClickListener {
      if (isMyServiceRunning(RtpService::class.java)) {
        stopService(Intent(applicationContext, RtpService::class.java))
        b_start_stop.setText(R.string.start_button)
      } else {
        val intent = Intent(applicationContext, RtpService::class.java)
        intent.putExtra("endpoint", et_rtp_url.text.toString())
        startService(intent)
        b_start_stop.setText(R.string.stop_button)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (isMyServiceRunning(RtpService::class.java)) {
      b_start_stop.setText(R.string.stop_button)
    } else {
      b_start_stop.setText(R.string.start_button)
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
}
