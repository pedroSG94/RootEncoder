package com.pedro.whip.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager


/**
 * Created by pedro on 8/7/25.
 */
object Requests {
  fun makeRequest(
    endpoint: String, method: String,
    headers: Map<String, String>, body: String?,
    timeout: Int, secured: Boolean, certificates: TrustManager?
  ): RequestResponse {
    val url = URL(endpoint)
    val socket = if (secured) url.openConnection() as HttpsURLConnection else url.openConnection() as HttpURLConnection
    socket.requestMethod = method
    headers.forEach { (key, value) -> socket.addRequestProperty(key, value) }
    socket.doOutput = true
    socket.connectTimeout = timeout
    socket.readTimeout = timeout
    if (socket is HttpsURLConnection && certificates != null) {
      val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(certificates), null)
      }
      socket.sslSocketFactory = sslContext.socketFactory
    }

    try {
      socket.connect()
      if (body != null) socket.outputStream.write(body.toByteArray())
      val code = socket.responseCode
      Log.i("Requests", "$method, code: $code\nheaders: $headers\nbody: $body")
      val stream = if (code in 200..399) socket.inputStream else socket.errorStream
      val bytes = stream?.readBytes() ?: ByteArray(0)
      val responseHeaders = mutableMapOf<String, String>()
      socket.headerFields.forEach {
        try {
          responseHeaders[it.key] = it.value.joinToString(", ")
        } catch (_: Exception){}
      }
      val bodyResult = String(bytes)
      return RequestResponse(code, responseHeaders, bodyResult)
    } catch (e : Exception) {
      Log.e("Requests", "Error", e)
      return RequestResponse(-1, emptyMap(), "")
    } finally {
      socket.disconnect()
    }
  }
}