package com.pedro.rtsp.rtp.packets

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3984
 */
open class H264Packet(sps: ByteArray, pps: ByteArray, private val videoPacketCallback: VideoPacketCallback) : BasePacket(RtpConstants.clockVideoFrequency,
    RtpConstants.payloadType + RtpConstants.trackVideo) {

  private val header = ByteArray(5)
  private var stapA: ByteArray? = null
  private var sendKeyFrame = false

  init {
    channelIdentifier = RtpConstants.trackVideo
    setSpsPps(sps, pps)
  }

  override fun createAndSendPacket(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
    if (bufferInfo.size < 5) return
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    byteBuffer.rewind()
    byteBuffer.get(header, 0, 5)
    val ts = bufferInfo.presentationTimeUs * 1000L
    val naluLength = bufferInfo.size - byteBuffer.position() + 1
    val type: Int = (header[4] and 0x1F).toInt()
    if (type == RtpConstants.IDR || bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
      //fix malformed keyframe header if necessary
      if (type != RtpConstants.IDR) header[4] = RtpConstants.IDR.toByte()
      stapA?.let {
        val buffer = getBuffer(it.size + RtpConstants.RTP_HEADER_LENGTH)
        val rtpTs = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        System.arraycopy(it, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, it.size)
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, it.size + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier)
        videoPacketCallback.onVideoFrameCreated(rtpFrame)
        sendKeyFrame = true
      } ?: run {
        Log.i(TAG, "can't create key frame because setSpsPps was not called")
      }
    }
    if (sendKeyFrame) {
      // Small NAL unit => Single NAL unit
      if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
        val cont = naluLength - 1
        val length = if (cont < bufferInfo.size - byteBuffer.position()) {
          cont
        } else {
          bufferInfo.size - byteBuffer.position()
        }
        val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 1)
        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[4]
        byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, length)
        val rtpTs = updateTimeStamp(buffer, ts)
        markPacket(buffer) //mark end frame
        updateSeq(buffer)
        val rtpFrame = RtpFrame(buffer, rtpTs, naluLength + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort, channelIdentifier)
        videoPacketCallback.onVideoFrameCreated(rtpFrame)
      } else {
        // Set FU-A header
        header[1] = header[4] and 0x1F // FU header type
        header[1] = header[1].plus(0x80).toByte()  // set start bit to 1
        // Set FU-A indicator
        header[0] = header[4] and 0x60 and 0xFF.toByte() // FU indicator NRI
        header[0] = header[0].plus(28).toByte()
        var sum = 1
        while (sum < naluLength) {
          val cont = if (naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
            maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2
          } else {
            naluLength - sum
          }
          val length = if (cont < bufferInfo.size - byteBuffer.position()) {
            cont
          } else {
            bufferInfo.size - byteBuffer.position()
          }
          val buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2)
          buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0]
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1]
          val rtpTs = updateTimeStamp(buffer, ts)
          byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length)
          sum += length
          // Last packet before next NAL
          if (sum >= naluLength) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = buffer[RtpConstants.RTP_HEADER_LENGTH + 1].plus(0x40).toByte()
            markPacket(buffer) //mark end frame
          }
          updateSeq(buffer)
          val rtpFrame = RtpFrame(buffer, rtpTs, length + RtpConstants.RTP_HEADER_LENGTH + 2, rtpPort, rtcpPort, channelIdentifier)
          videoPacketCallback.onVideoFrameCreated(rtpFrame)
          // Switch start bit
          header[1] = header[1] and 0x7F
        }
      }
    }
  }

  private fun setSpsPps(sps: ByteArray, pps: ByteArray) {
    stapA = ByteArray(sps.size + pps.size + 5)
    stapA?.let {
      // STAP-A NAL header is 24
      it[0] = 24

      // Write NALU 1 size into the array (NALU 1 is the SPS).
      it[1] = (sps.size shr 8).toByte()
      it[2] = (sps.size and 0xFF).toByte()

      // Write NALU 2 size into the array (NALU 2 is the PPS).
      it[sps.size + 3] = (pps.size shr 8).toByte()
      it[sps.size + 4] = (pps.size and 0xFF).toByte()

      // Write NALU 1 into the array, then write NALU 2 into the array.
      System.arraycopy(sps, 0, it, 3, sps.size)
      System.arraycopy(pps, 0, it, 5 + sps.size, pps.size)
    }
  }

  override fun reset() {
    super.reset()
    sendKeyFrame = false
  }
}