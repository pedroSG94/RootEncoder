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

package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.common.TimeUtils
import com.pedro.rtmp.utils.socket.RtmpSocket
import java.io.IOException
import kotlin.random.Random

/**
 * Created by pedro on 8/04/21.
 *
 * The C0 and S0 packets are a single octet
 *
 * The version defined by this
 * specification is 3. Values 0-2 are deprecated values used by
 * earlier proprietary products; 4-31 are reserved for future
 * implementations; and 32-255 are not allowed
 * 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+
 * | version |
 * +-+-+-+-+-+-+-+-+
 *
 * The C1 and S1 packets are 1536 octets long
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | time (4 bytes) | local generated timestamp
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | zero (4 bytes) |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | random bytes |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | random bytes |
 * | (cont) |
 * | .... |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The C2 and S2 packets are 1536 octets long, and nearly an echo of S1 and C1 (respectively).
 *
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | time (4 bytes) | s1 timestamp for c2 or c1 for s2. In this case s1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | time2 (4 bytes) | timestamp of previous packet (s1 or c1). In this case c1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | random echo | random data field sent by the peer in s1 for c2 or s2 for c1.
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | random echo | random data field sent by the peer in s1 for c2 or s2 for c1.
 * | (cont) |
 * | .... |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
class Handshake {

  private val TAG = "Handshake"

  private val protocolVersion = 0x03
  private val handshakeSize = 1536
  private var timestampC1 = 0

  @Throws(IOException::class)
  suspend fun sendHandshake(socket: RtmpSocket): Boolean {
    writeC0(socket)
    val c1 = writeC1(socket)
    socket.flush()
    readS0(socket)
    val s1 = readS1(socket)
    writeC2(socket, s1)
    socket.flush()
    readS2(socket, c1)
    return true
  }

  @Throws(IOException::class)
  private suspend fun writeC0(socket: RtmpSocket) {
    Log.i(TAG, "writing C0")
    socket.write(protocolVersion)
    Log.i(TAG, "C0 write successful")
  }

  @Throws(IOException::class)
  private suspend fun writeC1(socket: RtmpSocket): ByteArray {
    Log.i(TAG, "writing C1")
    val c1 = ByteArray(handshakeSize)

    timestampC1 = (TimeUtils.getCurrentTimeMillis() / 1000).toInt()
    Log.i(TAG, "writing time $timestampC1 to c1")
    val timestampData = ByteArray(4)
    timestampData[0] = (timestampC1 ushr 24).toByte()
    timestampData[1] = (timestampC1 ushr 16).toByte()
    timestampData[2] = (timestampC1 ushr 8).toByte()
    timestampData[3] = timestampC1.toByte()
    System.arraycopy(timestampData, 0, c1, 0, timestampData.size)

    Log.i(TAG, "writing zero to c1")
    val zeroData = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    System.arraycopy(zeroData, 0, c1, timestampData.size, zeroData.size)

    Log.i(TAG, "writing random to c1")
    val random = Random.Default
    val randomData = ByteArray(handshakeSize - 8)
    for (i in randomData.indices step 1) { //step UInt8 size
      //random with UInt8 max value
      val uInt8 = random.nextInt().toByte()
      randomData[i] = uInt8
    }
    System.arraycopy(randomData, 0, c1, 8, randomData.size)
    socket.write(c1)
    Log.i(TAG, "C1 write successful")
    return c1
  }

  @Throws(IOException::class)
  private suspend fun writeC2(socket: RtmpSocket, s1: ByteArray) {
    Log.i(TAG, "writing C2")
    socket.write(s1)
    Log.i(TAG, "C2 write successful")
  }

  @Throws(IOException::class)
  private suspend fun readS0(socket: RtmpSocket): ByteArray {
    Log.i(TAG, "reading S0")
    val response = socket.read()
    if (response == protocolVersion || response == 72) {
      Log.i(TAG, "read S0 successful")
      return byteArrayOf(response.toByte())
    } else {
      throw IOException("$TAG error, unexpected $response S0 received")
    }
  }

  @Throws(IOException::class)
  private suspend fun readS1(socket: RtmpSocket): ByteArray {
    Log.i(TAG, "reading S1")
    val s1 = ByteArray(handshakeSize)
    socket.readUntil(s1)
    Log.i(TAG, "read S1 successful")
    return s1
  }

  @Throws(IOException::class)
  private suspend fun readS2(socket: RtmpSocket, c1: ByteArray): ByteArray {
    Log.i(TAG, "reading S2")
    val s2 = ByteArray(handshakeSize)
    socket.readUntil(s2)
    //S2 should be equals to C1 but we can skip this
    if (!s2.contentEquals(c1)) {
      Log.e(TAG, "S2 content is different that C1")
    }
    Log.i(TAG, "read S2 successful")
    return s2
  }
}