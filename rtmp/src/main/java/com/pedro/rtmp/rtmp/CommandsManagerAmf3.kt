package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.rtmp.amf.v3.Amf3Dictionary
import com.pedro.rtmp.amf.v3.Amf3Null
import com.pedro.rtmp.amf.v3.Amf3Object
import com.pedro.rtmp.amf.v3.Amf3String
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.command.CommandAmf3
import com.pedro.rtmp.rtmp.message.data.DataAmf3
import java.io.OutputStream

class CommandsManagerAmf3: CommandsManager() {
  override fun sendConnect(auth: String, output: OutputStream) {
    val connect = CommandAmf3("connect", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
    val connectInfo = Amf3Object()
    connectInfo.setProperty("app", appName + auth)
    connectInfo.setProperty("flashVer", "FMLE/3.0 (compatible; Lavf57.56.101)")
    connectInfo.setProperty("swfUrl", "")
    connectInfo.setProperty("tcUrl", tcUrl + auth)
    connectInfo.setProperty("fpad", false)
    connectInfo.setProperty("capabilities", 239.0)
    if (!audioDisabled) {
      connectInfo.setProperty("audioCodecs", 3191.0)
    }
    if (!videoDisabled) {
      connectInfo.setProperty("videoCodecs", 252.0)
      connectInfo.setProperty("videoFunction", 1.0)
    }
    connectInfo.setProperty("pageUrl", "")
    connectInfo.setProperty("objectEncoding", 3.0)
    connect.addData(connectInfo)

    connect.writeHeader(output)
    connect.writeBody(output)
    sessionHistory.setPacket(commandId, "connect")
    Log.i(TAG, "send $connect")
  }

  override fun createStream(output: OutputStream) {
    val releaseStream = CommandAmf3("releaseStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    releaseStream.addData(Amf3Null())
    releaseStream.addData(Amf3String(streamName))

    releaseStream.writeHeader(output)
    releaseStream.writeBody(output)
    sessionHistory.setPacket(commandId, "releaseStream")
    Log.i(TAG, "send $releaseStream")

    val fcPublish = CommandAmf3("FCPublish", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    fcPublish.addData(Amf3Null())
    fcPublish.addData(Amf3String(streamName))

    fcPublish.writeHeader(output)
    fcPublish.writeBody(output)
    sessionHistory.setPacket(commandId, "FCPublish")
    Log.i(TAG, "send $fcPublish")

    val createStream = CommandAmf3("createStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
    createStream.addData(Amf3Null())

    createStream.writeHeader(output)
    createStream.writeBody(output)
    sessionHistory.setPacket(commandId, "createStream")
    Log.i(TAG, "send $createStream")
  }

  override fun sendMetadata(output: OutputStream) {
    val name = "@setDataFrame"
    val metadata = DataAmf3(name, getCurrentTimestamp(), streamId)
    metadata.addData(Amf3String("onMetaData"))
    val amfEcmaArray = Amf3Dictionary()
    amfEcmaArray.setProperty("duration", 0.0)
    if (!videoDisabled) {
      amfEcmaArray.setProperty("width", width.toDouble())
      amfEcmaArray.setProperty("height", height.toDouble())
      amfEcmaArray.setProperty("videocodecid", 7.0)
      amfEcmaArray.setProperty("framerate", fps.toDouble())
      amfEcmaArray.setProperty("videodatarate", 0.0)
    }
    if (!audioDisabled) {
      amfEcmaArray.setProperty("audiocodecid", 10.0)
      amfEcmaArray.setProperty("audiosamplerate", sampleRate.toDouble())
      amfEcmaArray.setProperty("audiosamplesize", 16.0)
      amfEcmaArray.setProperty("audiodatarate", 0.0)
      amfEcmaArray.setProperty("stereo", isStereo)
    }
    amfEcmaArray.setProperty("filesize", 0.0)
    metadata.addData(amfEcmaArray)

    metadata.writeHeader(output)
    metadata.writeBody(output)
    Log.i(TAG, "send $metadata")
  }

  override fun sendPublish(output: OutputStream) {
    val name = "publish"
    val publish = CommandAmf3(name, ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    publish.addData(Amf3Null())
    publish.addData(Amf3String(streamName))
    publish.addData(Amf3String("live"))

    publish.writeHeader(output)
    publish.writeBody(output)
    sessionHistory.setPacket(commandId, name)
    Log.i(TAG, "send $publish")
  }

  override fun sendClose(output: OutputStream) {
    val name = "closeStream"
    val closeStream = CommandAmf3(name, ++commandId, getCurrentTimestamp(), streamId, BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    closeStream.addData(Amf3Null())

    closeStream.writeHeader(output)
    closeStream.writeBody(output)
    sessionHistory.setPacket(commandId, name)
    Log.i(TAG, "send $closeStream")
  }
}