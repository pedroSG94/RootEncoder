package com.pedro.rtmp.rtmp

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.rtmp.message.RtmpMessage
import com.pedro.rtmp.rtmp.message.command.Command
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtmp.utils.CreateSSLSocket
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.regex.Pattern

/**
 * Created by pedro on 8/04/21.
 */
class RtmpClient(private val connectCheckerRtmp: ConnectCheckerRtmp) {

  private val TAG = "RtmpClient"
  private val rtmpUrlPattern = Pattern.compile("^rtmps?://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$")

  private var connectionSocket: Socket? = null
  private var reader: BufferedInputStream? = null
  private var writer: BufferedOutputStream? = null
  private var thread: HandlerThread? = null
  private val commandsManager = CommandsManager()

  private var isStreaming = false
  private var tlsEnabled = false

  fun setAuthorization(user: String?, password: String?) {
    commandsManager.setAuth(user, password)
  }

  fun connect(url: String?) {
    if (url == null) {
      connectCheckerRtmp.onConnectionFailedRtmp(
          "Endpoint malformed, should be: rtmp://ip:port/appname/streamname")
      return
    }
    val rtmpMatcher = rtmpUrlPattern.matcher(url)
    if (rtmpMatcher.matches()) {
      tlsEnabled = rtmpMatcher.group(0).startsWith("rtmps")
    } else {
      connectCheckerRtmp.onConnectionFailedRtmp(
          "Endpoint malformed, should be: rtmp://ip:port/appname/streamname")
      return
    }

    commandsManager.host = rtmpMatcher.group(1) ?: ""
    val portStr = rtmpMatcher.group(2)
    commandsManager.port = portStr?.toInt() ?: 1935
    commandsManager.appName = getAppName(rtmpMatcher.group(3) ?: "", rtmpMatcher.group(4) ?: "")
    commandsManager.streamName = getStreamName(rtmpMatcher.group(4) ?: "")
    commandsManager.tcUrl = getTcUrl((rtmpMatcher.group(0) ?: "").substring(0, (rtmpMatcher.group(0)
        ?: "").length - commandsManager.streamName.length))

    isStreaming = true
    thread = HandlerThread(TAG)
    thread?.start()
    thread?.let {
      val h = Handler(it.looper)
      h.post {
        try {
          if (!tlsEnabled) {
            connectionSocket = Socket()
            val socketAddress: SocketAddress = InetSocketAddress(commandsManager.host, commandsManager.port)
            connectionSocket?.connect(socketAddress, 5000)
          } else {
            connectionSocket = CreateSSLSocket.createSSlSocket(commandsManager.host, commandsManager.port)
            if (connectionSocket == null) throw IOException("Socket creation failed")
          }

          val reader = BufferedInputStream(connectionSocket?.getInputStream())
          val writer = BufferedOutputStream(connectionSocket?.getOutputStream())
          this.reader = reader
          this.writer = writer

          val handshake = Handshake()
          handshake.sendHandshake(reader, writer)
          commandsManager.connect("", writer)
          writer.flush()
          val connectResponse = commandsManager.getCommandResponse("connect", reader)
          if (connectResponse.name == "_result") {
            commandsManager.createStream(writer)
            val createStreamResponse = commandsManager.getCommandResponse("createStream", reader)
            val n = createStreamResponse.data[3] as AmfNumber
            Log.e("Pedro", "streamId: ${n.value}")
          }
        } catch (e: Exception) {
          Log.e(TAG, "connection error", e)
          connectCheckerRtmp.onConnectionFailedRtmp("Error configure stream, ${e.message}")
          return@post
        }
      }
    }
  }

  private fun getAppName(app: String, name: String): String {
    return if (!name.contains("/")) {
      app
    } else {
      app + "/" + name.substring(0, name.indexOf("/"))
    }
  }

  private fun getStreamName(name: String): String {
    return if (!name.contains("/")) {
      name
    } else {
      name.substring(name.indexOf("/") + 1)
    }
  }

  private fun getTcUrl(url: String): String {
    return if (url.endsWith("/")) {
      url.substring(0, url.length - 1)
    } else {
      url
    }
  }
}