/*
 * Copyright (C) 2023 pedroSG94.
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
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import com.pedro.srt.srt.packets.control.Ack2
import com.pedro.srt.srt.packets.control.Shutdown
import com.pedro.srt.srt.packets.control.handshake.Handshake
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.SrtSocket
import com.pedro.srt.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.NetworkInterface
import kotlin.random.Random

/**
 * Created by pedro on 23/8/23.
 */
class CommandsManager {

  private val TAG = "CommandsManager"
  //used for packet lost
  private val packetHandlingQueue = mutableListOf<DataPacket>()

  var sequenceNumber: Int = generateInitialSequence()
  var messageNumber = 1
  var MTU = Constants.MTU
  var socketId = 0
  var startTS = 0L //microSeconds
  var audioDisabled = false
  var videoDisabled = false
  //Avoid write a packet in middle of other.
  private val writeSync = Mutex(locked = false)

  fun loadStartTs() {
    startTS = TimeUtils.getCurrentTimeMicro()
  }

  fun getTs(): Int {
    return (TimeUtils.getCurrentTimeMicro() - startTS).toInt()
  }

  @Throws(IOException::class)
  suspend fun writeHandshake(socket: SrtSocket?, handshake: Handshake = Handshake()) {
    writeSync.withLock {
      handshake.initialPacketSequence = sequenceNumber
      handshake.ipAddress = getIPAddress()
      handshake.write(getTs(), 0)
      Log.i(TAG, handshake.toString())
      socket?.write(handshake)
    }
  }

  @Throws(IOException::class)
  fun readHandshake(socket: SrtSocket?): Handshake {
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
        sequenceNumber = sequenceNumber++,
        packetPosition = packet.packetPosition,
        messageNumber = messageNumber++,
        payload = packet.buffer,
        ts = getTs(),
        socketId = socketId
      )
      packetHandlingQueue.add(dataPacket)
      dataPacket.write()
      socket?.write(dataPacket)
      return dataPacket.getSize()
    }
  }

  @Throws(IOException::class)
  suspend fun reSendPackets(packetsLost: List<Int>, socket: SrtSocket?) {
    writeSync.withLock {
      val dataPackets = packetHandlingQueue.filter { packetsLost.contains(it.sequenceNumber) }
      dataPackets.forEach { packet ->
        packet.messageNumber = messageNumber++
        packet.retransmitted = true
        packet.write()
        socket?.write(packet)
      }
    }
  }

  suspend fun updateHandlingQueue(lastPacketSequence: Int) {
    writeSync.withLock {
      packetHandlingQueue.removeAll { it.sequenceNumber < lastPacketSequence }
    }
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

  fun reset() {
    sequenceNumber = generateInitialSequence()
    messageNumber = 1
    MTU = Constants.MTU
    socketId = 0
    startTS = 0L
    packetHandlingQueue.clear()
  }

  private fun generateInitialSequence(): Int {
    return Random.nextInt(0, Int.MAX_VALUE)
  }

  private fun getIPAddress(): String {
    val interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList()
    val vpnInterfaces = interfaces.filter { it.displayName.contains("tun") }
    val address: String by lazy { interfaces.findAddress().firstOrNull() ?: "0.0.0.0" }
    return if (vpnInterfaces.isNotEmpty()) {
      val vpnAddresses = vpnInterfaces.findAddress()
      vpnAddresses.firstOrNull() ?: address
    } else {
      address
    }
  }

  private fun List<NetworkInterface>.findAddress(): List<String?> = this.asSequence()
    .map { addresses -> addresses.inetAddresses.asSequence() }
    .flatten()
    .filter { address -> !address.isLoopbackAddress }
    .map { it.hostAddress }
    .filter { address -> address?.contains(":") == false }
    .toList()
}