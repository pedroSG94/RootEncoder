package com.pedro.rtmp.utils.socket

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Created by pedro on 5/4/22.
 */
class TcpTunneledSocket(private val host: String, private val port: Int, private val secured: Boolean): RtpSocket() {

  private val TAG = "TcpTunneledSocket"

  private val headers = mapOf(
    "Content-Type" to "application/x-fcs",
    "User-Agent" to "Shockwave Flash"
  )
  private var connectionId: String = ""
  private var connected = false
  private var index = 0

  override fun getOutStream(): OutputStream? {
    return null
  }

  override fun getInputStream(): InputStream? {
    return null
  }

  override fun connect() {
    try {
      requestWrite("/fcs/ident2", secured, byteArrayOf(0x00))
      val openResult = requestRead("/open/1", secured)
      connectionId = String(openResult)
      requestWrite("/idle/$connectionId/$index", secured, byteArrayOf(0x00))
      connected = true
      Log.i(TAG,"Connection success")
    } catch (e: IOException) {
      Log.e(TAG,"Connection failed: ${e.message}")
      connected = false
    }
  }

  override fun close() {
    try {
      requestWrite("/close/$connectionId", secured, byteArrayOf(0x00))
      Log.i(TAG,"Close success")
    } catch (e: IOException) {
      Log.e(TAG,"Close request failed: ${e.message}")
      connected = false
    } finally {
      index = 0
      connectionId = ""
      connected = false
    }
  }

  override fun isConnected(): Boolean = connected

  override fun isReachable(): Boolean = connected

  @Throws(IOException::class)
  fun write(bytes: ByteArray) {
    index++
    requestWrite("/send/$connectionId/$index", secured, bytes)
  }

  @Throws(IOException::class)
  fun read(): ByteArray {
    index++
    return requestRead("/idle/$connectionId/$index", secured)
  }

  @Throws(IOException::class)
  private fun requestWrite(path: String, secured: Boolean,
    data: ByteArray) {
    val socket = configureSocket(path, secured)
    socket.connect()
    socket.outputStream.write(data)
    val success = socket.responseCode == 200
    Log.i(TAG, "write response: ${socket.responseCode}")
    socket.disconnect()
    if (!success) throw IOException("send packet failed")
  }

  @Throws(IOException::class)
  private fun requestRead(path: String, secured: Boolean): ByteArray {
    val socket = configureSocket(path, secured)
    socket.connect()
    val data = socket.inputStream.readBytes()
    val success = socket.responseCode == 200
    Log.i(TAG, "read response: ${socket.responseCode}")
    socket.disconnect()
    if (!success) throw IOException("receive packet failed")
    return data
  }

  private fun configureSocket(path: String, secured: Boolean): HttpURLConnection {
    val schema = if (secured) "https" else "http"
    val url = URL("$schema://$host:$port/$path")
    val socket = if (secured) {
      url.openConnection() as HttpsURLConnection
    } else {
      url.openConnection() as HttpURLConnection
    }
    socket.requestMethod = "POST"
    headers.forEach { (key, value) ->
      socket.addRequestProperty(key, value)
    }
    socket.doOutput = true
    socket.doInput = true
    socket.useCaches = false
    socket.connectTimeout = 5000
    socket.readTimeout = 5000
    return socket
  }
}