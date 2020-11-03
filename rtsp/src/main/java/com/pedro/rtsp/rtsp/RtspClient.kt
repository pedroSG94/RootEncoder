package com.pedro.rtsp.rtsp

import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtsp.utils.CreateSSLSocket.createSSlSocket
import kotlinx.coroutines.*
import java.io.*
import java.lang.Runnable
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.regex.Pattern

/**
 * Created by pedro on 10/02/17.
 */
class RtspClient(private val connectCheckerRtsp: ConnectCheckerRtsp) {

  private val TAG = "RtspClient"

  val host: String?
    get() = commandsManager.host
  val port: Int
    get() = commandsManager.port
  val path: String?
    get() = commandsManager.path

  //sockets objects
  private var connectionSocket: Socket? = null
  private var reader: BufferedReader? = null
  private var writer: BufferedWriter? = null
  private var thread: Job? = null

  @Volatile
  var isStreaming = false
    private set

  //for secure transport
  private var tlsEnabled = false
  private val rtspSender: RtspSender = RtspSender(connectCheckerRtsp)
  private var url: String? = null
  private val commandsManager: CommandsManager = CommandsManager()
  private var doingRetry = false
  private var numRetry = 0
  private var reTries = 0
  private val handler: Handler = Handler(Looper.getMainLooper())
  private var runnable: Runnable? = null

  val droppedAudioFrames: Long
    get() = rtspSender.droppedAudioFrames
  val droppedVideoFrames: Long
    get() = rtspSender.droppedVideoFrames

  val cacheSize: Int
    get() = rtspSender.getCacheSize()
  val sentAudioFrames: Long
    get() = rtspSender.getSentAudioFrames()
  val sentVideoFrames: Long
    get() = rtspSender.getSentVideoFrames()

  companion object {
    private val rtspUrlPattern = Pattern.compile("^rtsps?://([^/:]+)(?::(\\d+))*/([^/]+)/?([^*]*)$")
  }

  fun setOnlyAudio(onlyAudio: Boolean) {
    commandsManager.isOnlyAudio = onlyAudio
  }

  fun setProtocol(protocol: Protocol) {
    commandsManager.protocol = protocol
  }

  fun setAuthorization(user: String?, password: String?) {
    commandsManager.setAuth(user, password)
  }

  fun setReTries(reTries: Int) {
    numRetry = reTries
    this.reTries = reTries
  }

  fun shouldRetry(reason: String): Boolean {
    val validReason = doingRetry && !reason.contains("Endpoint malformed")
    return validReason && reTries > 0
  }

  fun setUrl(url: String?) {
    this.url = url
  }

  fun setSampleRate(sampleRate: Int) {
    commandsManager.sampleRate = sampleRate
  }

  fun setSPSandPPS(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {
    commandsManager.setVideoInfo(sps, pps, vps)
  }

  fun setIsStereo(isStereo: Boolean) {
    commandsManager.isStereo = isStereo
  }

  @JvmOverloads
  fun connect(isRetry: Boolean = false) {
    if (!isRetry) doingRetry = true
    if (url == null) {
      isStreaming = false
      connectCheckerRtsp.onConnectionFailedRtsp(
          "Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
      return
    }
    if (!isStreaming) {
      val rtspMatcher = rtspUrlPattern.matcher(url!!)
      if (rtspMatcher.matches()) {
        tlsEnabled = (rtspMatcher.group(0) ?: "").startsWith("rtsps")
      } else {
        isStreaming = false
        connectCheckerRtsp.onConnectionFailedRtsp(
            "Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
        return
      }
      val host = rtspMatcher.group(1)
      val port: Int = (rtspMatcher.group(2) ?: "554").toInt()
      val path = "/" + rtspMatcher.group(3) + "/" + rtspMatcher.group(4)
      commandsManager.setUrl(host, port, path)
      rtspSender.setSocketsInfo(commandsManager.protocol,
          commandsManager.videoClientPorts, commandsManager.audioClientPorts)
      rtspSender.setAudioInfo(commandsManager.sampleRate)
      if (!commandsManager.isOnlyAudio) {
        if (commandsManager.sps != null && commandsManager.pps != null) {
          rtspSender.setVideoInfo(commandsManager.sps!!, commandsManager.pps!!,
              commandsManager.vps)
        } else {
          connectCheckerRtsp.onConnectionFailedRtsp("sps or pps is null")
        }
      }
      thread = GlobalScope.launch(Dispatchers.IO) {
        try {
          if (!tlsEnabled) {
            connectionSocket = Socket()
            val socketAddress: SocketAddress = InetSocketAddress(commandsManager.host, commandsManager.port)
            connectionSocket?.connect(socketAddress, 5000)
          } else {
            connectionSocket = createSSlSocket(commandsManager.host ?: "",
                commandsManager.port)
            if (connectionSocket == null) throw IOException("Socket creation failed")
          }
          connectionSocket?.sendBufferSize = 1
          connectionSocket?.soTimeout = 5000
          reader = BufferedReader(InputStreamReader(connectionSocket?.getInputStream()))
          val outputStream = connectionSocket?.getOutputStream()
          writer = BufferedWriter(OutputStreamWriter(outputStream))
          writer?.write(commandsManager.createOptions())
          writer?.flush()
          commandsManager.getResponse(reader, connectCheckerRtsp, isAudio = false, checkStatus = false)
          writer?.write(commandsManager.createAnnounce())
          writer?.flush()
          //check if you need credential for stream, if you need try connect with credential
          val response = commandsManager.getResponse(reader, connectCheckerRtsp, isAudio = false, checkStatus = false)
              ?: ""
          val status = commandsManager.getResponseStatus(response)
          if (status == 403) {
            connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, access denied")
            Log.e(TAG, "Response 403, access denied")
            cancel()
          } else if (status == 401) {
            if (commandsManager.user == null || commandsManager.password == null) {
              connectCheckerRtsp.onAuthErrorRtsp()
              cancel()
            } else {
              writer?.write(commandsManager.createAnnounceWithAuth(response))
              writer?.flush()
              val statusAuth = commandsManager.getResponseStatus(commandsManager.getResponse(reader, connectCheckerRtsp, isAudio = false, checkStatus = false)
                  ?: "")
              when (statusAuth) {
                401 -> {
                  connectCheckerRtsp.onAuthErrorRtsp()
                  cancel()
                }
                200 -> {
                  connectCheckerRtsp.onAuthSuccessRtsp()
                }
                else -> {
                  connectCheckerRtsp.onConnectionFailedRtsp(
                      "Error configure stream, announce with auth failed")
                }
              }
            }
          } else if (status != 200) {
            connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, announce failed")
          }
          writer?.write(commandsManager.createSetup(commandsManager.trackAudio))
          writer?.flush()
          commandsManager.getResponse(reader, connectCheckerRtsp, isAudio = true, checkStatus = true)
          if (!commandsManager.isOnlyAudio) {
            writer?.write(commandsManager.createSetup(commandsManager.trackVideo))
            writer?.flush()
            commandsManager.getResponse(reader, connectCheckerRtsp, isAudio = false, checkStatus = true)
          }
          writer?.write(commandsManager.createRecord())
          writer?.flush()
          commandsManager.getResponse(reader, connectCheckerRtsp, isAudio = false, checkStatus = true)
          outputStream?.let {
            rtspSender.setDataStream(it, commandsManager.host ?: "")
          }
          val videoPorts = commandsManager.videoServerPorts
          val audioPorts = commandsManager.audioServerPorts
          if (!commandsManager.isOnlyAudio) {
            rtspSender.setVideoPorts(videoPorts[0], videoPorts[1])
          }
          rtspSender.setAudioPorts(audioPorts[0], audioPorts[1])
          rtspSender.start()
          isStreaming = true
          reTries = numRetry
          connectCheckerRtsp.onConnectionSuccessRtsp()
        } catch (e: IOException) {
          Log.e(TAG, "connection error", e)
          connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, " + e.message)
          isStreaming = false
        } catch (e: NullPointerException) {
          Log.e(TAG, "connection error", e)
          connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, " + e.message)
          isStreaming = false
        } catch (e: IllegalStateException) {
          Log.e(TAG, "connection error", e)
          connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, " + e.message)
          isStreaming = false
        }
      }
    }
  }

  fun disconnect() {
    runnable?.let { handler.removeCallbacks(it) }
    disconnect(true)
  }

  private fun disconnect(clear: Boolean) {
    runBlocking {
      writer?.flush()
      if (isStreaming) rtspSender.stop()
      if (!doingRetry) isStreaming = false
      thread?.cancelAndJoin()
      val teardown = async(Dispatchers.IO) {
        try {
          writer?.write(commandsManager.createTeardown())
          writer?.flush()
          if (clear) {
            commandsManager.clear()
          } else {
            commandsManager.retryClear()
          }
          connectionSocket?.close()
          writer = null
          connectionSocket = null
        } catch (e: IOException) {
          if (clear) {
            commandsManager.clear()
          } else {
            commandsManager.retryClear()
          }
          Log.e(TAG, "disconnect error", e)
        }
      }
      teardown.await()
      if (clear) {
        reTries = numRetry
        doingRetry = false
        connectCheckerRtsp.onDisconnectRtsp()
      }
    }
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (!commandsManager.isOnlyAudio) {
      rtspSender.sendVideoFrame(h264Buffer, info)
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspSender.sendAudioFrame(aacBuffer, info)
  }

  fun hasCongestion(): Boolean {
    return rtspSender.hasCongestion()
  }

  fun reConnect(delay: Long) {
    reTries--
    disconnect(false)
    runnable = Runnable { connect(true) }
    runnable?.let { handler.postDelayed(it, delay) }
  }

  fun resetSentAudioFrames() {
    rtspSender.resetSentAudioFrames()
  }

  fun resetSentVideoFrames() {
    rtspSender.resetSentVideoFrames()
  }

  fun resetDroppedAudioFrames() {
    rtspSender.resetDroppedAudioFrames()
  }

  fun resetDroppedVideoFrames() {
    rtspSender.resetDroppedVideoFrames()
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    rtspSender.resizeCache(newSize)
  }

  fun setLogs(enable: Boolean) {
    rtspSender.setLogs(enable)
  }
}