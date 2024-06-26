/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.rtmp.flv.video.packet

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.common.av1.Av1Parser
import com.pedro.common.av1.ObuType
import com.pedro.common.isKeyframe
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.video.FourCCPacketType
import com.pedro.rtmp.flv.video.VideoDataType
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigAV1
import java.nio.ByteBuffer

/**
 * Created by pedro on 05/12/23.
 *
 */
class Av1Packet: BasePacket() {

  private val TAG = "AV1Packet"

  private val parser = Av1Parser()
  private val header = ByteArray(5)
  //first time we need send video config
  private var configSend = false
  private var obuSequence: ByteArray? = null

  fun sendVideoInfo(obuSequence: ByteBuffer) {
    this.obuSequence = obuSequence.toByteArray()
  }

  override fun createFlvPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (FlvPacket) -> Unit
  ) {
    var fixedBuffer = byteBuffer.duplicate().removeInfo(info)
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
    if (!configSend) {
      header[0] = (0b10000000 or (VideoDataType.KEYFRAME.value shl 4) or FourCCPacketType.SEQUENCE_START.value).toByte()
      val obuSequence = this.obuSequence
      if (obuSequence != null) {
        val config = VideoSpecificConfigAV1(obuSequence)
        buffer = ByteArray(config.size + header.size)
        config.write(buffer, header.size)
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

    val nalType = if (info.isKeyframe()) VideoDataType.KEYFRAME.value else VideoDataType.INTER_FRAME.value
    header[0] = (0b10000000 or (nalType shl 4) or FourCCPacketType.CODED_FRAMES.value).toByte()
    fixedBuffer.get(buffer, header.size, size)

    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  override fun reset(resetInfo: Boolean) {
    if (resetInfo) {
      obuSequence = null
    }
    configSend = false
  }
}