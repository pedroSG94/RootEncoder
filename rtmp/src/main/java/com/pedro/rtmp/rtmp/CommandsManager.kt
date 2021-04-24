package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.rtmp.amf.v0.AmfNull
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.RtmpMessage
import com.pedro.rtmp.rtmp.message.command.Command
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.utils.CommandSessionHistory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 21/04/21.
 */
class CommandsManager {

  private val TAG = "CommandsManager"

  private val sessionHistory = CommandSessionHistory()
  private var commandId = 0
  private var streamId = 0
  var host = ""
  var port = 1935
  var appName = ""
  var streamName = ""
  var  tcUrl = ""
  private var user: String? = null
  private var password: String? = null

  fun setAuth(user: String?, password: String?) {
    this.user = user
    this.password = password
  }

  @Throws(IOException::class)
  fun connect(auth: String, output: OutputStream, timestamp: Long) {
    val connect = CommandAmf0("connect", ++commandId, (timestamp - (System.currentTimeMillis() / 1000)).toInt())
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
    sessionHistory.setPacket(commandId, "connect")
    Log.i(TAG, "send $connect")
  }

  fun createStream(output: OutputStream, timestamp: Long) {
    val releaseStream = CommandAmf0("releaseStream", ++commandId, (timestamp - (System.currentTimeMillis() / 1000)).toInt(),
        basicHeader = BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM))
    releaseStream.addData(AmfNull())
    releaseStream.addData(AmfString(streamName))

    releaseStream.writeHeader(output)
    releaseStream.writeBody(output)
    sessionHistory.setPacket(commandId, "releaseStream")
    Log.i(TAG, "send $releaseStream")

    val fcPublish = CommandAmf0("FCPublish", ++commandId, (timestamp - (System.currentTimeMillis() / 1000)).toInt(),
        basicHeader = BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM))
    fcPublish.addData(AmfNull())
    fcPublish.addData(AmfString(streamName))

    fcPublish.writeHeader(output)
    fcPublish.writeBody(output)
    sessionHistory.setPacket(commandId, "FCPublish")
    Log.i(TAG, "send $fcPublish")

    val createStream = CommandAmf0("createStream", ++commandId, (timestamp - (System.currentTimeMillis() / 1000)).toInt(),
        basicHeader = BasicHeader(ChunkType.TYPE_1, ChunkStreamId.OVER_CONNECTION))
    createStream.addData(AmfNull())

    createStream.writeHeader(output)
    createStream.writeBody(output)
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

  fun reset() {
    commandId = 0
    sessionHistory.reset()
  }
}