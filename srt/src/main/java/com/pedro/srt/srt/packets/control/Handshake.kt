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

package com.pedro.srt.srt.packets.control

import android.util.Log
import com.pedro.srt.srt.packets.ControlPacket
import com.pedro.srt.srt.packets.ControlType
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.readUInt16
import com.pedro.srt.utils.readUInt32
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Created by pedro on 21/8/23.
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          HS Version                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |        Encryption Field       |        Extension Field        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |0|               Initial Packet Sequence Number                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                 Maximum Transmission Unit Size                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Maximum Flow Window Size                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         Handshake Type                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         SRT Socket ID                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           SYN Cookie                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * +                                                               +
 * |                                                               |
 * +                        Peer IP Address                        +
 * |                                                               |
 * +                                                               +
 * |                                                               |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |   Extension Type (optional)   | Extension Length (optional)   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * +                Extension Contents (optional)                  +
 * |                                                               |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *
 */
data class Handshake(
  private var handshakeVersion: Int = 5,
  var encryption: EncryptionType = EncryptionType.NONE,
  var extensionField: ExtensionField = ExtensionField.KM_REQ,
  var initialPacketSequence: Int = 1647295161,
  var MTU: Int = Constants.MTU,
  var flowWindowsSize: Int = 8192,
  var handshakeType: HandshakeType = HandshakeType.INDUCTION,
  var srtSocketId: Int = 679031891,
  var synCookie: Int = 0,
  var ipAddress: String = "0.0.0.0", //128 bits (32 bits each number)
  var extensionType: ExtensionType? = null,
  var extensionLength: Int? = null,
  var handshakeExtension: HandshakeExtension? = null
): ControlPacket(ControlType.HANDSHAKE) {

  fun write(ts: Int, socketId: Int) {
    //control packet header (16 bytes)
    super.writeHeader(ts, socketId)
    //body (32 bytes + peer ip)
    writeBody()
  }

  private fun writeBody() {
    writeInt(handshakeVersion)
    writeShort(encryption.value.toShort())
    writeShort(extensionField.value.toShort())
    writeInt(initialPacketSequence and 0x7FFFFFFF) //0 + 31 bits
    writeInt(MTU)
    writeInt(flowWindowsSize)
    writeInt(handshakeType.value)
    writeInt(srtSocketId)
    writeInt(synCookie)
    writeAddress()
    extensionType?.let { writeShort(it.value.toShort()) }
    extensionLength?.let { writeShort(it.toShort()) }
    handshakeExtension?.let { buffer.write(it.getData()) }
  }

  private fun writeAddress() {
      val numbers = ipAddress.split(".").map { it.trim().toInt() }
    numbers.forEach {
      writeInt(it)
    }
  }

  private fun readAddress(input: InputStream): String {
    val num1 = input.readUInt32()
    val num2 = input.readUInt32()
    val num3 = input.readUInt32()
    val num4 = input.readUInt32()
    return "$num1.$num2.$num3.$num4"
  }

  fun read(buffer: ByteArray) {
    val input = ByteArrayInputStream(buffer)
    super.readHeader(input)
    readBody(input)
  }

  private fun readBody(input: InputStream) {
    handshakeVersion = input.readUInt32()
    encryption = EncryptionType.from(input.readUInt16())
    try {
      extensionField = ExtensionField.from(input.readUInt16())
    } catch (e: Exception) {
      Log.e("Pedro", "error", e)
    }
    initialPacketSequence = input.readUInt32() and 0x7FFFFFFF // 31 bits
    MTU = input.readUInt32()
    flowWindowsSize = input.readUInt32()
    handshakeType = HandshakeType.from(input.readUInt32())
    srtSocketId = input.readUInt32()
    synCookie = input.readUInt32()
    readAddress(input)
  }

  override fun toString(): String {
    return "Handshake(typeSpecificInformation=$typeSpecificInformation, handshakeVersion=$handshakeVersion, encryption=$encryption, extensionField=$extensionField, initialPacketSequence=$initialPacketSequence, MTU=$MTU, flowWindowsSize=$flowWindowsSize, handshakeType=$handshakeType, srtSocketId=$srtSocketId, synCookie=$synCookie, ipAddress='$ipAddress', extensionType=$extensionType, extensionLength=$extensionLength, extensionPayload=${handshakeExtension.toString()})"
  }
}