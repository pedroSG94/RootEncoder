package com.pedro.srt.srt

import com.pedro.common.TimeUtils
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.utils.SrtSocket
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.text.clear

class PacketRetransmission {
  companion object {
    private const val MIN_RETRANSMIT_BYTES_PER_SECOND = 8_000
    private const val MIN_RESEND_INTERVAL_US = 20_000
    private const val MEDIA_RATE_WINDOW_US = 1_000_000L
    private const val MEDIA_RATE_WARMUP_WINDOW_US = 200_000L
    private const val RETRANSMIT_BURST_WINDOW_US = 500_000L
    private const val MEDIA_RATE_EWMA_OLD = 0.8
    private const val MEDIA_RATE_EWMA_NEW = 0.2
  }

  private val packetHandlingQueue = mutableListOf<DataPacket>()
  /**
   * Max retransmit bandwidth as a percentage of the estimated media rate.
   * Values <= 0 disable the limit (legacy behavior).
   */
  var retransmitOverheadPercent: Int = 25
  @Volatile
  private var rtt = 0
  @Volatile
  private var rttVariance = 0
  private var mediaBytesPerSecond = 0.0
  private var mediaWindowStartUs = 0L
  private var mediaWindowBytes = 0L
  private var retransmitTokens = 0.0
  private var lastTokenRefillUs = 0L
  private var bucketInitialized = false
  //used for unique packet lost
  private var lastNakSequence = 0
  private var lastNakInitialized = false

  fun addPacket(packet: DataPacket) {
    packetHandlingQueue.add(packet)
  }

  fun updateRtt(rtt: Int, rttVariance: Int, latency: Int) {
    val maxUs = latency * 1000
    this.rtt = rtt.coerceIn(0, maxUs)
    this.rttVariance = rttVariance.coerceIn(0, maxUs)
  }

  fun reset() {
    packetHandlingQueue.clear()
    rtt = 0
    rttVariance = 0
    mediaBytesPerSecond = 0.0
    mediaWindowStartUs = 0L
    mediaWindowBytes = 0L
    retransmitTokens = 0.0
    lastTokenRefillUs = 0L
    bucketInitialized = false
    lastNakSequence = 0
    lastNakInitialized = false
  }

  @Throws(IOException::class)
  suspend fun reSendPackets(
    lostRanges: List<Pair<Int, Int>>,
    socket: SrtSocket?,
    nowTs: Int,
    latency: Int,
    mtu: Int
  ): Int {
    val budgetEnabled = retransmitOverheadPercent > 0
    val nowUs = TimeUtils.getCurrentTimeMicro()
    if (budgetEnabled) refillRetransmitTokens(nowUs, latency, mtu)

    val latencyUs = latency * 1000
    val minResendInterval = min(max(rtt + 4 * rttVariance, MIN_RESEND_INTERVAL_US), latencyUs / 4)
    val newlyReported = countNewLostPackets(lostRanges)
    var budgetExhausted = false
    for (packet in packetHandlingQueue) {
      if (!isInLostRange(packet.sequenceNumber, lostRanges)) continue
      if ((nowTs - packet.ts + rtt / 2) >= latencyUs) continue
      if (packet.retransmitted && (nowTs - packet.lastSentTs) < minResendInterval) continue
      if (budgetEnabled) {
        if (budgetExhausted) continue
        val packetSize = packet.getSize()
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

  suspend fun updateHandlingQueue(lastPacketSequence: Int) {
    packetHandlingQueue.removeAll {
      //discard confirmed packets
      val diff = (lastPacketSequence - it.sequenceNumber) and 0x7FFFFFFF
      diff in 1 until 0x40000000
    }
  }

  private fun countNewLostPackets(lostRanges: List<Pair<Int, Int>>): Int {
    var count = 0
    lostRanges.forEach { (from, to) ->
      val size = ((to - from) and 0x7FFFFFFF) + 1
      if (!lastNakInitialized) {
        lastNakInitialized = true
        lastNakSequence = to
        count += size
      } else {
        val ahead = (to - lastNakSequence) and 0x7FFFFFFF
        if (ahead in 1 until 0x40000000) {
          count += min(ahead, size)
          lastNakSequence = to
        }
      }
    }
    return count
  }

  private fun isInLostRange(sequenceNumber: Int, lostRanges: List<Pair<Int, Int>>): Boolean {
    return lostRanges.any { (min, max) ->
      ((sequenceNumber - min) and 0x7FFFFFFF) <= ((max - min) and 0x7FFFFFFF)
    }
  }

  fun trackMediaBytes(bytes: Int, nowUs: Long) {
    if (mediaWindowStartUs == 0L) mediaWindowStartUs = nowUs
    mediaWindowBytes += bytes
    val elapsed = nowUs - mediaWindowStartUs
    val window = if (mediaBytesPerSecond > 0.0) MEDIA_RATE_WINDOW_US else MEDIA_RATE_WARMUP_WINDOW_US
    if (elapsed >= window) {
      val rate = mediaWindowBytes.toDouble() * MEDIA_RATE_WINDOW_US / elapsed
      //follow an increase at once to not limit below the real media rate, average a decrease
      mediaBytesPerSecond = if (rate > mediaBytesPerSecond) rate else {
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

  private fun getRetransmitCapacity(rate: Double, latency: Int, mtu: Int): Int {
    val burstCapacity = if (mediaBytesPerSecond > 0.0) {
      (mediaBytesPerSecond * RETRANSMIT_BURST_WINDOW_US / MEDIA_RATE_WINDOW_US).toInt()
    } else {
      0
    }
    return max(burstCapacity, max((rate * latency / 1000.0).toInt(), mtu))
  }

  fun dropTooLatePackets(nowTs: Int, latency: Int) {
    val thresholdUs = latency * 1000
    val firstKept = packetHandlingQueue.indexOfFirst { (nowTs - it.ts) <= thresholdUs }
    if (firstKept > 0) packetHandlingQueue.subList(0, firstKept).clear()
  }

  private fun refillRetransmitTokens(nowUs: Long, latency: Int, mtu: Int) {
    if (!bucketInitialized) {
      val rate = getRetransmitRate()
      retransmitTokens = getRetransmitCapacity(rate, latency, mtu).toDouble()
      lastTokenRefillUs = nowUs
      bucketInitialized = true
      return
    }
    val elapsedUs = nowUs - lastTokenRefillUs
    if (elapsedUs <= 0) return
    val rate = getRetransmitRate()
    val capacity = getRetransmitCapacity(rate, latency, mtu)
    retransmitTokens = min(retransmitTokens + rate * elapsedUs / MEDIA_RATE_WINDOW_US, capacity.toDouble())
    lastTokenRefillUs = nowUs
  }
}