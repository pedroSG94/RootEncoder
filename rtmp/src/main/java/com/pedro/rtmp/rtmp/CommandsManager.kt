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
abstract class CommandsManager {

  protected val TAG = "CommandsManager"

  val sessionHistory = CommandSessionHistory()
  var timestamp = 0
  protected var commandId = 0
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

  protected var width = 640
  protected var height = 480
  var fps = 30
  protected var sampleRate = 44100
  protected var isStereo = true
  //Avoid write a packet in middle of other.
  private val writeSync = Any()

  fun setVideoResolution(width: Int, height: Int) {
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

  protected fun getCurrentTimestamp(): Int {
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
      sendConnect(auth, output)
      socket.flush()
    }
  }

  @Throws(IOException::class)
  fun createStream(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      createStream(output)
      socket.flush()
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
      sendMetadata(output)
      socket.flush()
    }
  }

  @Throws(IOException::class)
  fun sendPublish(socket: RtmpSocket) {
    synchronized(writeSync) {
      val output = socket.getOutStream()
      sendPublish(output)
      socket.flush()
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
      sendClose(output)
      socket.flush()
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

  abstract fun sendConnect(auth: String, output: OutputStream)
  abstract fun createStream(output: OutputStream)
  abstract fun sendMetadata(output: OutputStream)
  abstract fun sendPublish(output: OutputStream)
  abstract fun sendClose(output: OutputStream)

  fun reset() {
    startTs = 0
    timestamp = 0
    streamId = 0
    commandId = 0
    sessionHistory.reset()
  }
}