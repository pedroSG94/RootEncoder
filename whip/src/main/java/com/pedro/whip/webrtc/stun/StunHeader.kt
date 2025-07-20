package com.pedro.whip.webrtc.stun

import com.pedro.common.toByteArray
import java.nio.ByteBuffer

class StunHeader(
    val type: HeaderType,
    val length: Int,
    val cookie: Int,
    val id: ByteArray //12 bytes number
) {
    fun toByteArray(bodyLength: Int): ByteArray {
        val buffer = ByteBuffer.allocate(20)
        buffer.putShort(type.value.toShort())
        buffer.putShort(bodyLength.toShort())
        buffer.putInt(cookie) //constant
        buffer.put(id)
        return buffer.toByteArray()
    }

    override fun toString(): String {
        return "StunHeader(type=$type, length=$length, cookie=$cookie, id=${id.contentToString()})"
    }
}