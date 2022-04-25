/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.rtmp.amf.v0.*
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.*
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.rtmp.message.control.Event
import com.pedro.rtmp.rtmp.message.control.Type
import com.pedro.rtmp.rtmp.message.control.UserControl
import com.pedro.rtmp.rtmp.message.data.DataAmf0
import com.pedro.rtmp.utils.CommandSessionHistory
import com.pedro.rtmp.utils.RtmpConfig
import com.pedro.rtmp.utils.socket.RtmpSocket
import java.io.*

/**
 * Created by pedro on 21/04/21.
 */
class CommandsManager {

  private val TAG = "CommandsManager"

  val sessionHistory = CommandSessionHistory()
  var timestamp = 0
  private var commandId = 0
  var streamId = 0
  var host = ""
  var port = 1935
  var appName = ""
  var streamName = ""
  var tcUrl = ""
  var user: String? = null
  var password: String? = null
  var onAuth = false
  var akamaiTs = false
  var startTs = 0L
  var readChunkSize = RtmpConfig.DEFAULT_CHUNK_SIZE
  var audioDisabled = false
  var videoDisabled = false

  private var width = 640
  private var height = 480
  private var fps = 30
  private var sampleRate = 44100
  private var isStereo = true
  //Avoid write a packet in middle of other.
  private val writeSync = Any()

  fun setVideoResolution(width: Int, height: Int) {
    this.width = width
    this.height = height
  }

  fun setFps(fps: Int) {
    this.fps = fps
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
  fun sendChunkSize(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      if (RtmpConfig.writeChunkSize != RtmpConfig.DEFAULT_CHUNK_SIZE) {
        val chunkSize = SetChunkSize(RtmpConfig.writeChunkSize)
        chunkSize.header.timeStamp = getCurrentTimestamp()
        chunkSize.header.messageStreamId = streamId
        chunkSize.writeHeader(output)
        chunkSize.writeBody(output)
        socket.flush()
        Log.i(TAG, "send $chunkSize")
      } else {
        Log.i(TAG, "using default write chunk size ${RtmpConfig.DEFAULT_CHUNK_SIZE}")
      }
    }
  }

  @Throws(IOException::class)
  fun sendConnect(auth: String, socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val connect = CommandAmf0("connect", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
      val connectInfo = AmfObject()
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
      connectInfo.setProperty("objectEncoding", 0.0)
      connect.addData(connectInfo)

      connect.writeHeader(output)
      connect.writeBody(output)
      socket.flush()
      sessionHistory.setPacket(commandId, "connect")
      Log.i(TAG, "send $connect")
    }
  }

  @Throws(IOException::class)
  fun createStream(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val releaseStream = CommandAmf0("releaseStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
      releaseStream.addData(AmfNull())
      releaseStream.addData(AmfString(streamName))

      releaseStream.writeHeader(output)
      releaseStream.writeBody(output)
      sessionHistory.setPacket(commandId, "releaseStream")
      Log.i(TAG, "send $releaseStream")

      val fcPublish = CommandAmf0("FCPublish", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
      fcPublish.addData(AmfNull())
      fcPublish.addData(AmfString(streamName))

      fcPublish.writeHeader(output)
      fcPublish.writeBody(output)
      sessionHistory.setPacket(commandId, "FCPublish")
      Log.i(TAG, "send $fcPublish")

      val createStream = CommandAmf0("createStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
      createStream.addData(AmfNull())

      createStream.writeHeader(output)
      createStream.writeBody(output)
      socket.flush()
      sessionHistory.setPacket(commandId, "createStream")
      Log.i(TAG, "send $createStream")
    }
  }

  @Throws(IOException::class)
  fun readMessageResponse(socket: RtmpSocket): RtmpMessage {
    val input = socket.getInputStream()
    val message = RtmpMessage.getRtmpMessage(input, readChunkSize, sessionHistory)
    sessionHistory.setReadHeader(message.header)
    Log.i(TAG, "read $message")
    return message
  }

  @Throws(IOException::class)
  fun sendMetadata(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val name = "@setDataFrame"
      val metadata = DataAmf0(name, getCurrentTimestamp(), streamId)
      metadata.addData(AmfString("onMetaData"))
      val amfEcmaArray = AmfEcmaArray()
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
      }
      amfEcmaArray.setProperty("stereo", isStereo)
      amfEcmaArray.setProperty("filesize", 0.0)
      metadata.addData(amfEcmaArray)

      metadata.writeHeader(output)
      metadata.writeBody(output)
      socket.flush()
      Log.i(TAG, "send $metadata")
    }
  }

  @Throws(IOException::class)
  fun sendPublish(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val name = "publish"
      val publish = CommandAmf0(name, ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
      publish.addData(AmfNull())
      publish.addData(AmfString(streamName))
      publish.addData(AmfString("live"))

      publish.writeHeader(output)
      publish.writeBody(output)
      socket.flush()
      sessionHistory.setPacket(commandId, name)
      Log.i(TAG, "send $publish")
    }
  }

  @Throws(IOException::class)
  fun sendWindowAcknowledgementSize(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val windowAcknowledgementSize = WindowAcknowledgementSize(RtmpConfig.acknowledgementWindowSize, getCurrentTimestamp())
      windowAcknowledgementSize.writeHeader(output)
      windowAcknowledgementSize.writeBody(output)
      socket.flush()
    }
  }

  fun sendPong(event: Event, socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val pong = UserControl(Type.PONG_REPLY, event)
      pong.writeHeader(output)
      pong.writeBody(output)
      socket.flush()
      Log.i(TAG, "send pong")
    }
  }

  @Throws(IOException::class)
  fun sendClose(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      val name = "closeStream"
      val closeStream = CommandAmf0(name, ++commandId, getCurrentTimestamp(), streamId, BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
      closeStream.addData(AmfNull())

      closeStream.writeHeader(output)
      closeStream.writeBody(output)
      socket.flush()
      sessionHistory.setPacket(commandId, name)
      Log.i(TAG, "send $closeStream")
    }
  }

  @Throws(IOException::class)
  fun sendVideoPacket(flvPacket: FlvPacket, socket: RtmpSocket): Int {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      if (akamaiTs) {
        flvPacket.timeStamp = ((System.nanoTime() / 1000 - startTs) / 1000)
      }
      val video = Video(flvPacket, streamId)
      video.writeHeader(output)
      video.writeBody(output)
      socket.flush()
      return video.header.getPacketLength() //get packet size with header included to calculate bps
    }
  }

  @Throws(IOException::class)
  fun sendAudioPacket(flvPacket: FlvPacket, socket: RtmpSocket): Int {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      if (akamaiTs) {
        flvPacket.timeStamp = ((System.nanoTime() / 1000 - startTs) / 1000)
      }
      val audio = Audio(flvPacket, streamId)
      audio.writeHeader(output)
      audio.writeBody(output)
      socket.flush()
      return audio.header.getPacketLength() //get packet size with header included to calculate bps
    }
  }

  fun reset() {
    startTs = 0
    timestamp = 0
    streamId = 0
    commandId = 0
    sessionHistory.reset()
  }
}