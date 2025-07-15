package com.pedro.whip.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


/**
 * Created by pedro on 8/7/25.
 */
object Requests {
  fun makeRequest(
    endpoint: String, method: String,
    headers: Map<String, String>, body: String?,
    timeout: Int, secured: Boolean
  ): RequestResponse {
    val url = URL(endpoint)
    val socket = if (secured) url.openConnection() as HttpsURLConnection else url.openConnection() as HttpURLConnection
    socket.requestMethod = method
    headers.forEach { (key, value) -> socket.addRequestProperty(key, value) }
    socket.doOutput = true
    socket.connectTimeout = timeout
    socket.readTimeout = timeout

    try {
      socket.connect()
      if (body != null) socket.outputStream.write(body.toByteArray())
      Log.i("Requests", "$method, code: ${socket.responseCode}\nheaders: $headers\nbody: $body")
      val bytes = socket.inputStream.readBytes()
      val responseHeaders = mutableMapOf<String, String>()
      socket.headerFields.forEach {
        try {
          responseHeaders.put(it.key, it.value.joinToString(", "))
        } catch (_: Exception){}
      }
      val bodyResult = String(bytes)
      return RequestResponse(socket.responseCode, responseHeaders, bodyResult)
    } catch (_ : Exception) {
      return RequestResponse(-1, emptyMap(), "")
    } finally {
      socket.disconnect()
    }
  }
}