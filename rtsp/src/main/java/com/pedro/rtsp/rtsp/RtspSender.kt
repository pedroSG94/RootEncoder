/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtsp.rtsp

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.base.BaseSender
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.common.validMessage
import com.pedro.rtsp.rtcp.BaseSenderReport
import com.pedro.rtsp.rtp.packets.*
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp
import com.pedro.rtsp.rtsp.commands.CommandsManager
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.*

/**
 * Created by pedro on 7/11/18.
 */
class RtspSender(
  connectChecker: ConnectChecker,
  private val commandsManager: CommandsManager
): BaseSender(connectChecker, "RtspSender") {

  private var videoPacket: BasePacket = H264Packet()
  private var audioPacket: BasePacket = AacPacket()
  private var rtpSocket: BaseRtpSocket? = null
  private var baseSenderReport: BaseSenderReport? = null

  @Throws(IOException::class)
  fun setSocketsInfo(
    socketType: SocketType,
    protocol: Protocol, host: String,
    videoSourcePorts: Array<Int?>, audioSourcePorts: Array<Int?>,
    videoServerPorts: Array<Int?>, audioServerPorts: Array<Int?>,
  ) {
    rtpSocket = BaseRtpSocket.getInstance(socketType, protocol, host, videoSourcePorts[0], audioSourcePorts[0], videoServerPorts[0], audioServerPorts[0])
    baseSenderReport = BaseSenderReport.getInstance(socketType, protocol, host, videoSourcePorts[1], audioSourcePorts[1], videoServerPorts[1], audioServerPorts[1])
  }

  @Throws(IOException::class)
  suspend fun setSocket(socket: TcpStreamSocket) {
    rtpSocket?.setSocket(socket)
    baseSenderReport?.setSocket(socket)
  }

  override fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    videoPacket = when (commandsManager.videoCodec) {
      VideoCodec.H264 -> {
        if (pps == null) throw IllegalArgumentException("pps can't be null with h264")
        H264Packet().apply { sendVideoInfo(sps, pps) }
      }
      VideoCodec.H265 -> {
        if (vps == null || pps == null) throw IllegalArgumentException("pps or vps can't be null with h265")
        H265Packet()
      }
      VideoCodec.AV1 -> Av1Packet()
    }
  }

  override fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    audioPacket = when (commandsManager.audioCodec) {
      AudioCodec.G711 -> G711Packet().apply { setAudioInfo(sampleRate) }
      AudioCodec.AAC -> AacPacket().apply { setAudioInfo(sampleRate) }
      AudioCodec.OPUS -> OpusPacket().apply { setAudioInfo(sampleRate) }
    }
  }

  override suspend fun onRun() {
    val ssrcVideo = Random().nextInt().toLong()
    val ssrcAudio = Random().nextInt().toLong()
    baseSenderReport?.setSSRC(ssrcVideo, ssrcAudio)
    videoPacket.setSSRC(ssrcVideo)
    audioPacket.setSSRC(ssrcAudio)
    val isTcp = rtpSocket is RtpSocketTcp
    while (scope.isActive && running) {
      val error = runCatching {
        val mediaFrame = runInterruptible { queue.take() }
        getRtpPackets(mediaFrame) { rtpFrames ->
          var size = 0
          var isVideo = false
          rtpFrames.forEach { rtpFrame ->
            rtpSocket?.sendFrame(rtpFrame)
            //4 is tcp header length
            val packetSize = if (isTcp) rtpFrame.length + 4 else rtpFrame.length
            bytesSend += packetSize
            size += packetSize
            isVideo = rtpFrame.isVideoFrame()
            if (isVideo) {
              videoFramesSent++
            } else {
              audioFramesSent++
            }
            if (baseSenderReport?.update(rtpFrame) == true) {
              //4 is tcp header length
              val reportSize = if (isTcp) RtpConstants.REPORT_PACKET_LENGTH + 4 else RtpConstants.REPORT_PACKET_LENGTH
              bytesSend += reportSize
              if (isEnableLogs) Log.i(TAG, "wrote report")
            }
          }
          rtpSocket?.flush()
          if (isEnableLogs) {
            val type = if (isVideo) "Video" else "Audio"
            Log.i(TAG, "wrote $type packet, size $size")
          }
        }
      }.exceptionOrNull()
      if (error != null) {
        onMainThread {
          connectChecker.onConnectionFailed("Error send packet, ${error.validMessage()}")
        }
        Log.e(TAG, "send error: ", error)
        running = false
        return
      }
    }
  }

  override suspend fun stopImp(clear: Boolean) {
    baseSenderReport?.reset()
    baseSenderReport?.close()
    rtpSocket?.close()
    audioPacket.reset()
    videoPacket.reset()
  }

  private suspend fun getRtpPackets(mediaFrame: MediaFrame?, callback: suspend (List<RtpFrame>) -> Unit) {
    if (mediaFrame == null) return
    when (mediaFrame.type) {
      MediaFrame.Type.VIDEO -> videoPacket.createAndSendPacket(mediaFrame) { callback(it) }
      MediaFrame.Type.AUDIO -> audioPacket.createAndSendPacket(mediaFrame) { callback(it) }
    }
  }
}