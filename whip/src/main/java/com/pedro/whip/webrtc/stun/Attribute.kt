package com.pedro.whip.webrtc.stun

import com.pedro.common.toByteArray
import java.nio.ByteBuffer

class Attribute(
    val type: AttributeType,
    val value: ByteArray
) {
    fun toByteArray(): ByteArray {
        //type 2 bytes + length 2 bytes
        val buffer = ByteBuffer.allocate(4 + value.size)
        buffer.putShort(type.value.toShort())
        buffer.putShort(value.size.toShort())
        buffer.put(value)
        return buffer.toByteArray()
    }

    override fun toString(): String {
        return "Attribute(type=$type, value=${value.contentToString()})"
    }
}