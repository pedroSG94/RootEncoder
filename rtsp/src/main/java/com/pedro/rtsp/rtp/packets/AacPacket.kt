package com.pedro.rtsp.rtp.packets

import android.media.MediaCodec
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3640.
 */
open class AacPacket(sampleRate: Int, private val audioPacketCallback: AudioPacketCallback) : BasePacket(sampleRate.toLong(),
    RtpConstants.payloadType + RtpConstants.trackAudio) {

  init {
    channelIdentifier = RtpConstants.trackAudio
  }

  override fun createAndSendPacket(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
    val length = bufferInfo.size - byteBuffer.position()
    if (length > 0) {
      val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 4)
      byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 4, length)
      val ts = bufferInfo.presentationTimeUs * 1000
      markPacket(buffer)
      val rtpTs = updateTimeStamp(buffer, ts)

      // AU-headers-length field: contains the size in bits of a AU-header
      // 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta
      // 13 bits will be enough because ADTS uses 13 bits for frame length
      buffer[RtpConstants.RTP_HEADER_LENGTH] = 0.toByte()
      buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = 0x10.toByte()

      // AU-size
      buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = (length shr 5).toByte()
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] = (length shl 3).toByte()

      // AU-Index
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] = buffer[RtpConstants.RTP_HEADER_LENGTH + 3] and 0xF8.toByte()
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] = buffer[RtpConstants.RTP_HEADER_LENGTH + 3] or 0x00
      updateSeq(buffer)
      val rtpFrame = RtpFrame(buffer, rtpTs, RtpConstants.RTP_HEADER_LENGTH + length + 4, rtpPort, rtcpPort, channelIdentifier)
      audioPacketCallback.onAudioFrameCreated(rtpFrame)
    }
  }
}