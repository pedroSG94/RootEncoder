package com.pedro.rtmp.rtmp

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.message.*
import com.pedro.rtmp.rtmp.message.command.Command
import com.pedro.rtmp.rtmp.message.control.UserControl
import com.pedro.rtmp.utils.AuthUtil
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtmp.utils.CreateSSLSocket
import com.pedro.rtmp.utils.RtmpConfig
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.*
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
  private val rtmpSender = RtmpSender()

  @Volatile
  var isStreaming = false
    private set

  private var url: String? = null
  private var tlsEnabled = false

  private var doingRetry = false
  private var numRetry = 0
  private var reTries = 0
  private val handler: Handler = Handler(Looper.getMainLooper())
  private var runnable: Runnable? = null

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

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    commandsManager.setAudioInfo(sampleRate, isStereo)
  }

  fun setSPSandPPS(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {
    Log.i(TAG, "send sps and pps")
    rtmpSender.setVideoInfo(sps, pps, vps)
  }

  fun setVideoResolution(width: Int, height: Int) {
    commandsManager.setVideoResolution(width, height)
  }

  fun connect(url: String?, isRetry: Boolean = false) {
    if (!isRetry) doingRetry = true
    if (url == null) {
      isStreaming = false
      connectCheckerRtmp.onConnectionFailedRtmp(
          "Endpoint malformed, should be: rtmp://ip:port/appname/streamname")
      return
    }
    if (!isStreaming || isRetry) {
      this.url = url
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
      commandsManager.tcUrl = getTcUrl((rtmpMatcher.group(0)
          ?: "").substring(0, (rtmpMatcher.group(0)
          ?: "").length - commandsManager.streamName.length))

      isStreaming = true
      thread = HandlerThread(TAG)
      thread?.start()
      thread?.let {
        val h = Handler(it.looper)
        h.post {
          try {
            if (!establishConnection()) {
              connectCheckerRtmp.onConnectionFailedRtmp("Handshake failed")
              return@post
            }
            commandsManager.sendConnect("", writer!!)
            while (!Thread.interrupted()) {
              //Handle all command received and send response for it. Return true if connection success received
              handleMessages(reader!!, writer!!)
            }
          } catch (e: Exception) {
            Log.e(TAG, "connection error", e)
            connectCheckerRtmp.onConnectionFailedRtmp("Error configure stream, ${e.message}")
            return@post
          }
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

  private fun establishConnection(): Boolean {
    if (!tlsEnabled) {
      connectionSocket = Socket()
      val socketAddress: SocketAddress = InetSocketAddress(commandsManager.host, commandsManager.port)
      connectionSocket?.connect(socketAddress, 5000)
    } else {
      connectionSocket = CreateSSLSocket.createSSlSocket(commandsManager.host, commandsManager.port)
      if (connectionSocket == null) throw IOException("Socket creation failed")
    }
    connectionSocket?.soTimeout = 5000
    val reader = BufferedInputStream(connectionSocket?.getInputStream())
    val writer = BufferedOutputStream(connectionSocket?.getOutputStream())
    this.reader = reader
    this.writer = writer
    val timestamp = System.currentTimeMillis() / 1000
    val handshake = Handshake()
    if (!handshake.sendHandshake(reader, writer)) return false
    commandsManager.timestamp = timestamp.toInt()
    return true
  }

  /**
   * Read all messages from server and response to it
   */
  private fun handleMessages(input: InputStream, output: OutputStream) {
    val message = commandsManager.readMessageResponse(input)
    when (message.getType()) {
      MessageType.SET_CHUNK_SIZE -> {
        val setChunkSize = message as SetChunkSize
        RtmpConfig.chunkSize = setChunkSize.chunkSize
        Log.i(TAG, "chunk size configured to ${setChunkSize.chunkSize}")
      }
      MessageType.ACKNOWLEDGEMENT -> {
        val acknowledgement = message as Acknowledgement
      }
      MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE -> {
        val windowAcknowledgementSize = message as WindowAcknowledgementSize
        RtmpConfig.acknowledgementWindowSize = windowAcknowledgementSize.acknowledgementWindowSize
      }
      MessageType.SET_PEER_BANDWIDTH -> {
        val setPeerBandwidth = message as SetPeerBandwidth
        commandsManager.sendWindowAcknowledgementSize(output)
      }
      MessageType.ABORT -> {
        val abort = message as Abort
      }
      MessageType.AGGREGATE -> {
        val aggregate = message as Aggregate
      }
      MessageType.USER_CONTROL -> {
        val userControl = message as UserControl
        when (val type = userControl.type) {
          UserControl.Type.PING_REQUEST -> {
            val pong = UserControl(UserControl.Type.PONG_REPLY, userControl.event)
            pong.writeHeader(output)
            pong.writeBody(output)
            output.flush()
          }
          else -> {
            Log.i(TAG, "user control command $type ignored")
          }
        }
      }
      MessageType.COMMAND_AMF0, MessageType.COMMAND_AMF3 -> {
        val command = message as Command
        val commandName = commandsManager.sessionHistory.getName(command.commandId)
        when (command.name) {
          "_result" -> {
            when (commandName) {
              "connect" -> {
                if (commandsManager.onAuth) {
                  connectCheckerRtmp.onAuthSuccessRtmp()
                  commandsManager.onAuth = false
                }
                commandsManager.createStream(output)
              }
              "createStream" -> {
                commandsManager.streamId = (command.data[3] as AmfNumber).value.toInt()
                commandsManager.sendPublish(output)
              }
              else -> {
                Log.i(TAG, "success response received from ${commandName ?: "unknown command"}")
              }
            }
          }
          "_error" -> {
            when (val description = ((command.data[3] as AmfObject).getProperty("code") as AmfString).value) {
              "connect" -> {
                if (description.contains("reason=authfail") || description.contains("reason=nosuchuser")) {
                  connectCheckerRtmp.onAuthErrorRtmp()
                } else if (commandsManager.user != null && commandsManager.password != null &&
                    description.contains("challenge=") && description.contains("salt=") //adobe response
                    || description.contains("nonce=")) { //llnw response
                  closeConnection()
                  establishConnection()
                  commandsManager.onAuth = true
                  if (description.contains("challenge=") && description.contains("salt=")) { //create adobe auth
                    val salt = AuthUtil.getSalt(description)
                    val challenge = AuthUtil.getChallenge(description)
                    val opaque = AuthUtil.getOpaque(description)
                    commandsManager.sendConnect(AuthUtil.getAdobeAuthUserResult(commandsManager.user
                        ?: "", commandsManager.password ?: "",
                        salt, challenge, opaque), output)
                  } else if (description.contains("nonce=")) { //create llnw auth
                    val nonce = AuthUtil.getNonce(description)
                    commandsManager.sendConnect(AuthUtil.getLlnwAuthUserResult(commandsManager.user
                        ?: "", commandsManager.password ?: "",
                        nonce, commandsManager.appName), output)
                  }
                } else if (description.contains("code=403")) {
                  if (description.contains("authmod=adobe")) {
                    closeConnection()
                    establishConnection()
                    Log.i(TAG, "sending auth mode adobe")
                    commandsManager.sendConnect("?authmod=adobe&user=${commandsManager.user}", output)
                  } else if (description.contains("authmod=llnw")) {
                    Log.i(TAG, "sending auth mode llnw")
                    commandsManager.sendConnect("?authmod=llnw&user=${commandsManager.user}", output)
                  }
                } else {
                  connectCheckerRtmp.onAuthErrorRtmp()
                }
              }
              else -> {
                connectCheckerRtmp.onConnectionFailedRtmp(description)
              }
            }
          }
          "onStatus" -> {
            try {
              when (val code = ((command.data[3] as AmfObject).getProperty("code") as AmfString).value) {
                "NetStream.Publish.Start" -> {
                  commandsManager.sendMetadata(output)
                  connectCheckerRtmp.onConnectionSuccessRtmp()
                  rtmpSender.start()
                }
                else -> {
                  Log.i(TAG, "onStatus $code response received from ${commandName ?: "unknown command"}")
                  connectCheckerRtmp.onConnectionFailedRtmp("onStatus: $code")
                }
              }
            } catch (e: Exception) {
              Log.e(TAG, "error parsing onStatus command", e)
            }
          }
          else -> {
            Log.i(TAG, "unknown ${command.name} response received from ${commandName ?: "unknown command"}")
          }
        }
      }
      MessageType.VIDEO, MessageType.AUDIO, MessageType.DATA_AMF0, MessageType.DATA_AMF3,
      MessageType.SHARED_OBJECT_AMF0, MessageType.SHARED_OBJECT_AMF3 -> {
        Log.e(TAG, "unimplemented response for ${message.getType()}. Ignored")
      }
    }
  }

  private fun closeConnection() {
    connectionSocket?.close()
    commandsManager.reset()
  }

  fun reConnect(delay: Long) {
    reTries--
    disconnect(false)
    runnable = Runnable {
      connect(url, true)
    }
    runnable?.let { handler.postDelayed(it, delay) }
  }

  fun disconnect() {
    runnable?.let { handler.removeCallbacks(it) }
    disconnect(true)
  }

  private fun disconnect(clear: Boolean) {
    closeConnection()
    if (isStreaming) rtmpSender.stop()
    thread?.looper?.thread?.interrupt()
    thread?.looper?.quit()
    thread?.quit()
    try {
      writer?.flush()
      thread?.join(100)
    } catch (e: Exception) {
    }
    if (clear) {
      reTries = numRetry
      doingRetry = false
      isStreaming = false
      connectCheckerRtmp.onDisconnectRtmp()
    }
  }
}