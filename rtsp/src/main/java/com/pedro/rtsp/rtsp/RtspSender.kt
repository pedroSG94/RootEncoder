package com.pedro.rtsp.rtsp

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtsp.rtcp.BaseSenderReport
import com.pedro.rtsp.rtp.packets.*
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket
import com.pedro.rtsp.utils.BitrateManager
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by pedro on 7/11/18.
 */
class RtspSender(private val connectCheckerRtsp: ConnectCheckerRtsp) : VideoPacketCallback, AudioPacketCallback {

  private var videoPacket: BasePacket? = null
  private var aacPacket: AacPacket? = null
  private var rtpSocket: BaseRtpSocket? = null
  private var baseSenderReport: BaseSenderReport? = null

  @Volatile
  private var rtpFrameBlockingQueue: BlockingQueue<RtpFrame> = LinkedBlockingQueue(defaultCacheSize)
  private var thread: Job? = null
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
    videoPacket?.createAndSendPacket(h264Buffer, info)
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    aacPacket?.createAndSendPacket(aacBuffer, info)
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
    thread = GlobalScope.launch(Dispatchers.IO) {
      while (!Thread.interrupted()) {
        try {
          val rtpFrame = rtpFrameBlockingQueue.poll(1, TimeUnit.SECONDS)
          if (rtpFrame == null) {
            Log.i(TAG, "Skipping iteration, frame null")
            continue
          }
          rtpSocket?.sendFrame(rtpFrame, isEnableLogs)
          //bytes to bits
          bitrateManager.calculateBitrate(rtpFrame.length * 8.toLong())
          if (rtpFrame.isVideoFrame()) {
            videoFramesSent++
          } else {
            audioFramesSent++
          }
          baseSenderReport?.update(rtpFrame, isEnableLogs)
        } catch (e: InterruptedException) {
          Thread.currentThread().interrupt()
        } catch (e: IOException) {
          connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.message)
          Thread.currentThread().interrupt()
          Log.e(TAG, "send error: ", e)
        }
      }
    }
  }

  fun stop() {
    runBlocking {
      thread?.cancelAndJoin()
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
  }

  fun hasCongestion(): Boolean {
    val size = rtpFrameBlockingQueue.size.toFloat()
    val remaining = rtpFrameBlockingQueue.remainingCapacity().toFloat()
    val capacity = size + remaining
    return size >= capacity * 0.2 //more than 20% queue used. You could have congestion
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