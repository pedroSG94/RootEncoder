package com.pedro.whip.webrtc.stun

import com.pedro.common.toUInt16
import com.pedro.common.toUInt32
import java.math.BigInteger

object StunCommandReader {

    fun readPacket(byteArray: ByteArray): StunCommand {
        val headerData = byteArray.copyOfRange(0, 20)
        val bodyData = byteArray.copyOfRange(20, byteArray.size)
        val header = readHeader(headerData)
        val body = readBody(bodyData)
        return StunCommand(header, body)
    }

    private fun readHeader(byteArray: ByteArray): StunHeader {
        val typeValue = byteArray.copyOfRange(0, 2).toUInt16()
        val length = byteArray.copyOfRange(2, 4).toUInt16()
        val cookie = byteArray.copyOfRange(4, 8).toUInt32()
        val id = BigInteger(byteArray.copyOfRange(8, byteArray.size))
        val type = Type.entries.find { it.value == typeValue } ?: Type.ERROR
        return StunHeader(type, length, cookie, id)
    }

    private fun readBody(byteArray: ByteArray): List<Attribute> {
        val attributes = mutableListOf<Attribute>()
        var index = 0
        while (index < byteArray.size) {
            val typeValue = byteArray.copyOfRange(index, index + 2).toUInt16()
            index += 2
            val length = byteArray.copyOfRange(index, index + 2).toUInt16()
            index += 2
            val value = byteArray.copyOfRange(index, index + length)
            index += length
            val type = AttributeType.entries.find { it.value == typeValue } ?: AttributeType.UNKNOWN_ATTRIBUTES
            attributes.add(Attribute(type, value))
        }
        return attributes
    }
}