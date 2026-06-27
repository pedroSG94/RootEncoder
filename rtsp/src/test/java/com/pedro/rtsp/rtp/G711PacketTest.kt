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

package com.pedro.rtsp.rtp

import com.pedro.common.frame.MediaFrame
import com.pedro.rtsp.rtp.packets.G711Packet
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.CryptoProperties
import com.pedro.rtsp.utils.RtpConstants
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by pedro on 17/12/23.
 */
class G711PacketTest {

  @Test
  fun `GIVEN g711 data WHEN create rtp packet THEN get expected packet`() = runTest {
    val timestamp = 123456789L
    val fakeG711 = ByteArray(30) { 0x05 }

    val info = MediaFrame.Info(0, fakeG711.size, timestamp, false)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(fakeG711), info, MediaFrame.Type.AUDIO)
    val g711Packet = G711Packet().apply { setAudioInfo(8000) }
    g711Packet.setSSRC(123456789)
    val frames = mutableListOf<RtpFrame>()
    g711Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    val expectedRtp = byteArrayOf(-128, -120, 0, 1, 0, 15, 18, 6, 7, 91, -51, 21).plus(fakeG711)
    val expectedTimeStamp = 987654L
    val expectedSize = RtpConstants.RTP_HEADER_LENGTH + info.size
    val packetResult = RtpFrame(expectedRtp, expectedTimeStamp, expectedSize, RtpConstants.trackAudio)
    assertEquals(1, frames.size)
    assertEquals(packetResult, frames[0])
  }

  /**
   * Validates the SRTP output of [G711Packet] against a packet built independently with the JCE.
   *
   * The expected buffer is: RTP header (12) + AES-CM encrypted payload + HMAC-SHA1-80 tag (10).
   *
   * AES-CM (RFC 3711 §4.1.1) is reproduced here with the standard "AES/CTR/NoPadding" cipher.
   * This is a valid independent oracle because the SRTP IV always has its low 16 bits set to
   * zero, so JCE's full 128-bit counter increment is equivalent to incrementing only the block
   * counter at bytes 14..15 (the RFC behaviour) for the small number of blocks used here. If the
   * packetizer placed/incremented the counter anywhere else, blocks after the first 16 bytes
   * would diverge and this test would fail.
   */
  @Test
  fun `GIVEN g711 data and crypto WHEN create srtp packet THEN get expected encrypted packet`() = runTest {
    val timestamp = 123456789L
    val ssrc = 123456789L
    // > 16 bytes on purpose so encryption spans several AES-CM blocks.
    val fakeG711 = ByteArray(30) { it.toByte() }

    // Deterministic SRTP session material (sizes per AES_CM_128_HMAC_SHA1_80).
    val sessionKey = ByteArray(16) { (it + 1).toByte() }
    val authKey = ByteArray(20) { (it + 100).toByte() }
    val salt = ByteArray(14) { (it + 50).toByte() }

    val info = MediaFrame.Info(0, fakeG711.size, timestamp, false)
    val mediaFrame = MediaFrame(ByteBuffer.wrap(fakeG711), info, MediaFrame.Type.AUDIO)
    val g711Packet = G711Packet().apply {
      setAudioInfo(8000)
      setSSRC(ssrc)
      setCryptoProperties(CryptoProperties(authKey, sessionKey, salt))
    }
    val frames = mutableListOf<RtpFrame>()
    g711Packet.createAndSendPacket(mediaFrame) { frames.addAll(it) }

    assertEquals(1, frames.size)

    // --- Build the expected SRTP packet independently ---
    val seq = 1L
    val roc = 0
    val rtpTimeStamp = 987654L

    val header = ByteArray(RtpConstants.RTP_HEADER_LENGTH)
    header[0] = 0x80.toByte()
    header[1] = (0x80 or RtpConstants.payloadTypeG711).toByte() // marker bit + payload type
    header.setBigEndian(seq, 2, 4)
    header.setBigEndian(rtpTimeStamp, 4, 8)
    header.setBigEndian(ssrc, 8, 12)

    val iv = srtpIv(ssrc, (roc.toLong() shl 16) or (seq and 0xFFFF), salt)
    val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
      init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), IvParameterSpec(iv))
    }
    val encryptedPayload = cipher.doFinal(fakeG711)

    val authenticated = header.plus(encryptedPayload)
    val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(authKey, "HmacSHA1")) }
    mac.update(authenticated)
    mac.update(byteArrayOf(0, 0, 0, roc.toByte())) // roc as big-endian uint32
    val hmac = mac.doFinal().copyOf(RtpConstants.HMAC_SIZE)

    val expectedBuffer = authenticated.plus(hmac)

    assertEquals(expectedBuffer.toList(), frames[0].buffer.toList())
    assertEquals(rtpTimeStamp, frames[0].timeStamp)
    assertEquals(RtpConstants.trackAudio, frames[0].channelIdentifier)
  }

  // RFC 3711 §4.1.1 SRTP IV: (salt * 2^16) XOR (ssrc * 2^64) XOR (index * 2^16), low 16 bits = 0.
  private fun srtpIv(ssrc: Long, index: Long, salt: ByteArray): ByteArray {
    val ivBase = ByteArray(16)
    ByteBuffer.wrap(ivBase, 4, 4).putInt(ssrc.toInt())
    val indexBytes = ByteBuffer.allocate(8).putLong(index).array()
    System.arraycopy(indexBytes, 2, ivBase, 8, 6)
    val paddedSalt = ByteArray(16).also { salt.copyInto(it) }
    return ByteArray(16) { (ivBase[it].toInt() xor paddedSalt[it].toInt()).toByte() }
  }

  private fun ByteArray.setBigEndian(value: Long, begin: Int, end: Int) {
    var v = value
    for (i in end - 1 downTo begin) {
      this[i] = (v and 0xFF).toByte()
      v = v shr 8
    }
  }
}
