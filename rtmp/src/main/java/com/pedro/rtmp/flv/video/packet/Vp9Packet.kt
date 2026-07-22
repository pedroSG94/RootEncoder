package com.pedro.rtmp.flv.video.packet

import android.util.Log
import com.pedro.common.frame.MediaFrame
import com.pedro.common.removeInfo
import com.pedro.common.toByteArray
import com.pedro.rtmp.flv.BasePacket
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.video.VideoDataType
import com.pedro.rtmp.flv.video.VideoFormat
import com.pedro.rtmp.flv.video.VideoFourCCPacketType
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigVp8
import com.pedro.rtmp.flv.video.config.VideoSpecificConfigVp9
import java.nio.ByteBuffer

class Vp9Packet: BasePacket() {

  private val TAG = "Vp9Packet"
  private val header = ByteArray(8)
  //first time we need send video config
  private var configSend = false

  private var videoInfo: ByteBuffer? = null

  fun sendVideoInfo(info: ByteBuffer) {
    this.videoInfo = info
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
    val codec = VideoFormat.VP9.value // { "v", "p", "0", "9" }
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

      val config = VideoSpecificConfigVp9(videoInfo.toByteArray())
      buffer = ByteArray(config.size + header.size - ctsLength)
      config.write(buffer, header.size - ctsLength)
      System.arraycopy(header, 0, buffer, 0, header.size - ctsLength)
      callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
      configSend = true
    }

    fixedBuffer.rewind()
    buffer = ByteArray(header.size + fixedBuffer.remaining())
    fixedBuffer.get(buffer, header.size, fixedBuffer.remaining())
    val nalType = if (mediaFrame.info.isKeyFrame) VideoDataType.KEYFRAME.value else VideoDataType.INTER_FRAME.value
    header[0] = (0b10000000 or (nalType shl 4) or VideoFourCCPacketType.CODED_FRAMES.value).toByte()
    System.arraycopy(header, 0, buffer, 0, header.size)
    callback(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  override fun reset(resetInfo: Boolean) {
    configSend = false
    if (resetInfo) videoInfo = null
  }
}