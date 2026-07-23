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
import com.pedro.common.removeHeader
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.common.writeUInt32
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.video.VideoDataType
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.VideoNalType
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigAVC
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 8/04/21.
 *
 * ISO 14496-15
 */
class H264Packet: BasePacket() {

  private val TAG = "H264Packet"

  private val header = ByteArray(5)
  private val naluSize = 4
  //first time we need send video config
  private var configSend = false

  private var videoInfo: List<ByteBuffer>? = null

  enum class Type(val value: Byte) {
    SEQUENCE(0x00), NALU(0x01), EO_SEQ(0x02)
  }

  fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer) {
    videoInfo = listOf(sps.removeHeader(), pps.removeHeader())
  }

  override suspend fun createFlvPacket(
    mediaFrame: MediaFrame,
    callback: suspend (FlvPacket) -> Unit
  ) {
    val videoInfo = this.videoInfo
    if (videoInfo == null) {
      Log.e(TAG, "waiting for a valid sps and pps")
      return
    }

    val fixedBuffer = mediaFrame.data.removeInfo(mediaFrame.info)
    val ts = mediaFrame.info.timestamp / 1000
    //header is 5 bytes length:
    //4 bits FrameType, 4 bits CodecID
    //1 byte AVCPacketType
    //3 bytes CompositionTime, the cts.
    val cts = 0
    header[2] = (cts shr 16).toByte()
    header[3] = (cts shr 8).toByte()
    header[4] = cts.toByte()

    var buffer: ByteArray
    if (!configSend) {
      header[0] = ((VideoDataType.KEYFRAME.value shl 4) or VideoFormat.AVC.value).toByte()
      header[1] = Type.SEQUENCE.value

      val config = VideoSpecificConfigAVC(videoInfo[0].toByteArray(), videoInfo[1].toByteArray())
      buffer = ByteArray(config.size + header.size)
      config.write(buffer, header.size)
      System.arraycopy(header, 0, buffer, 0, header.size)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }

    fixedBuffer.rewind()
    val nals = NalReader.extractNals(fixedBuffer, VideoCodec.H264, true)
    if (nals.isEmpty()) return

    val size = nals.sumOf { it.capacity() }
    buffer = ByteArray(header.size + size + naluSize * nals.size)

    val type: Int = (nals[0].get(0) and 0x1F).toInt()
    var nalType = VideoDataType.INTER_FRAME.value
    if (type == VideoNalType.IDR.value || mediaFrame.info.isKeyFrame) {
      nalType = VideoDataType.KEYFRAME.value
    } else if (type == VideoNalType.SPS.value || type == VideoNalType.PPS.value) {
      // we don't need send it because we already do it in video config
      return
    }
    header[0] = ((nalType shl 4) or VideoFormat.AVC.value).toByte()
    header[1] = Type.NALU.value
    var offset = header.size
    nals.forEach {
      val nalSize = it.capacity()
      buffer.writeUInt32(offset, nalSize)
      it.get(buffer, offset + naluSize, nalSize)
      offset += naluSize + nalSize
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  override fun reset(resetInfo: Boolean) {
    if (resetInfo) videoInfo = null
    configSend = false
  }
}