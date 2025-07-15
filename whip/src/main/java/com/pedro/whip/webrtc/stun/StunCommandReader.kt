package com.pedro.whip.webrtc.stun

import android.util.Log
import com.pedro.common.readUInt16
import com.pedro.common.readUInt32
import com.pedro.common.readUntil
import com.pedro.common.toUInt16
import com.pedro.common.toUInt32
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigInteger

object StunCommandReader {

    fun readPacket(byteArray: ByteArray): StunCommand {
        val input = ByteArrayInputStream(byteArray)
        val header = readHeader(input)
        val body = readBody(input)
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
        return StunHeader(type, id)
    }

    private fun readBody(input: InputStream): List<Attribute> {
        val attributes = mutableListOf<Attribute>()
        while (input.available() > 0) {
            val typeValue = input.readUInt16()
            val length = input.readUInt16()
            val padding = (4 - (length % 4)) % 4
            val value = ByteArray(length + padding)
            input.readUntil(value)
            val type = AttributeType.entries.find { it.value == typeValue } ?: AttributeType.UNKNOWN_ATTRIBUTES
            attributes.add(Attribute(type, value))
        }
        return attributes
    }
}