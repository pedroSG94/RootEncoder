package com.pedro.rtsp.rtp.sockets

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
 * Created by pedro on 7/11/18.
 */
open class RtpSocketUdp(videoSourcePort: Int, audioSourcePort: Int) : BaseRtpSocket() {

  private var multicastSocketVideo: MulticastSocket? = null
  private var multicastSocketAudio: MulticastSocket? = null
  private val datagramPacket = DatagramPacket(byteArrayOf(0), 1)

  init {
    try {
      multicastSocketVideo = MulticastSocket(videoSourcePort)
      multicastSocketVideo?.timeToLive = 64
      multicastSocketAudio = MulticastSocket(audioSourcePort)
      multicastSocketAudio?.timeToLive = 64
    } catch (e: IOException) {
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
  override fun sendFrame(rtpFrame: RtpFrame, isEnableLogs: Boolean) {
    sendFrameUDP(rtpFrame, isEnableLogs)
  }

  override fun close() {
    multicastSocketVideo?.close()
    multicastSocketAudio?.close()
  }

  @Throws(IOException::class)
  private fun sendFrameUDP(rtpFrame: RtpFrame, isEnableLogs: Boolean) {
    synchronized(RtpConstants.lock) {
      datagramPacket.data = rtpFrame.buffer
      datagramPacket.port = rtpFrame.rtpPort
      datagramPacket.length = rtpFrame.length
      if (rtpFrame.isVideoFrame()) {
        multicastSocketVideo?.send(datagramPacket)
      } else {
        multicastSocketAudio?.send(datagramPacket)
      }
      if (isEnableLogs) {
        Log.i(TAG, "wrote packet: ${(if (rtpFrame.isVideoFrame()) "Video" else "Audio")}, size: ${rtpFrame.length}, port: ${rtpFrame.rtpPort}")
      }
    }
  }
}