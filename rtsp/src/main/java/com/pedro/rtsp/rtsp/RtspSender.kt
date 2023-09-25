/*
 * Copyright (C) 2021 pedroSG94.
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

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtsp.rtcp.BaseSenderReport
import com.pedro.rtsp.rtp.packets.*
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp
import com.pedro.rtsp.utils.BitrateManager
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.onMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.*

/**
 * Created by pedro on 7/11/18.
 */
class RtspSender(private val connectCheckerRtsp: ConnectCheckerRtsp) {

  private var videoPacket: BasePacket? = null
  private var aacPacket: AacPacket? = null
  private var rtpSocket: BaseRtpSocket? = null
  private var baseSenderReport: BaseSenderReport? = null

  private val defaultCacheSize: Int
    get() = 10 * 1024 * 1024 / RtpConstants.MTU
  private var cacheSize = defaultCacheSize
  @Volatile
  private var itemsInQueue = 0
  @Volatile
  private var running = false

  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)
  private var queue = Channel<RtpFrame>(cacheSize)
  private var queueFlow = queue.receiveAsFlow()

  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set
  private val bitrateManager: BitrateManager = BitrateManager(connectCheckerRtsp)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "RtspSender"
  }

  @Throws(IOException::class)
  fun setSocketsInfo(protocol: Protocol, videoSourcePorts: IntArray, audioSourcePorts: IntArray) {
    rtpSocket = BaseRtpSocket.getInstance(protocol, videoSourcePorts[0], audioSourcePorts[0])
    baseSenderReport = BaseSenderReport.getInstance(protocol, videoSourcePorts[1], audioSourcePorts[1])
  }

  fun setVideoInfo(sps: ByteArray, pps: ByteArray, vps: ByteArray?) {
    videoPacket = if (vps == null) H264Packet(sps, pps) else H265Packet(sps, pps, vps)
  }

  fun setAudioInfo(sampleRate: Int) {
    aacPacket = AacPacket(sampleRate)
  }

  @Throws(IOException::class)
  fun setDataStream(outputStream: OutputStream, host: String) {
    rtpSocket?.setDataStream(outputStream, host)
    baseSenderReport?.setDataStream(outputStream, host)
  }

  fun setVideoPorts(rtpPort: Int, rtcpPort: Int) {
    videoPacket?.setPorts(rtpPort, rtcpPort)
  }

  fun setAudioPorts(rtpPort: Int, rtcpPort: Int) {
    aacPacket?.setPorts(rtpPort, rtcpPort)
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      videoPacket?.createAndSendPacket(h264Buffer, info) { rtpFrame ->
        val result = queue.trySend(rtpFrame)
        if (!result.isSuccess) {
          Log.i(TAG, "Video frame discarded")
          droppedVideoFrames++
        } else {
          itemsInQueue++
        }
      }
    }
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) {
      aacPacket?.createAndSendPacket(aacBuffer, info) { rtpFrame ->
        val result = queue.trySend(rtpFrame)
        if (!result.isSuccess) {
          Log.i(TAG, "Audio frame discarded")
          droppedAudioFrames++
        } else {
          itemsInQueue++
        }
      }
    }
  }

  fun start() {
    queue = Channel(cacheSize)
    queueFlow = queue.receiveAsFlow()
    running = true
    job = scope.launch {
      val ssrcVideo = Random().nextInt().toLong()
      val ssrcAudio = Random().nextInt().toLong()
      baseSenderReport?.setSSRC(ssrcVideo, ssrcAudio)
      videoPacket?.setSSRC(ssrcVideo)
      aacPacket?.setSSRC(ssrcAudio)
      val isTcp = rtpSocket is RtpSocketTcp
      queueFlow.collect { rtpFrame ->
        itemsInQueue--
        val error = runCatching {
          rtpSocket?.sendFrame(rtpFrame, isEnableLogs)
          //bytes to bits (4 is tcp header length)
          val packetSize = if (isTcp) rtpFrame.length + 4 else rtpFrame.length
          bitrateManager.calculateBitrate(packetSize * 8.toLong())
          if (rtpFrame.isVideoFrame()) {
            videoFramesSent++
          } else {
            audioFramesSent++
          }
          if (baseSenderReport?.update(rtpFrame, isEnableLogs) == true) {
            //bytes to bits (4 is tcp header length)
            val reportSize = if (isTcp) baseSenderReport?.PACKET_LENGTH ?: (0 + 4) else baseSenderReport?.PACKET_LENGTH ?: 0
            bitrateManager.calculateBitrate(reportSize * 8.toLong())
          }
        }.exceptionOrNull()
        if (error != null) {
          onMainThread {
            connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + error.message)
          }
          Log.e(TAG, "send error: ", error)
          return@collect
        }
      }
    }
  }

  suspend fun stop() {
    running = false
    queue.cancel()
    itemsInQueue = 0
    queue = Channel(cacheSize)
    queueFlow = queue.receiveAsFlow()
    baseSenderReport?.reset()
    baseSenderReport?.close()
    rtpSocket?.close()
    aacPacket?.reset()
    videoPacket?.reset()
    resetSentAudioFrames()
    resetSentVideoFrames()
    resetDroppedAudioFrames()
    resetDroppedVideoFrames()
    job?.cancelAndJoin()
    job = null
  }

  fun hasCongestion(): Boolean {
    val size = cacheSize
    val remaining = cacheSize - itemsInQueue
    val capacity = size + remaining
    return size >= capacity * 0.2f //more than 20% queue used. You could have congestion
  }

  fun resizeCache(newSize: Int) {
    if (!scope.isActive) {
      val tempQueue = Channel<RtpFrame>(newSize)
      queue = tempQueue
      queueFlow = queue.receiveAsFlow()
      cacheSize = newSize
    } else {
      throw RuntimeException("resize cache while streaming is not available")
    }
  }

  fun getCacheSize(): Int {
    return cacheSize
  }

  fun getSentAudioFrames(): Long {
    return audioFramesSent
  }

  fun getSentVideoFrames(): Long {
    return videoFramesSent
  }

  fun resetSentAudioFrames() {
    audioFramesSent = 0
  }

  fun resetSentVideoFrames() {
    videoFramesSent = 0
  }

  fun resetDroppedAudioFrames() {
    droppedAudioFrames = 0
  }

  fun resetDroppedVideoFrames() {
    droppedVideoFrames = 0
  }

  fun setLogs(enable: Boolean) {
    isEnableLogs = enable
  }
}