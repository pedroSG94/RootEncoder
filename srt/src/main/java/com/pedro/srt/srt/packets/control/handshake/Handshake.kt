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

package com.pedro.srt.srt.packets.control.handshake

import com.pedro.srt.srt.packets.ControlPacket
import com.pedro.srt.srt.packets.control.ControlType
import com.pedro.srt.srt.packets.control.handshake.extension.HandshakeExtension
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.readUInt16
import com.pedro.srt.utils.readUInt32
import com.pedro.srt.utils.writeUInt16
import com.pedro.srt.utils.writeUInt32
import java.io.InputStream
import java.net.InetAddress

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
  private var handshakeVersion: Int = 4,
  var encryption: EncryptionType = EncryptionType.NONE,
  var extensionField: Int = ExtensionField.KM_REQ.value,
  var initialPacketSequence: Int = 0,
  var MTU: Int = Constants.MTU,
  var flowWindowsSize: Int = 8192,
  var handshakeType: HandshakeType = HandshakeType.INDUCTION,
  var srtSocketId: Int = 762640158,
  var synCookie: Int = 0,
  var ipAddress: String = "0.0.0.0", //128 bits (32 bits each number)
  var handshakeExtension: HandshakeExtension? = null
): ControlPacket(ControlType.HANDSHAKE) {

  fun write(ts: Int, socketId: Int) {
    //control packet header (16 bytes)
    super.writeHeader(ts, socketId)
    //body (32 bytes + peer ip)
    writeBody()
  }
  //40
  private fun writeBody() {
    buffer.writeUInt32(handshakeVersion)
    buffer.writeUInt16(encryption.value)
    buffer.writeUInt16(extensionField)
    buffer.writeUInt32(initialPacketSequence and 0x7FFFFFFF) //31 bits
    buffer.writeUInt32(MTU)
    buffer.writeUInt32(flowWindowsSize)
    buffer.writeUInt32(handshakeType.value)
    buffer.writeUInt32(srtSocketId)
    buffer.writeUInt32(synCookie)
    writeAddress()
    handshakeExtension?.let {
      it.write()
      buffer.write(it.getData())
    }
  }

  private fun writeAddress() {
    val address = InetAddress.getByName(ipAddress)
    val bytes = address.address.toList().chunked(4).map { it.reversed() }
    bytes.forEach {
      buffer.write(it.toByteArray())
    }
    if (bytes.size == 1) { //ipv4
      buffer.writeUInt32(0)
      buffer.writeUInt32(0)
      buffer.writeUInt32(0)
    }
  }

  fun read(input: InputStream) {
    super.readHeader(input)
    readBody(input)
  }

  private fun readBody(input: InputStream) {
    handshakeVersion = input.readUInt32()
    encryption = EncryptionType.from(input.readUInt16())
    extensionField = input.readUInt16()
    initialPacketSequence = input.readUInt32() and 0x7FFFFFFF // 31 bits
    MTU = input.readUInt32()
    flowWindowsSize = input.readUInt32()
    handshakeType = HandshakeType.from(input.readUInt32())
    srtSocketId = input.readUInt32()
    synCookie = input.readUInt32()
    readAddress(input)
  }

  private fun readAddress(input: InputStream): String {
    val num1 = input.readUInt32()
    val num2 = input.readUInt32()
    val num3 = input.readUInt32()
    val num4 = input.readUInt32()
    return "$num1.$num2.$num3.$num4"
  }

  fun isErrorType(): Boolean {
    return (handshakeType.value >= HandshakeType.SRT_REJ_UNKNOWN.value && handshakeType.value <= HandshakeType.SRT_REJ_CRYPTO.value)
  }

  override fun toString(): String {
    return "Handshake(handshakeVersion=$handshakeVersion, encryption=$encryption, extensionField=$extensionField, initialPacketSequence=$initialPacketSequence, MTU=$MTU, flowWindowsSize=$flowWindowsSize, handshakeType=$handshakeType, srtSocketId=$srtSocketId, synCookie=$synCookie, ipAddress='$ipAddress', handshakeExtension=$handshakeExtension)"
  }
}