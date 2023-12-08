/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.rtmp.flv.video

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.video.av1.AV1Parser
import com.pedro.rtmp.flv.video.av1.ObuType
import java.nio.ByteBuffer

/**
 * Created by pedro on 05/12/23.
 *
 */
class Av1Packet {

  private val TAG = "AV1Packet"

  private val parser = AV1Parser()
  private val header = ByteArray(5)
  //first time we need send video config
  private var configSend = false

  private var av1ConfigurationRecord: ByteArray? = null

  fun sendVideoInfo(av1ConfigurationRecord: ByteBuffer) {
    this.av1ConfigurationRecord = av1ConfigurationRecord.toByteArray()
  }

  fun createFlvVideoPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    var fixedBuffer = byteBuffer.duplicate().removeInfo(info)
    val isKeyframe = info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
    val ts = info.presentationTimeUs / 1000

    //header is 8 bytes length:
    //mark first byte as extended header (0b10000000)
    //4 bits data type, 4 bits packet type
    //4 bytes extended codec type (in this case av01)
    //3 bytes CompositionTime, the cts.
    val codec = VideoFormat.AV1.value // { "a", "v", "0", "1" }
    header[1] = (codec shr 24).toByte()
    header[2] = (codec shr 16).toByte()
    header[3] = (codec shr 8).toByte()
    header[4] = codec.toByte()

    var buffer: ByteArray
    if (!configSend && isKeyframe) {
      header[0] = (0b10000000 or (VideoDataType.KEYFRAME.value shl 4) or FourCCPacketType.SEQUENCE_START.value).toByte()
      val av1ConfigurationRecord = this.av1ConfigurationRecord
      if (av1ConfigurationRecord != null) {
        val obus = parser.getObus(fixedBuffer.duplicate().toByteArray())
        val sequenceObu = obus.find { parser.getObuType(it.header[0]) == ObuType.SEQUENCE_HEADER }?.getFullData() ?: byteArrayOf()
        val config = av1ConfigurationRecord.plus(sequenceObu)
        buffer = ByteArray(config.size + header.size)
        val b = ByteBuffer.wrap(buffer, header.size, config.size)
        b.put(config)
      } else {
        Log.e(TAG, "waiting for a valid av1ConfigurationRecord")
        return
      }

      System.arraycopy(header, 0, buffer, 0, header.size)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }
    //remove temporal delimitered OBU if found on start
    if (parser.getObuType(fixedBuffer.get(0)) == ObuType.TEMPORAL_DELIMITER) {
      fixedBuffer.position(2)
      fixedBuffer = fixedBuffer.slice()
    }

    fixedBuffer.rewind()
    val size = fixedBuffer.remaining()
    buffer = ByteArray(header.size + size)

    var nalType = VideoDataType.INTER_FRAME.value
    if (isKeyframe) nalType = VideoDataType.KEYFRAME.value
    header[0] = (0b10000000 or (nalType shl 4) or FourCCPacketType.CODED_FRAMES.value).toByte()
    fixedBuffer.get(buffer, header.size, size)

    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  fun reset(resetInfo: Boolean = true) {
    if (resetInfo) {
      av1ConfigurationRecord = null
    }
    configSend = false
  }
}