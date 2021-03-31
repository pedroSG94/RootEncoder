package com.pedro.rtsp.rtcp

import android.util.Log
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.UnknownHostException

/**
 * Created by pedro on 8/11/18.
 */
open class SenderReportUdp(videoSourcePort: Int, audioSourcePort: Int) : BaseSenderReport() {

  private var multicastSocketVideo: MulticastSocket? = null
  private var multicastSocketAudio: MulticastSocket? = null
  private val datagramPacket = DatagramPacket(byteArrayOf(0), 1)

  init {
    try {
      multicastSocketVideo = MulticastSocket(videoSourcePort)
      multicastSocketVideo?.timeToLive = 64
      multicastSocketAudio = MulticastSocket(audioSourcePort)
      multicastSocketAudio?.timeToLive = 64
    } catch (e: Exception) {
      Log.e(TAG, "Error", e)
    }
  }

  override fun setDataStream(outputStream: OutputStream, host: String) {
    try {
      datagramPacket.address = InetAddress.getByName(host)
    } catch (e: UnknownHostException) {
      Log.e(TAG, "Error", e)
    }
  }

  @Throws(IOException::class)
  override fun sendReport(buffer: ByteArray, rtpFrame: RtpFrame, type: String, packetCount: Int, octetCount: Int, isEnableLogs: Boolean) {
    sendReportUDP(buffer, rtpFrame.rtcpPort, type, packetCount, octetCount, isEnableLogs)
  }

  override fun close() {
    multicastSocketVideo?.close()
    multicastSocketAudio?.close()
  }

  @Throws(IOException::class)
  private fun sendReportUDP(buffer: ByteArray, port: Int, type: String, packet: Int, octet: Int, isEnableLogs: Boolean) {
    synchronized(RtpConstants.lock) {
      datagramPacket.data = buffer
      datagramPacket.port = port
      datagramPacket.length = PACKET_LENGTH
      if (type == "Video") {
        multicastSocketVideo?.send(datagramPacket)
      } else {
        multicastSocketAudio?.send(datagramPacket)
      }
      if (isEnableLogs) {
        Log.i(TAG, "wrote report: $type, port: $port, packets: $packet, octet: $octet")
      }
    }
  }
}