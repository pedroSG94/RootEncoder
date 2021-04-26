package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.rtmp.amf.v0.*
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.*
import com.pedro.rtmp.rtmp.message.command.Command
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.rtmp.message.control.UserControl
import com.pedro.rtmp.rtmp.message.data.DataAmf0
import com.pedro.rtmp.utils.CommandSessionHistory
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtmp.utils.RtmpConfig
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 21/04/21.
 */
class CommandsManager {

  private val TAG = "CommandsManager"

  private val sessionHistory = CommandSessionHistory()
  var timestamp = 0
  private var commandId = 0
  private var streamId = 0
  var host = ""
  var port = 1935
  var appName = ""
  var streamName = ""
  var  tcUrl = ""
  private var user: String? = null
  private var password: String? = null
  private var onAuth = false

  private var width = 640
  private var height = 480
  private var sampleRate = 44100
  private var isStereo = true

  fun setVideoInfo(width: Int, height: Int) {
    this.width = width
    this.height = height
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
  }

  fun setAuth(user: String?, password: String?) {
    this.user = user
    this.password = password
  }

  private fun getCurrentTimestamp(): Int {
    return (System.currentTimeMillis() / 1000 - timestamp).toInt()
  }

  @Throws(IOException::class)
  fun connect(auth: String, output: OutputStream) {
    val connect = CommandAmf0("connect", ++commandId, streamId, getCurrentTimestamp(),
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION))
    val connectInfo = AmfObject()
    connectInfo.setProperty("app", appName + auth)
    connectInfo.setProperty("flashVer", "FMLE/3.0 (compatible; Lavf57.56.101)")
    connectInfo.setProperty("swfUrl", "")
    connectInfo.setProperty("tcUrl", tcUrl + auth)
    connectInfo.setProperty("fpad", false)
    connectInfo.setProperty("capabilities", 239.0)
    connectInfo.setProperty("audioCodecs", 3191.0)
    connectInfo.setProperty("videoCodecs", 252.0)
    connectInfo.setProperty("videoFunction", 1.0)
    connectInfo.setProperty("pageUrl", "")
    connectInfo.setProperty("objectEncoding", 0.0)
    connect.addData(connectInfo)

    connect.writeHeader(output)
    connect.writeBody(output)
    output.flush()
    sessionHistory.setPacket(commandId, "connect")
    Log.i(TAG, "send $connect")
  }

  fun createStream(output: OutputStream) {
    val releaseStream = CommandAmf0("releaseStream", ++commandId, streamId, getCurrentTimestamp(),
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM))
    releaseStream.addData(AmfNull())
    releaseStream.addData(AmfString(streamName))

    releaseStream.writeHeader(output)
    releaseStream.writeBody(output)
    sessionHistory.setPacket(commandId, "releaseStream")
    Log.i(TAG, "send $releaseStream")

    val fcPublish = CommandAmf0("FCPublish", ++commandId, streamId, getCurrentTimestamp(),
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM))
    fcPublish.addData(AmfNull())
    fcPublish.addData(AmfString(streamName))

    fcPublish.writeHeader(output)
    fcPublish.writeBody(output)
    sessionHistory.setPacket(commandId, "FCPublish")
    Log.i(TAG, "send $fcPublish")

    val createStream = CommandAmf0("createStream", ++commandId, streamId, getCurrentTimestamp(),
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION))
    createStream.addData(AmfNull())

    createStream.writeHeader(output)
    createStream.writeBody(output)
    output.flush()
    sessionHistory.setPacket(commandId, "createStream")
    Log.i(TAG, "send $createStream")
  }

  @Throws(IOException::class)
  fun readMessageResponse(input: InputStream): RtmpMessage {
    val message = RtmpMessage.getRtmpMessage(input)
    Log.i(TAG, "read $message")
    return message
  }

  fun getCommandResponse(commandName: String, input: InputStream): Command {
    val response = readMessageResponse(input)
    if (response is Command) {
      if (sessionHistory.getName(response.commandId) == commandName) {
        return response
      }
    }
    return getCommandResponse(commandName, input)
  }

  fun getMessageName(rtmpMessage: RtmpMessage): String? {
    return if (rtmpMessage is Command) {
      sessionHistory.getName(rtmpMessage.commandId)
    } else {
      null
    }
  }

  fun sendMetadata(output: OutputStream) {
    val name = "@setDataFrame"
    val metadata = DataAmf0(name, getCurrentTimestamp(), streamId)
    val amfEcmaArray = AmfEcmaArray()
    amfEcmaArray.setProperty("duration", 0.0)
    amfEcmaArray.setProperty("width", width.toDouble())
    amfEcmaArray.setProperty("height", height.toDouble())
    amfEcmaArray.setProperty("videocodecid", 7.0)
    amfEcmaArray.setProperty("framerate", 30.0)
    amfEcmaArray.setProperty("videodatarate", 0.0)
    amfEcmaArray.setProperty("audiocodecid", 10.0)
    amfEcmaArray.setProperty("audiosamplerate", sampleRate.toDouble())
    amfEcmaArray.setProperty("audiosamplesize", 16.0)
    amfEcmaArray.setProperty("audiodatarate", 0.0)
    amfEcmaArray.setProperty("stereo", isStereo)
    amfEcmaArray.setProperty("filesize", 0.0)
    metadata.addData(amfEcmaArray)

    metadata.writeHeader(output)
    metadata.writeBody(output)
    output.flush()
    sessionHistory.setPacket(commandId, name)
  }

  fun sendPublish(output: OutputStream) {
    val name = "publish"
    val publish = CommandAmf0(name, ++commandId, streamId, getCurrentTimestamp(), BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM))
    publish.addData(AmfNull())
    publish.addData(AmfString(streamName))
    publish.addData(AmfString("live"))

    publish.writeHeader(output)
    publish.writeBody(output)
    output.flush()
    sessionHistory.setPacket(commandId, name)
  }

  fun sendWindowAcknowledgementSize(output: OutputStream) {
    val windowAcknowledgementSize = WindowAcknowledgementSize(RtmpConfig.acknowledgementWindowSize, getCurrentTimestamp())
    windowAcknowledgementSize.writeHeader(output)
    windowAcknowledgementSize.writeBody(output)
    output.flush()
  }

  /**
   * Read all messages from server and response to it
   */
  fun handleMessages(input: InputStream, output: OutputStream, connectCheckerRtmp: ConnectCheckerRtmp): Boolean {
    val message = readMessageResponse(input)
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
        sendWindowAcknowledgementSize(output)
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
        val commandName = sessionHistory.getName(command.commandId)
        when (command.name) {
          "_result" -> {
            when (commandName) {
              "connect" -> {
                if (onAuth) {
                  connectCheckerRtmp.onAuthSuccessRtmp()
                  onAuth = false
                }
                createStream(output)
              }
              "createStream" -> {
                streamId = (command.data[3] as AmfNumber).value.toInt()
                sendPublish(output)
              }
              else -> {
                Log.i(TAG, "success response received from ${commandName ?: "unknown command"}")
              }
            }
          }
          "_error" -> {
            when (val description = ((command.data[3] as AmfObject).getProperty("code") as AmfString).value) {
              "connect" -> {
                //connect command error check if you need auth and do it
                if (user != null && password != null) {
                  onAuth = true
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
                  sendMetadata(output)
                  connectCheckerRtmp.onConnectionSuccessRtmp()
                  return true
                }
                "NetConnection.Connect.Rejected" -> {

                }
                "NetStream.Unpublish.Success" -> {

                }
                "NetStream.Publish.BadName" -> {

                }
                else -> {
                  Log.i(TAG, "onStatus $code response received from ${commandName ?: "unknown command"}")
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
    return false
  }

  fun reset() {
    streamId = 0
    commandId = 0
    sessionHistory.reset()
  }
}