package com.pedro.rtsp.rtp.packets

import android.media.MediaCodec
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.setLong
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Created by pedro on 27/11/18.
 */
abstract class BasePacket(private val clock: Long, private val payloadType: Int) {

  protected var channelIdentifier: Int = 0
  protected var rtpPort = 0
  protected var rtcpPort = 0
  private var seq = 0
  private var ssrc = 0
  protected val maxPacketSize = RtpConstants.MTU - 28
  protected val TAG = "BasePacket"

  abstract fun createAndSendPacket(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)

  fun setPorts(rtpPort: Int, rtcpPort: Int) {
    this.rtpPort = rtpPort
    this.rtcpPort = rtcpPort
  }

  open fun reset() {
    seq = 0
    ssrc = 0
  }

  fun setSSRC(ssrc: Int) {
    this.ssrc = ssrc
  }

  protected fun getBuffer(size: Int): ByteArray {
    val buffer = ByteArray(size)
    buffer[0] = "10000000".toInt(2).toByte()
    buffer[1] = payloadType.toByte()
    setLongSSRC(buffer, ssrc)
    requestBuffer(buffer)
    return buffer
  }

  protected fun updateTimeStamp(buffer: ByteArray, timestamp: Long): Long {
    val ts = timestamp * clock / 1000000000L
    buffer.setLong(ts, 4, 8)
    return ts
  }

  protected fun updateSeq(buffer: ByteArray) {
    buffer.setLong((++seq).toLong(), 2, 4)
  }

  protected fun markPacket(buffer: ByteArray) {
    buffer[1] = buffer[1] or (0x80).toByte()
  }

  private fun setLongSSRC(buffer: ByteArray, ssrc: Int) {
    buffer.setLong(ssrc.toLong(), 8, 12)
  }

  private fun requestBuffer(buffer: ByteArray) {
    buffer[1] = buffer[1] and 0x7F
  }
}