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

package com.pedro.rtmp.rtmp

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtmp.flv.FlvPacket
import com.pedro.rtmp.flv.FlvType
import com.pedro.rtmp.flv.audio.AacPacket
import com.pedro.rtmp.flv.audio.AudioPacketCallback
import com.pedro.rtmp.flv.video.H264Packet
import com.pedro.rtmp.flv.video.ProfileIop
import com.pedro.rtmp.flv.video.VideoPacketCallback
import com.pedro.rtmp.utils.BitrateManager
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtmp.utils.socket.RtmpSocket
import java.nio.ByteBuffer
import java.util.concurrent.*

/**
 * Created by pedro on 8/04/21.
 */
class RtmpSender(private val connectCheckerRtmp: ConnectCheckerRtmp,
  private val commandsManager: CommandsManager) : AudioPacketCallback, VideoPacketCallback {

  private var aacPacket = AacPacket(this)
  private var h264Packet = H264Packet(this)
  @Volatile
  private var running = false

  @Volatile
  private var flvPacketBlockingQueue: BlockingQueue<FlvPacket> = LinkedBlockingQueue(60)
  private var thread: ExecutorService? = null
  private var audioFramesSent: Long = 0
  private var videoFramesSent: Long = 0
  var socket: RtmpSocket? = null
  var droppedAudioFrames: Long = 0
    private set
  var droppedVideoFrames: Long = 0
    private set
  private val bitrateManager: BitrateManager = BitrateManager(connectCheckerRtmp)
  private var isEnableLogs = true

  companion object {
    private const val TAG = "RtmpSender"
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    h264Packet.sendVideoInfo(sps, pps)
  }

  fun setProfileIop(profileIop: ProfileIop) {
    h264Packet.profileIop = profileIop
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    aacPacket.sendAudioInfo(sampleRate, isStereo)
  }

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) h264Packet.createFlvVideoPacket(h264Buffer, info)
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (running) aacPacket.createFlvAudioPacket(aacBuffer, info)
  }

  override fun onVideoFrameCreated(flvPacket: FlvPacket) {
    try {
      flvPacketBlockingQueue.add(flvPacket)
    } catch (e: IllegalStateException) {
      Log.i(TAG, "Video frame discarded")
      droppedVideoFrames++
    }
  }

  override fun onAudioFrameCreated(flvPacket: FlvPacket) {
    try {
      flvPacketBlockingQueue.add(flvPacket)
    } catch (e: IllegalStateException) {
      Log.i(TAG, "Audio frame discarded")
      droppedAudioFrames++
    }
  }

  fun start() {
    thread = Executors.newSingleThreadExecutor()
    running = true
    thread?.execute post@{
      while (!Thread.interrupted() && running) {
        try {
          val flvPacket = flvPacketBlockingQueue.poll(1, TimeUnit.SECONDS)
          if (flvPacket == null) {
            Log.i(TAG, "Skipping iteration, frame null")
            continue
          }
          var size = 0
          if (flvPacket.type == FlvType.VIDEO) {
            videoFramesSent++
            socket?.let { socket ->
              size = commandsManager.sendVideoPacket(flvPacket, socket)
              if (isEnableLogs) {
                Log.i(TAG, "wrote Video packet, size $size")
              }
            }
          } else {
            audioFramesSent++
            socket?.let { socket ->
              size = commandsManager.sendAudioPacket(flvPacket, socket)
              if (isEnableLogs) {
                Log.i(TAG, "wrote Audio packet, size $size")
              }
            }
          }
          //bytes to bits
          bitrateManager.calculateBitrate(size * 8L)
        } catch (e: Exception) {
          //InterruptedException is only when you disconnect manually, you don't need report it.
          if (e !is InterruptedException && running) {
            connectCheckerRtmp.onConnectionFailedRtmp("Error send packet, " + e.message)
            Log.e(TAG, "send error: ", e)
          }
          return@post
        }
      }
    }
  }

  fun stop(clear: Boolean = true) {
    running = false
    thread?.shutdownNow()
    try {
      thread?.awaitTermination(100, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) { }
    thread = null
    flvPacketBlockingQueue.clear()
    aacPacket.reset()
    h264Packet.reset(clear)
    resetSentAudioFrames()
    resetSentVideoFrames()
    resetDroppedAudioFrames()
    resetDroppedVideoFrames()
  }

  fun hasCongestion(): Boolean {
    val size = flvPacketBlockingQueue.size.toFloat()
    val remaining = flvPacketBlockingQueue.remainingCapacity().toFloat()
    val capacity = size + remaining
    return size >= capacity * 0.2f //more than 20% queue used. You could have congestion
  }

  fun resizeCache(newSize: Int) {
    if (newSize < flvPacketBlockingQueue.size - flvPacketBlockingQueue.remainingCapacity()) {
      throw RuntimeException("Can't fit current cache inside new cache size")
    }
    val tempQueue: BlockingQueue<FlvPacket> = LinkedBlockingQueue(newSize)
    flvPacketBlockingQueue.drainTo(tempQueue)
    flvPacketBlockingQueue = tempQueue
  }

  fun getCacheSize(): Int {
    return flvPacketBlockingQueue.size
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