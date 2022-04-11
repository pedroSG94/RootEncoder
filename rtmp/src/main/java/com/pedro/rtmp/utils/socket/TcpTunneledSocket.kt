package com.pedro.rtmp.utils.socket

import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
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
  private var index = AtomicLong(0)
  private var output = ByteArrayOutputStream()
  private var input = ByteArrayInputStream(byteArrayOf())
  private val sync = Any()

  override fun getOutStream(): OutputStream = output

  override fun getInputStream(): InputStream {
    synchronized(sync) {
      while (input.available() <= 1) {
        val i = index.addAndGet(1)
        val bytes = requestRead("idle/$connectionId/$i", secured)
        input = ByteArrayInputStream(bytes, 1, bytes.size)
      }
    }
    return input
  }

  override fun flush() {
    synchronized(sync) {
      val i = index.addAndGet(1)
      val bytes = output.toByteArray()
      output = ByteArrayOutputStream()
      requestWrite("send/$connectionId/$i", secured, bytes)
    }
  }

  override fun connect() {
    synchronized(sync) {
      try {
        requestWrite("fcs/ident2", secured, byteArrayOf(0x00))
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
    synchronized(sync) {
      try {
        requestWrite("close/$connectionId", secured, byteArrayOf(0x00))
        Log.i(TAG, "Close success")
      } catch (e: IOException) {
        Log.e(TAG, "Close request failed: ${e.message}")
        connected = false
      } finally {
        index.set(0)
        connectionId = ""
        connected = false
      }
    }
  }

  override fun isConnected(): Boolean = connected

  override fun isReachable(): Boolean = connected

  @Throws(IOException::class)
  private fun requestWrite(path: String, secured: Boolean,
    data: ByteArray) {
    val socket = configureSocket(path, secured)
    socket.connect()
    socket.outputStream.write(data)
    //necessary to improve speed
    socket.inputStream.readBytes()
    val success = socket.responseCode == HttpURLConnection.HTTP_OK
    socket.disconnect()
    if (!success) throw IOException("send packet failed: ${socket.responseMessage}")
  }

  @Throws(IOException::class)
  private fun requestRead(path: String, secured: Boolean): ByteArray {
    val socket = configureSocket(path, secured)
    socket.connect()
    val data = socket.inputStream.readBytes()
    val success = socket.responseCode == HttpURLConnection.HTTP_OK
    socket.disconnect()
    if (!success) throw IOException("receive packet failed: ${socket.responseMessage}")
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
    Log.i(TAG, "open: $url")
    socket.requestMethod = "POST"
    headers.forEach { (key, value) ->
      socket.addRequestProperty(key, value)
    }
    socket.doOutput = true
    socket.doInput = true
    socket.connectTimeout = 5000
    socket.readTimeout = 5000
    return socket
  }
}