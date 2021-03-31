package com.pedro.rtsp.rtp.packets

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 28/11/18.
 *
 * RFC 7798.
 */
open class H265Packet(sps: ByteArray, pps: ByteArray, vps: ByteArray, private val videoPacketCallback: VideoPacketCallback) : BasePacket(RtpConstants.clockVideoFrequency,
    RtpConstants.payloadType + RtpConstants.trackVideo) {

  private val header = ByteArray(6)
  private var stapA: ByteArray? = null
  private var sendKeyFrame = false

  init {
    channelIdentifier = RtpConstants.trackVideo
    setSpsPpsVps(sps, pps, vps)
  }

  override fun createAndSendPacket(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    byteBuffer.rewind()
    byteBuffer.get(header, 0, 6)
    val ts = bufferInfo.presentationTimeUs * 1000L
    val naluLength = bufferInfo.size - byteBuffer.position() + 1
    val type: Int = header[4].toInt().shr(1 and 0x3f)
    if (type == RtpConstants.IDR_N_LP || type == RtpConstants.IDR_W_DLP || bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
      stapA?.let {
        val buffer = getBuffer(it.size + RtpConstants.RTP_HEADER_LENGTH)
        val rtpts = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        System.arraycopy(it, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, it.size)
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpts, it.size + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier)
        videoPacketCallback.onVideoFrameCreated(rtpFrame)
        sendKeyFrame = true
      } ?: run {
        Log.i(TAG, "can't create key frame because setSpsPps was not called")
      }
    }
    if (sendKeyFrame) {
      // Small NAL unit => Single NAL unit
      if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3) {
        val cont = naluLength - 1
        val length = if (cont < bufferInfo.size - byteBuffer.position()) {
          cont
        } else {
          bufferInfo.size - byteBuffer.position()
        }
        val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2)
        //Set PayloadHdr (exact copy of nal unit header)
        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[4]
        buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[5]
        byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length)
        val rtpts = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpts, naluLength + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier)
        videoPacketCallback.onVideoFrameCreated(rtpFrame)
      } else {
        //Set PayloadHdr (16bit type=49)
        header[0] = 49 shl 1
        header[1] = 1
        // Set FU header
        //   +---------------+
        //   |0|1|2|3|4|5|6|7|
        //   +-+-+-+-+-+-+-+-+
        //   |S|E|  FuType   |
        //   +---------------+
        header[2] = type.toByte() // FU header type
        header[2] = header[2].plus(0x80).toByte() // Start bit
        var sum = 1
        while (sum < naluLength) {
          val cont = if (naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3) {
            maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3
          } else {
            naluLength - sum
          }
          val length = if (cont < bufferInfo.size - byteBuffer.position()) {
            cont
          } else {
            bufferInfo.size - byteBuffer.position()
          }
          val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 3)
          buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0]
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1]
          buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = header[2]
          val rtpts = updateTimeStamp(buffer, ts)
          byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 3, length)
          sum += length
          // Last packet before next NAL
          if (sum >= naluLength) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = buffer[RtpConstants.RTP_HEADER_LENGTH + 2].plus(0x40).toByte()
            markPacket(buffer) //mark end frame
          }
          updateSeq(buffer)
          val rtpFrame = RtpFrame(buffer, rtpts, length + RtpConstants.RTP_HEADER_LENGTH + 3, rtpPort, rtcpPort, channelIdentifier)
          videoPacketCallback.onVideoFrameCreated(rtpFrame)
          // Switch start bit
          header[2] = header[2] and 0x7F
        }
      }
    }
  }

  private fun setSpsPpsVps(sps: ByteArray, pps: ByteArray, vps: ByteArray) {
    stapA = ByteArray(sps.size + pps.size + 6)
    stapA?.let {
      it[0] = 48 shl 1
      it[1] = 1

      // Write NALU 1 size into the array (NALU 1 is the SPS).
      it[2] = (sps.size shr 8).toByte()
      it[3] = (sps.size and 0xFF).toByte()

      // Write NALU 2 size into the array (NALU 2 is the PPS).
      it[sps.size + 4] = (pps.size shr 8).toByte()
      it[sps.size + 5] = (pps.size and 0xFF).toByte()

      // Write NALU 1 into the array, then write NALU 2 into the array.
      System.arraycopy(sps, 0, it, 4, sps.size)
      System.arraycopy(pps, 0, it, 6 + sps.size, pps.size)
    }
  }

  override fun reset() {
    super.reset()
    sendKeyFrame = false
  }
}