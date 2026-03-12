/*
 * Copyright (C) 2024 pedroSG94.
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
package com.pedro.streamer.srtreceiver

import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedro.srtreceiver.SrtReceiver
import com.pedro.streamer.R
import com.pedro.streamer.utils.fitAppPadding
import com.pedro.streamer.utils.toast

/**
 * Demo activity showing SRT receiver (listener mode) functionality.
 * 
 * This activity demonstrates receiving an SRT stream in listener mode and displaying
 * the video/audio on Android using MediaCodec decoders.
 * 
 * Usage:
 * 1. Start this activity
 * 2. Enter port number (default: 9991)
 * 3. Click "Start Receiver"
 * 4. From another device/computer, send SRT stream:
 *    ffmpeg -re -i input.mp4 -c copy -f mpegts "srt://DEVICE_IP:9991?mode=caller"
 */
class SrtReceiverActivity : AppCompatActivity() {

  private lateinit var surfaceView: SurfaceView
  private lateinit var etPort: EditText
  private lateinit var bStartStop: Button
  private lateinit var tvStatus: TextView
  
  private var srtReceiver: SrtReceiver? = null
  private var isReceiving = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_srt_receiver)
    fitAppPadding()
    
    surfaceView = findViewById(R.id.surfaceView)
    etPort = findViewById(R.id.et_port)
    bStartStop = findViewById(R.id.b_start_stop)
    tvStatus = findViewById(R.id.tv_status)
    
    // Default port
    etPort.setText("9991")
    
    bStartStop.setOnClickListener {
      if (isReceiving) {
        stopReceiver()
      } else {
        startReceiver()
      }
    }
    
    updateStatus("Ready")
  }
  
  private fun startReceiver() {
    val portStr = etPort.text.toString()
    if (portStr.isEmpty()) {
      toast("Please enter a port number")
      return
    }
    
    val port = portStr.toIntOrNull()
    if (port == null || port < 1024 || port > 65535) {
      toast("Invalid port number (must be 1024-65535)")
      return
    }
    
    try {
      // Create SRT receiver
      srtReceiver = SrtReceiver(surfaceView)
      
      // Start listening
      srtReceiver?.start(port)
      
      isReceiving = true
      bStartStop.text = "Stop Receiver"
      updateStatus("Listening on port $port\nWaiting for connection...")
      
      // Show connection info
      val ipAddress = getIPAddress()
      toast("Listening on $ipAddress:$port\nSend stream with:\nffmpeg -re -i input.mp4 -c copy -f mpegts \"srt://$ipAddress:$port?mode=caller\"")
      
    } catch (e: Exception) {
      toast("Failed to start receiver: ${e.message}")
      updateStatus("Error: ${e.message}")
      e.printStackTrace()
    }
  }
  
  private fun stopReceiver() {
    try {
      srtReceiver?.stop()
      srtReceiver = null
      
      isReceiving = false
      bStartStop.text = "Start Receiver"
      updateStatus("Stopped")
      toast("SRT receiver stopped")
      
    } catch (e: Exception) {
      toast("Error stopping receiver: ${e.message}")
      e.printStackTrace()
    }
  }
  
  private fun updateStatus(status: String) {
    runOnUiThread {
      tvStatus.text = status
    }
  }
  
  private fun getIPAddress(): String {
    try {
      val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        val addresses = networkInterface.inetAddresses
        while (addresses.hasMoreElements()) {
          val address = addresses.nextElement()
          if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
            return address.hostAddress ?: "Unknown"
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return "Unknown"
  }
  
  override fun onDestroy() {
    super.onDestroy()
    stopReceiver()
  }
}
