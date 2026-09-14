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

package com.pedro.srt.srt

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.control.Ack2
import com.pedro.srt.srt.packets.control.KeepAlive
import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.EncryptionType
import com.pedro.srt.srt.packets.control.handshake.Handshake
import com.pedro.srt.srt.packets.data.KeyBasedEncryption
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.EncryptInfo
import com.pedro.srt.utils.EncryptionUtil
import com.pedro.srt.utils.SrtSocket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Created by pedro on 23/8/23.
 */
class CommandsManager(private val timeProvider: () -> Long = { TimeUtils.getCurrentTimeMicro() }) {

  private val TAG = "CommandsManager"
  //used for packet lost
  private val packetHandlingQueue = mutableListOf<DataPacket>()

  var sequenceNumber: Int = generateInitialSequence()
  var messageNumber = 1
  var MTU = Constants.MTU
  var socketId = 0
  //own socket id, must be unique per connection or the server can discard the handshake as duplicated
  private var localSocketId = generateSocketId()
  var startTS = 0L //microSeconds
  var audioDisabled = false
  var videoDisabled = false
  var host = ""
  var latency = 120 //in millis
  /**
   * Max retransmit bandwidth as a percentage of the estimated media rate.
   * Values <= 0 disable the limit (legacy behavior).
   */
  var retransmitOverheadPercent: Int = 25
  //Avoid write a packet in middle of other.
  private val writeSync = Mutex(locked = false)
  private var encryptor: EncryptionUtil? = null
  var videoCodec = VideoCodec.H264
  var audioCodec = AudioCodec.AAC

  private var rtt = 0
  private var rttVariance = 0
  private var mediaBytesPerSecond = 0.0
  private var mediaWindowStartUs = 0L
  private var mediaWindowBytes = 0L
  private var retransmitTokens = 0.0
  private var lastTokenRefillUs = 0L
  private var bucketInitialized = false

  fun setPassphrase(passphrase: String, type: EncryptionType) {
    encryptor = if (passphrase.isEmpty() || type == EncryptionType.NONE) null else EncryptionUtil(type, passphrase)
  }

  fun getEncryptInfo(): EncryptInfo? {
    return encryptor?.getEncryptInfo()
  }

  fun getEncryptType(): EncryptionType {
    return encryptor?.type ?: EncryptionType.NONE
  }

  fun encryptionEnabled() = encryptor != null

  fun loadStartTs() {
    startTS = timeProvider()
    localSocketId = generateSocketId()
  }

  fun getTs(): Int {
    return (timeProvider() - startTS).toInt()
  }

  suspend fun updateRtt(rtt: Int, rttVariance: Int) {
    writeSync.withLock {
      this.rtt = rtt
      this.rttVariance = rttVariance
    }
  }

  @Throws(IOException::class)
  suspend fun writeHandshake(socket: SrtSocket?, handshake: Handshake = Handshake()) {
    writeSync.withLock {
      handshake.initialPacketSequence = sequenceNumber
      handshake.srtSocketId = localSocketId
      handshake.ipAddress = host
      handshake.write(getTs(), 0)
      Log.i(TAG, handshake.toString())
      socket?.write(handshake)
    }
  }

  @Throws(IOException::class)
  suspend fun readHandshake(socket: SrtSocket?): Handshake {
    val handshakeBuffer = socket?.readBuffer() ?: throw IOException("read buffer failed, socket disconnected")
    val handshake = SrtPacket.getSrtPacket(handshakeBuffer)
    if (handshake is Handshake) {
      Log.i(TAG, handshake.toString())
      return handshake
    } else {
      throw IOException("unexpected response type: ${handshake.javaClass.name}")
    }
  }

  @Throws(IOException::class)
  suspend fun writeData(packet: MpegTsPacket, socket: SrtSocket?): Int {
    writeSync.withLock {
      if (sequenceNumber.toUInt() > 0x7FFFFFFFu) sequenceNumber = 0
      val dataPacket = DataPacket(
        encryption = if (encryptor != null) KeyBasedEncryption.PAIR_KEY else KeyBasedEncryption.NONE,
        sequenceNumber = sequenceNumber,
        packetPosition = packet.packetPosition,
        messageNumber = messageNumber++,
        payload = encryptor?.encrypt(packet.buffer, sequenceNumber) ?: packet.buffer,
        ts = getTs(),
        socketId = socketId
      )
      sequenceNumber++
      packetHandlingQueue.add(dataPacket)
      dropTooLatePackets(dataPacket.ts)
      trackMediaBytes(packet.buffer.size, timeProvider())
      dataPacket.write()
      socket?.write(dataPacket)
      return dataPacket.getSize()
    }
  }

  @Throws(IOException::class)
  suspend fun reSendPackets(lostRanges: List<Pair<Int, Int>>, socket: SrtSocket?): Int {
    writeSync.withLock {
      val unlimited = retransmitOverheadPercent <= 0
      val nowTs = getTs()
      val nowUs = timeProvider()
      if (!unlimited) {
        refillRetransmitTokens(nowUs)
      }
      val latencyUs = latency * 1000
      val minResendInterval = if (unlimited) 0 else {
        min(max(rtt + 4 * rttVariance, MIN_RESEND_INTERVAL_US), latencyUs / 4)
      }
      var newlyReported = 0
      var budgetExhausted = false
      for (packet in packetHandlingQueue) {
        if (!isInLostRange(packet.sequenceNumber, lostRanges)) continue
        if (!packet.nakReported) {
          packet.nakReported = true
          newlyReported++
        }
        if (!unlimited) {
          if ((nowTs - packet.ts + rtt / 2) >= latencyUs) continue
          // The gate only suppresses repeated reports of a packet that was already retransmitted;
          // the first NAK is always honored even when it arrives within minResendInterval of the original send.
          if (packet.retransmitted && (nowTs - packet.lastSentTs) < minResendInterval) continue
          if (budgetExhausted) continue
          val packetSize = dataPacketWireSize(packet)
          if (retransmitTokens < packetSize) {
            budgetExhausted = true
            continue
          }
          retransmitTokens -= packetSize
        }
        packet.retransmitted = true
        packet.write()
        socket?.write(packet)
        packet.lastSentTs = nowTs
      }
      return newlyReported
    }
  }

  suspend fun updateHandlingQueue(lastPacketSequence: Int) {
    writeSync.withLock {
      packetHandlingQueue.removeAll {
        //discard confirmed packets
        val diff = (lastPacketSequence - it.sequenceNumber) and 0x7FFFFFFF
        diff in 1 until 0x40000000
      }
    }
  }

  private fun isInLostRange(sequenceNumber: Int, lostRanges: List<Pair<Int, Int>>): Boolean {
    return lostRanges.any { (min, max) ->
      ((sequenceNumber - min) and 0x7FFFFFFF) <= ((max - min) and 0x7FFFFFFF)
    }
  }

  private fun trackMediaBytes(bytes: Int, nowUs: Long) {
    if (mediaWindowStartUs == 0L) mediaWindowStartUs = nowUs
    mediaWindowBytes += bytes
    val elapsed = nowUs - mediaWindowStartUs
    if (elapsed >= MEDIA_RATE_WINDOW_US) {
      val rate = mediaWindowBytes.toDouble() * MEDIA_RATE_WINDOW_US / elapsed
      mediaBytesPerSecond = if (mediaBytesPerSecond == 0.0) rate else {
        mediaBytesPerSecond * MEDIA_RATE_EWMA_OLD + rate * MEDIA_RATE_EWMA_NEW
      }
      mediaWindowStartUs = nowUs
      mediaWindowBytes = 0
    }
  }

  private fun getRetransmitRate(): Double {
    val percent = retransmitOverheadPercent
    val mediaRate = if (mediaBytesPerSecond > 0.0) mediaBytesPerSecond else MIN_RETRANSMIT_BYTES_PER_SECOND.toDouble()
    return max(mediaRate * percent / 100.0, MIN_RETRANSMIT_BYTES_PER_SECOND.toDouble())
  }

  private fun getRetransmitCapacity(rate: Double): Int {
    // Allow an immediate burst up to half a second of media so short loss events
    // (e.g. a brief link flap) are not retried one packet at a time on healthy links.
    val burstCapacity = if (mediaBytesPerSecond > 0.0) {
      (mediaBytesPerSecond * RETRANSMIT_BURST_WINDOW_US / MEDIA_RATE_WINDOW_US).toInt()
    } else {
      0
    }
    return max(burstCapacity, max((rate * latency / 1000.0).toInt(), MTU))
  }

  private fun refillRetransmitTokens(nowUs: Long) {
    if (!bucketInitialized) {
      val rate = getRetransmitRate()
      retransmitTokens = getRetransmitCapacity(rate).toDouble()
      lastTokenRefillUs = nowUs
      bucketInitialized = true
      return
    }
    val elapsedUs = nowUs - lastTokenRefillUs
    if (elapsedUs <= 0) return
    val rate = getRetransmitRate()
    val capacity = getRetransmitCapacity(rate)
    retransmitTokens = min(retransmitTokens + rate * elapsedUs / MEDIA_RATE_WINDOW_US, capacity.toDouble())
    lastTokenRefillUs = nowUs
  }

  private fun dataPacketWireSize(packet: DataPacket): Int {
    return packet.payload.size + DATA_HEADER_SIZE
  }

  private fun dropTooLatePackets(nowTs: Int) {
    val thresholdUs = latency * 1000
    val firstKept = packetHandlingQueue.indexOfFirst { (nowTs - it.ts) <= thresholdUs }
    if (firstKept > 0) packetHandlingQueue.subList(0, firstKept).clear()
  }

  @Throws(IOException::class)
  suspend fun writeAck2(ackSequence: Int, socket: SrtSocket?) {
    writeSync.withLock {
      val ack2 = Ack2(ackSequence)
      ack2.write(getTs(), socketId)
      socket?.write(ack2)
    }
  }

  @Throws(IOException::class)
  suspend fun writeShutdown(socket: SrtSocket?) {
    writeSync.withLock {
      val shutdown = Shutdown()
      shutdown.write(getTs(), socketId)
      socket?.write(shutdown)
    }
  }

  @Throws(IOException::class)
  suspend fun writeKeepAlive(socket: SrtSocket?) {
    writeSync.withLock {
      val keepAlive = KeepAlive()
      keepAlive.write(getTs(), socketId)
      socket?.write(keepAlive)
    }
  }

  fun reset() {
    sequenceNumber = generateInitialSequence()
    messageNumber = 1
    MTU = Constants.MTU
    socketId = 0
    startTS = 0L
    host = ""
    packetHandlingQueue.clear()
    rtt = 0
    rttVariance = 0
    mediaBytesPerSecond = 0.0
    mediaWindowStartUs = 0L
    mediaWindowBytes = 0L
    retransmitTokens = 0.0
    lastTokenRefillUs = 0L
    bucketInitialized = false
  }

  private fun generateInitialSequence(): Int {
    return Random.nextInt(0, Int.MAX_VALUE)
  }

  private fun generateSocketId(): Int {
    return Random.nextInt(1, Int.MAX_VALUE)
  }

  companion object {
    private const val DATA_HEADER_SIZE = 16
    private const val MIN_RETRANSMIT_BYTES_PER_SECOND = 8_000
    private const val MIN_RESEND_INTERVAL_US = 20_000
    private const val MEDIA_RATE_WINDOW_US = 1_000_000L
    private const val RETRANSMIT_BURST_WINDOW_US = 500_000L
    private const val MEDIA_RATE_EWMA_OLD = 0.8
    private const val MEDIA_RATE_EWMA_NEW = 0.2
  }
}
