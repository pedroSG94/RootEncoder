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

package com.pedro.rtmp.utils.socket

import android.util.Log
import com.pedro.common.TimeUtils
import java.io.*
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection

/**
 * Created by pedro on 5/4/22.
 */
class TcpTunneledSocket(private val host: String, private val port: Int, private val secured: Boolean): RtmpSocket() {

  private val TAG = "TcpTunneledSocket"

  private val headers = mapOf(
    "Content-Type" to "application/x-fcs",
    "User-Agent" to "Shockwave Flash"
  )
  private var connectionId: String = ""
  private var connected = false
  private var index = AtomicLong(0)
  private var output = ByteArrayOutputStream()
  private var input = ByteArrayInputStream(byteArrayOf())
  private val sync = Any()
  private var storedPackets = 0
  //send video/audio packets in packs of 10 on each HTTP request.
  private val maxStoredPackets = 10

  override fun getOutStream(): OutputStream = output

  override fun getInputStream(): InputStream {
    synchronized(sync) {
      val start = TimeUtils.getCurrentTimeMillis()
      while (input.available() <= 1 && connected) {
        val i = index.addAndGet(1)
        val bytes = requestRead("idle/$connectionId/$i", secured)
        input = ByteArrayInputStream(bytes, 1, bytes.size)
        if (TimeUtils.getCurrentTimeMillis() - start >= timeout) {
          throw SocketTimeoutException("couldn't receive a valid packet")
        }
      }
    }
    return input
  }

  override fun flush(isPacket: Boolean) {
    synchronized(sync) {
      if (isPacket && storedPackets < maxStoredPackets) {
        storedPackets++
        return
      }
      if (!connected) return
      val i = index.addAndGet(1)
      val bytes = output.toByteArray()
      output.reset()
      requestWrite("send/$connectionId/$i", secured, bytes)
      storedPackets = 0
    }
  }

  override fun connect() {
    synchronized(sync) {
      try {
        //optional in few servers
        requestWrite("fcs/ident2", secured, byteArrayOf(0x00))
      } catch (ignored: IOException) { }
      try {
        val openResult = requestRead("open/1", secured)
        connectionId = String(openResult).trimIndent()
        requestWrite("idle/$connectionId/${index.get()}", secured, byteArrayOf(0x00))
        connected = true
        Log.i(TAG, "Connection success")
      } catch (e: IOException) {
        Log.e(TAG, "Connection failed: ${e.message}")
        connected = false
      }
    }
  }

  override fun close() {
    Log.i(TAG, "closing tunneled socket...")
    connected = false
    synchronized(sync) {
      Thread {
        try {
          requestWrite("close/$connectionId", secured, byteArrayOf(0x00))
          Log.i(TAG, "Close success")
        } catch (e: IOException) {
          Log.e(TAG, "Close request failed: ${e.message}")
        } finally {
          index.set(0)
          connectionId = ""
        }
      }.start()
    }
  }

  override fun isConnected(): Boolean = connected

  override fun isReachable(): Boolean = connected

  @Throws(IOException::class)
  private fun requestWrite(path: String, secured: Boolean, data: ByteArray) {
    val socket = configureSocket(path, secured)
    try {
      socket.connect()
      socket.outputStream.write(data)
      val bytes = socket.inputStream.readBytes()
      if (bytes.size > 1) input = ByteArrayInputStream(bytes, 1, bytes.size)
      val success = socket.responseCode == HttpURLConnection.HTTP_OK
      if (!success) throw IOException("send packet failed: ${socket.responseMessage}")
    } finally {
      socket.disconnect()
    }
  }

  @Throws(IOException::class)
  private fun requestRead(path: String, secured: Boolean): ByteArray {
    val socket = configureSocket(path, secured)
    try {
      socket.connect()
      val data = socket.inputStream.readBytes()
      val success = socket.responseCode == HttpURLConnection.HTTP_OK
      if (!success) throw IOException("receive packet failed: ${socket.responseMessage}")
      return data
    } finally {
      socket.disconnect()
    }
  }

  private fun configureSocket(path: String, secured: Boolean): HttpURLConnection {
    val schema = if (secured) "https" else "http"
    val url = URL("$schema://$host:$port/$path")
    val socket = if (secured) {
      url.openConnection() as HttpsURLConnection
    } else {
      url.openConnection() as HttpURLConnection
    }
    Log.i(TAG, "open: $url")
    socket.requestMethod = "POST"
    headers.forEach { (key, value) ->
      socket.addRequestProperty(key, value)
    }
    socket.doOutput = true
    socket.connectTimeout = timeout
    socket.readTimeout = timeout
    return socket
  }
}