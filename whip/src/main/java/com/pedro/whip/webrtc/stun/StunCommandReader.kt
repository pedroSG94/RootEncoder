package com.pedro.whip.webrtc.stun

import com.pedro.common.readUInt16
import com.pedro.common.readUInt32
import com.pedro.common.readUntil
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigInteger

object StunCommandReader {

    fun readPacket(byteArray: ByteArray): StunCommand {
        val input = ByteArrayInputStream(byteArray)
        val header = readHeader(input)
        val body = readBody(input, header.length)
        return StunCommand(header, body)
    }

    private fun readHeader(input: InputStream): StunHeader {
        val typeValue = input.readUInt16()
        val length = input.readUInt16()
        val cookie = input.readUInt32()
        val idBytes = ByteArray(12)
        input.readUntil(idBytes)
        val id = BigInteger(idBytes)
        val type = Type.entries.find { it.value == typeValue } ?: Type.ERROR
        return StunHeader(type, length, cookie, id)
    }

    private fun readBody(input: InputStream, length: Int): List<StunAttribute> {
        val attributes = mutableListOf<StunAttribute>()
        var index = 0
        while (length > index) {
            val typeValue = input.readUInt16()
            index +=2
            val length = input.readUInt16()
            index +=2
            val value = ByteArray(length)
            input.readUntil(value)
            index += length
            val padding = (4 - (length % 4)) % 4
            if (padding > 0) {
                input.readUntil(ByteArray(padding))
                index += padding
            }
            val type = AttributeType.entries.find { it.value == typeValue } ?: AttributeType.UNKNOWN_ATTRIBUTES
            attributes.add(StunAttribute(type, value))
        }
        return attributes
    }
}