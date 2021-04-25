package com.pedro.rtsp.rtsp

import android.media.MediaCodec
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.pedro.rtsp.rtsp.commands.CommandsManager
import com.pedro.rtsp.rtsp.commands.Method
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtsp.utils.CreateSSLSocket.createSSlSocket
import com.pedro.rtsp.utils.RtpConstants
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Created by pedro on 10/02/17.
 */
open class RtspClient(private val connectCheckerRtsp: ConnectCheckerRtsp) {

  private val TAG = "RtspClient"

  //sockets objects
  private var connectionSocket: Socket? = null
  private var reader: BufferedReader? = null
  private var writer: BufferedWriter? = null
  private var thread: HandlerThread? = null
  private val semaphore = Semaphore(0)

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
    if (onlyAudio) {
      RtpConstants.trackAudio = 0
      RtpConstants.trackVideo = 1
    } else {
      RtpConstants.trackVideo = 0
      RtpConstants.trackAudio = 1
    }
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

  fun setSampleRate(sampleRate: Int) {
    commandsManager.sampleRate = sampleRate
  }

  fun setSPSandPPS(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {
    Log.i(TAG, "send sps and pps")
    commandsManager.setVideoInfo(sps, pps, vps)
    semaphore.release()
  }

  fun setIsStereo(isStereo: Boolean) {
    commandsManager.isStereo = isStereo
  }

  @JvmOverloads
  fun connect(url: String?, isRetry: Boolean = false) {
    if (!isRetry) doingRetry = true
    if (url == null) {
      isStreaming = false
      connectCheckerRtsp.onConnectionFailedRtsp("Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
      return
    }
    if (!isStreaming || isRetry) {
      this.url = url
      connectCheckerRtsp.onConnectionStartedRtsp(url)
      val rtspMatcher = rtspUrlPattern.matcher(url)
      if (rtspMatcher.matches()) {
        tlsEnabled = (rtspMatcher.group(0) ?: "").startsWith("rtsps")
      } else {
        isStreaming = false
        connectCheckerRtsp.onConnectionFailedRtsp("Endpoint malformed, should be: rtsp://ip:port/appname/streamname")
        return
      }
      val host = rtspMatcher.group(1) ?: ""
      val port: Int = (rtspMatcher.group(2) ?: "554").toInt()
      val streamName = if (rtspMatcher.group(4).isNullOrEmpty()) "" else "/" + rtspMatcher.group(4)
      val path = "/" + rtspMatcher.group(3) + streamName

      isStreaming = true
      thread = HandlerThread(TAG)
      thread?.start()
      thread?.let {
        val h = Handler(it.looper)
        h.post {
          try {
            commandsManager.setUrl(host, port, path)
            rtspSender.setSocketsInfo(commandsManager.protocol,
              commandsManager.videoClientPorts, commandsManager.audioClientPorts)
            rtspSender.setAudioInfo(commandsManager.sampleRate)
            if (!commandsManager.isOnlyAudio) {
              if (commandsManager.sps == null && commandsManager.pps == null) {
                semaphore.drainPermits()
                Log.i(TAG, "waiting for sps and pps")
                semaphore.tryAcquire(5000, TimeUnit.MILLISECONDS)
              }
              if (commandsManager.sps == null && commandsManager.pps == null) {
                connectCheckerRtsp.onConnectionFailedRtsp("sps or pps is null")
                return@post
              } else {
                rtspSender.setVideoInfo(commandsManager.sps!!, commandsManager.pps!!, commandsManager.vps)
              }
            }
            if (!tlsEnabled) {
              connectionSocket = Socket()
              val socketAddress: SocketAddress = InetSocketAddress(host, port)
              connectionSocket?.connect(socketAddress, 5000)
            } else {
              connectionSocket = createSSlSocket(host, port)
              if (connectionSocket == null) throw IOException("Socket creation failed")
            }
            connectionSocket?.soTimeout = 5000
            reader = BufferedReader(InputStreamReader(connectionSocket?.getInputStream()))
            val outputStream = connectionSocket?.getOutputStream()
            writer = BufferedWriter(OutputStreamWriter(outputStream))
            writer?.write(commandsManager.createOptions())
            writer?.flush()
            commandsManager.getResponse(reader, Method.OPTIONS)
            writer?.write(commandsManager.createAnnounce())
            writer?.flush()
            //check if you need credential for stream, if you need try connect with credential
            val announceResponse = commandsManager.getResponse(reader, Method.ANNOUNCE)
            when (announceResponse.status) {
              403 -> {
                connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, access denied")
                Log.e(TAG, "Response 403, access denied")
                return@post
              }
              401 -> {
                if (commandsManager.user == null || commandsManager.password == null) {
                  connectCheckerRtsp.onAuthErrorRtsp()
                  return@post
                } else {
                  writer?.write(commandsManager.createAnnounceWithAuth(announceResponse.text))
                  writer?.flush()
                  when (commandsManager.getResponse(reader, Method.ANNOUNCE).status) {
                    401 -> {
                      connectCheckerRtsp.onAuthErrorRtsp()
                      return@post
                    }
                    200 -> {
                      connectCheckerRtsp.onAuthSuccessRtsp()
                    }
                    else -> {
                      connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, announce with auth failed")
                      return@post
                    }
                  }
                }
              }
              200 -> {
                Log.i(TAG, "announce success")
              }
              else -> {
                connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, announce failed")
                return@post
              }
            }
            if (!commandsManager.isOnlyAudio) {
              writer?.write(commandsManager.createSetup(RtpConstants.trackVideo))
              writer?.flush()
              val setupVideoStatus = commandsManager.getResponse(reader, Method.SETUP).status
              if (setupVideoStatus != 200) {
                connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, setup video $setupVideoStatus")
                return@post
              }
            }
            writer?.write(commandsManager.createSetup(RtpConstants.trackAudio))
            writer?.flush()
            val setupAudioStatus = commandsManager.getResponse(reader, Method.SETUP).status
            if (setupAudioStatus != 200) {
              connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, setup audio $setupAudioStatus")
              return@post
            }
            writer?.write(commandsManager.createRecord())
            writer?.flush()
            val recordStatus = commandsManager.getResponse(reader, Method.RECORD).status
            if (recordStatus != 200) {
              connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, record $recordStatus")
              return@post
            }
            outputStream?.let { out ->
              rtspSender.setDataStream(out, host)
            }
            val videoPorts = commandsManager.videoServerPorts
            val audioPorts = commandsManager.audioServerPorts
            if (!commandsManager.isOnlyAudio) {
              rtspSender.setVideoPorts(videoPorts[0], videoPorts[1])
            }
            rtspSender.setAudioPorts(audioPorts[0], audioPorts[1])
            rtspSender.start()
            reTries = numRetry
            connectCheckerRtsp.onConnectionSuccessRtsp()
            handleServerCommands()
          } catch (e: Exception) {
            Log.e(TAG, "connection error", e)
            connectCheckerRtsp.onConnectionFailedRtsp("Error configure stream, " + e.message)
            return@post
          }
        }
      }
    }
  }

  private fun handleServerCommands() {
    //Read and print server commands received each 2 seconds
    while (!Thread.interrupted()) {
      try {
        Thread.sleep(2000)
        reader?.let { r ->
          if (r.ready()) {
            val command = commandsManager.getResponse(r)
            //Do something depend of command if required
          }
        }
      } catch (e: InterruptedException) { }
    }
  }

  fun disconnect() {
    runnable?.let { handler.removeCallbacks(it) }
    disconnect(true)
  }

  private fun disconnect(clear: Boolean) {
    if (isStreaming) rtspSender.stop()
    thread?.looper?.thread?.interrupt()
    thread?.looper?.quit()
    thread?.quit()
    try {
      writer?.flush()
      thread?.join(100)
    } catch (e: Exception) { }
    thread = HandlerThread(TAG)
    thread?.start()
    thread?.let {
      val h = Handler(it.looper)
      h.post {
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
          Log.i(TAG, "write teardown success")
        } catch (e: IOException) {
          if (clear) {
            commandsManager.clear()
          } else {
            commandsManager.retryClear()
          }
          Log.e(TAG, "disconnect error", e)
        }
      }
    }
    try {
      thread?.join(200) //wait finish teardown
      thread?.looper?.thread?.interrupt()
      thread?.looper?.quit()
      thread?.quit()
      writer?.flush()
      thread?.join(100)
      thread = null
      semaphore.release()
    } catch (e: Exception) { }
    if (clear) {
      reTries = numRetry
      doingRetry = false
      isStreaming = false
      connectCheckerRtsp.onDisconnectRtsp()
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

  @JvmOverloads
  fun reConnect(delay: Long, backupUrl: String? = null) {
    reTries--
    disconnect(false)
    runnable = Runnable {
      val reconnectUrl = backupUrl ?: url
      connect(reconnectUrl, true)
    }
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