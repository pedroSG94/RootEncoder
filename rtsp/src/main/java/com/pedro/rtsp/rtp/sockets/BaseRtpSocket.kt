package com.pedro.rtsp.rtp.sockets

import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtpFrame
import java.io.IOException
import java.io.OutputStream

/**
 * Created by pedro on 7/11/18.
 */
abstract class BaseRtpSocket {

  protected val TAG = "BaseRtpSocket"

  companion object {
    @JvmStatic
    fun getInstance(protocol: Protocol, videoSourcePort: Int, audioSourcePort: Int): BaseRtpSocket {
      return if (protocol === Protocol.TCP) {
        RtpSocketTcp()
      } else {
        RtpSocketUdp(videoSourcePort, audioSourcePort)
      }
    }
  }

  abstract fun setDataStream(outputStream: OutputStream, host: String)

  @Throws(IOException::class)
  abstract fun sendFrame(rtpFrame: RtpFrame, isEnableLogs: Boolean)

  abstract fun close()
}