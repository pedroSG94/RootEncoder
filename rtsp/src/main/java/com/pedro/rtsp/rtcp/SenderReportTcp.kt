package com.pedro.rtsp.rtcp

import android.util.Log
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.io.IOException
import java.io.OutputStream

/**
 * Created by pedro on 8/11/18.
 */
open class SenderReportTcp : BaseSenderReport() {

  private var outputStream: OutputStream? = null
  private val tcpHeader: ByteArray = byteArrayOf('$'.toByte(), 0, 0, PACKET_LENGTH.toByte())

  override fun setDataStream(outputStream: OutputStream, host: String) {
    this.outputStream = outputStream
  }

  @Throws(IOException::class)
  override fun sendReport(buffer: ByteArray, rtpFrame: RtpFrame, type: String, packetCount: Int, octetCount: Int, isEnableLogs: Boolean) {
    sendReportTCP(buffer, rtpFrame.channelIdentifier, type, packetCount, octetCount, isEnableLogs)
  }

  override fun close() {}

  @Throws(IOException::class)
  private fun sendReportTCP(buffer: ByteArray, channelIdentifier: Int, type: String, packet: Int, octet: Int, isEnableLogs: Boolean) {
    synchronized(RtpConstants.lock) {
      tcpHeader[1] = (2 * channelIdentifier + 1).toByte()
      outputStream?.write(tcpHeader)
      outputStream?.write(buffer, 0, PACKET_LENGTH)
      outputStream?.flush()
      if (isEnableLogs) {
        Log.i(TAG, "wrote report: $type, packets: $packet, octet: $octet")
      }
    }
  }
}