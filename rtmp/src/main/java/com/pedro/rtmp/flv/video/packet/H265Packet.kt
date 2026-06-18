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

import android.util.Log
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.getStartCodeSize
import com.pedro.common.nal.NalReader
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.video.VideoDataType
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.VideoFourCCPacketType
import com.pedro.rtmp.flv.video.VideoNalType
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigHEVC
import java.nio.ByteBuffer

/**
 * Created by pedro on 14/08/23.
 *
 */
class H265Packet: BasePacket() {

  private val TAG = "H265Packet"

  private val header = ByteArray(8)
  private val naluSize = 4
  //first time we need send video config
  private var configSend = false

  private var videoInfo: List<ByteBuffer>? = null

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
    this.videoInfo = listOf(removeHeader(sps), removeHeader(pps), removeHeader(vps))
  }

  override suspend fun createFlvPacket(
    mediaFrame: MediaFrame,
    callback: suspend (FlvPacket) -> Unit
  ) {
    val videoInfo = this.videoInfo
    if (videoInfo == null) {
      Log.e(TAG, "waiting for a valid sps, pps and vps")
      return
    }
    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val ts = mediaFrame.info.timestamp / 1000
    //header is 8 bytes length:
    //mark first byte as extended header (0b10000000)
    //4 bits data type, 4 bits packet type
    //4 bytes extended codec type (in this case hevc)
    //3 bytes CompositionTime, the cts.
    val codec = VideoFormat.HEVC.value // { "h", "v", "c", "1" }
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
      header[0] = (0b10000000 or (VideoDataType.KEYFRAME.value shl 4) or VideoFourCCPacketType.SEQUENCE_START.value).toByte()

      val config = VideoSpecificConfigHEVC(videoInfo[0].toByteArray(), videoInfo[1].toByteArray(), videoInfo[2].toByteArray())
      buffer = ByteArray(config.size + header.size - ctsLength)
      config.write(buffer, header.size - ctsLength)
      System.arraycopy(header, 0, buffer, 0, header.size - ctsLength)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }

    fixedBuffer.rewind()
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H265, true)
    if (nals.isEmpty()) return

    val size = nals.sumOf { it.capacity() }
    buffer = ByteArray(header.size + size + naluSize * nals.size)

    val type: Int = nals[0].get(0).toInt().shr(1) and 0x3F
    var nalType = VideoDataType.INTER_FRAME.value
    if (type == VideoNalType.IDR_N_LP.value || type == VideoNalType.IDR_W_DLP.value || mediaFrame.info.isKeyFrame) {
      nalType = VideoDataType.KEYFRAME.value
    } else if (type == VideoNalType.HEVC_VPS.value || type == VideoNalType.HEVC_SPS.value || type == VideoNalType.HEVC_PPS.value) {
      // we don't need send it because we already do it in video config
      return
    }
    header[0] = (0b10000000 or (nalType shl 4) or VideoFourCCPacketType.CODED_FRAMES.value).toByte()
    var offset = header.size
    nals.forEach {
      val nalSize = it.capacity()
      writeNaluSize(buffer, offset, nalSize)
      it.get(buffer, offset + naluSize, nalSize)
      offset += naluSize + nalSize
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  //naluSize = UInt32
  private fun writeNaluSize(buffer: ByteArray, offset: Int, size: Int) {
    buffer[offset] = (size ushr 24).toByte()
    buffer[offset + 1] = (size ushr 16).toByte()
    buffer[offset + 2] = (size ushr 8).toByte()
    buffer[offset + 3] = size.toByte()
  }

  private fun removeHeader(byteBuffer: ByteBuffer, size: Int = -1): ByteBuffer {
    val position = if (size == -1) byteBuffer.getStartCodeSize() else size
    byteBuffer.position(position)
    return byteBuffer.slice()
  }

  override fun reset(resetInfo: Boolean) {
    if (resetInfo) videoInfo = null
    configSend = false
  }
}