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
import java.nio.ByteBuffer

/**
 * Created by pedro on 05/12/23.
 *
 */
class Av1Packet {

  private val TAG = "AV1Packet"

  private val header = ByteArray(8)
  //first time we need send video config
  private var configSend = false

  private var av1ConfigurationRecord: ByteArray? = null

  fun sendVideoInfo(av1ConfigurationRecord: ByteBuffer) {
    this.av1ConfigurationRecord = av1ConfigurationRecord.toByteArray().reversedArray()
  }

  fun createFlvVideoPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    val fixedBuffer = byteBuffer.duplicate().removeInfo(info)
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
    val cts = 0
    val ctsLength = 3
    header[5] = (cts shr 16).toByte()
    header[6] = (cts shr 8).toByte()
    header[7] = cts.toByte()

    var buffer: ByteArray
    if (!configSend) {
      //avoid send cts on sequence start
      header[0] = (0b10000000 or (VideoDataType.KEYFRAME.value shl 4) or FourCCPacketType.SEQUENCE_START.value).toByte()
      val av1ConfigurationRecord = this.av1ConfigurationRecord
      if (av1ConfigurationRecord != null) {
        val config = av1ConfigurationRecord
        buffer = ByteArray(config.size + header.size - ctsLength)
        val b = ByteBuffer.wrap(buffer, header.size - ctsLength, av1ConfigurationRecord.size)
        b.put(av1ConfigurationRecord)
      } else {
        Log.e(TAG, "waiting for a valid av1ConfigurationRecord")
        return
      }

      System.arraycopy(header, 0, buffer, 0, header.size - ctsLength)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }
    fixedBuffer.rewind()
    val size = fixedBuffer.remaining()
    buffer = ByteArray(header.size + size)

    val type: Int = fixedBuffer.get(0).toInt().shr(1 and 0x3f)
    var nalType = VideoDataType.INTER_FRAME.value
    if (type == VideoNalType.KEY.value || info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
      nalType = VideoDataType.KEYFRAME.value
    } else if (type == VideoNalType.CONFIG.value) {
      // we don't need send it because we already do it in video config
      return
    }
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