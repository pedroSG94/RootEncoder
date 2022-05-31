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
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.*

/**
 * Created by pedro on 7/11/18.
 */
open class RtspSender(private val connectCheckerRtsp: ConnectCheckerRtsp) : VideoPacketCallback, AudioPacketCallback {

  private var videoPacket: BasePacket? = null
  private var aacPacket: AacPacket? = null
  private var rtpSocket: BaseRtpSocket? = null
  private var baseSenderReport: BaseSenderReport? = null
  @Volatile
  private var running = false

  @Volatile
  private var rtpFrameBlockingQueue: BlockingQueue<RtpFrame> = LinkedBlockingQueue(defaultCacheSize)
  private var thread: ExecutorService? = null
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
    videoPacket = if (vps == null) H264Packet(sps, pps, this) else H265Packet(sps, pps, vps, this)
  }

  fun setAudioInfo(sampleRate: Int) {
    aacPacket = AacPacket(sampleRate, this)
  }

  /**
   * @return number of packets
   */
  private val defaultCacheSize: Int
    get() = 10 * 1024 * 1024 / RtpConstants.MTU

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
    if (running) videoPacket?.createAndSendPacket(h264Buffer, info)
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) aacPacket?.createAndSendPacket(aacBuffer, info)
  }

  override fun onVideoFrameCreated(rtpFrame: RtpFrame) {
    try {
      rtpFrameBlockingQueue.add(rtpFrame)
    } catch (e: IllegalStateException) {
      Log.i(TAG, "Video frame discarded")
      droppedVideoFrames++
    }
  }

  override fun onAudioFrameCreated(rtpFrame: RtpFrame) {
    try {
      rtpFrameBlockingQueue.add(rtpFrame)
    } catch (e: IllegalStateException) {
      Log.i(TAG, "Audio frame discarded")
      droppedAudioFrames++
    }
  }

  fun start() {
    thread = Executors.newSingleThreadExecutor()
    running = true
    thread?.execute post@{
      val ssrcVideo = Random().nextInt().toLong()
      val ssrcAudio = Random().nextInt().toLong()
      baseSenderReport?.setSSRC(ssrcVideo, ssrcAudio)
      videoPacket?.setSSRC(ssrcVideo)
      aacPacket?.setSSRC(ssrcAudio)
      val isTcp = rtpSocket is RtpSocketTcp

      while (!Thread.interrupted() && running) {
        try {
          val rtpFrame = rtpFrameBlockingQueue.poll(1, TimeUnit.SECONDS)
          if (rtpFrame == null) {
            Log.i(TAG, "Skipping iteration, frame null")
            continue
          }
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
        } catch (e: Exception) {
          //InterruptedException is only when you disconnect manually, you don't need report it.
          if (e !is InterruptedException && running) {
            connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.message)
            Log.e(TAG, "send error: ", e)
          }
          return@post
        }
      }
    }
  }

  fun stop() {
    running = false
    thread?.shutdownNow()
    try {
      thread?.awaitTermination(100, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) { }
    thread = null
    rtpFrameBlockingQueue.clear()
    baseSenderReport?.reset()
    baseSenderReport?.close()
    rtpSocket?.close()
    aacPacket?.reset()
    videoPacket?.reset()
    resetSentAudioFrames()
    resetSentVideoFrames()
    resetDroppedAudioFrames()
    resetDroppedVideoFrames()
  }

  fun hasCongestion(): Boolean {
    val size = rtpFrameBlockingQueue.size.toFloat()
    val remaining = rtpFrameBlockingQueue.remainingCapacity().toFloat()
    val capacity = size + remaining
    return size >= capacity * 0.2f //more than 20% queue used. You could have congestion
  }

  fun resizeCache(newSize: Int) {
    if (newSize < rtpFrameBlockingQueue.size - rtpFrameBlockingQueue.remainingCapacity()) {
      throw RuntimeException("Can't fit current cache inside new cache size")
    }
    val tempQueue: BlockingQueue<RtpFrame> = LinkedBlockingQueue(newSize)
    rtpFrameBlockingQueue.drainTo(tempQueue)
    rtpFrameBlockingQueue = tempQueue
  }

  fun getCacheSize(): Int {
    return rtpFrameBlockingQueue.size
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