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
import com.pedro.common.readUInt16
import com.pedro.common.readUInt24
import com.pedro.common.readUInt32
import com.pedro.common.readUInt32LittleEndian
import com.pedro.common.readUntil
import com.pedro.common.writeUInt16
import com.pedro.common.writeUInt24
import com.pedro.common.writeUInt32
import com.pedro.common.writeUInt32LittleEndian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  private val timeout = 5000
  private var connectionId: String = ""
  private var connected = false
  private var index = AtomicLong(0)
  private var output = ByteArrayOutputStream()
  private var input = ByteArrayInputStream(byteArrayOf())
  private val sync = Any()
  private var storedPackets = 0
  //send video/audio packets in packs of 10 on each HTTP request.
  private val maxStoredPackets = 10

  private fun getInputStream(requiredBytes: Int): InputStream {
    if (input.available() >= requiredBytes) return input
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

  @Throws(IOException::class)
  private fun requestWrite(path: String, secured: Boolean, data: ByteArray) {
    val socket = configureSocket(path, secured)
    try {
      socket.connect()
      socket.outputStream.write(data)
      val bytes = socket.inputStream.readBytes()
      if (bytes.size > 1) input = ByteArrayInputStream(bytes, 1, bytes.size)
      val success = socket.responseCode == HttpURLConnection.HTTP_OK
      if (!success) throw IOException("send packet failed: ${socket.responseMessage}, broken pipe")
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
      if (!success) throw IOException("receive packet failed: ${socket.responseMessage}, broken pipe")
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

  override suspend fun flush(isPacket: Boolean) {
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

  override suspend fun connect() {
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

  override suspend fun close() {
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

  override suspend fun write(b: Int) = withContext(Dispatchers.IO) {
    output.write(b)
  }

  override suspend fun write(b: ByteArray) = withContext(Dispatchers.IO) {
    output.write(b)
  }

  override suspend fun write(b: ByteArray, offset: Int, size: Int) {
    output.write(b, offset, size)
  }

  override suspend fun writeUInt16(b: Int) {
    output.writeUInt16(b)
  }

  override suspend fun writeUInt24(b: Int) {
    output.writeUInt24(b)
  }

  override suspend fun writeUInt32(b: Int) {
    output.writeUInt32(b)
  }

  override suspend fun writeUInt32LittleEndian(b: Int) {
    output.writeUInt32LittleEndian(b)
  }

  override suspend fun read(): Int = withContext(Dispatchers.IO) {
    getInputStream(1).read()
  }

  override suspend fun readUInt16(): Int {
    return getInputStream(1).readUInt16()
  }

  override suspend fun readUInt24(): Int {
    return getInputStream(1).readUInt24()
  }

  override suspend fun readUInt32(): Int {
    return getInputStream(1).readUInt32()
  }

  override suspend fun readUInt32LittleEndian(): Int {
    return getInputStream(1).readUInt32LittleEndian()
  }

  override suspend fun readUntil(b: ByteArray) {
    return getInputStream(b.size).readUntil(b)
  }
}