package com.pedro.rtmp.flv.video

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 8/04/21.
 *
 * ISO 14496-15
 */
class H264Packet(private val videoPacketCallback: VideoPacketCallback) {

  private val TAG = "H264Packet"

  private val header = ByteArray(5)
  private val naluSize = 4
  //first time we need send video config
  private var configSend = false

  private var sps: ByteArray? = null
  private var pps: ByteArray? = null
  var profileIop = ProfileIop.BASELINE

  enum class Type(val value: Byte) {
    SEQUENCE(0x00), NALU(0x01), EO_SEQ(0x02)
  }

  fun sendVideoInfo(sps: ByteArray, pps: ByteArray) {
    this.pps = pps
    this.sps = sps
  }

  fun createFlvAudioPacket(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    //header is 5 bytes length:
    //4 bits FrameType, 4 bits CodecID
    //1 byte AVCPacketType
    //3 bytes CompositionTime, the cts.
    val cts = 0
    header[2] = (cts shr 16).toByte()
    header[3] = (cts shr 8).toByte()
    header[4] = cts.toByte()

    val buffer: ByteArray
    if (!configSend) {
      header[0] = ((VideoNalType.IDR.value shl 4) or VideoFormat.AVC.value).toByte()
      header[1] = Type.SEQUENCE.value

      if (sps != null && pps != null) {
        val config = VideoSpecificConfig(sps!!, pps!!, profileIop)
        buffer = ByteArray(config.size + header.size)
        config.write(buffer, header.size)
      } else {
        Log.e(TAG, "waiting for a valid sps and pps")
        return
      }
      configSend = true
    } else {
      val size = info.size - info.offset
      buffer = ByteArray(header.size + size + naluSize)

      val type: Int = (byteBuffer.get(4) and 0x1F).toInt()
      var nalType = VideoNalType.SLICE.ordinal
      if (type == VideoNalType.IDR.value || info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
        nalType = VideoNalType.IDR.value
      }
      header[0] = ((nalType shl 4) or VideoFormat.AVC.value).toByte()
      header[1] = Type.NALU.value
      writeNaluSize(buffer, header.size, size)
      byteBuffer.get(buffer, header.size + naluSize, info.size - info.offset)
    }
    System.arraycopy(header, 0, buffer, 0, header.size)
    val ts = info.presentationTimeUs / 1000
    videoPacketCallback.onVideoFrameCreated(FlvPacket(buffer, ts, buffer.size, FlvType.VIDEO))
  }

  //naluSize = UInt32
  private fun writeNaluSize(buffer: ByteArray, offset: Int, size: Int) {
    buffer[offset] = (size ushr 24).toByte()
    buffer[offset + 1] = (size ushr 16).toByte()
    buffer[offset + 2] = (size ushr 8).toByte()
    buffer[offset + 3] = size.toByte()
  }

  fun reset() {
    configSend = false
  }
}