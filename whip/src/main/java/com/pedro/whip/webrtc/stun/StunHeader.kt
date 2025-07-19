package com.pedro.whip.webrtc.stun

import com.pedro.common.toByteArray
import java.math.BigInteger
import java.nio.ByteBuffer

data class StunHeader(
    val type: Type,
    val length: Int,
    val cookie: Int,
    val id: BigInteger //12 bytes number
) {
    fun toByteArray(bodyLength: Int): ByteArray {
        val buffer = ByteBuffer.allocate(20)
        buffer.putShort(type.value.toShort())
        buffer.putShort(bodyLength.toShort())
        buffer.putInt(cookie) //constant
        buffer.put(id.toByteArray())
        return buffer.toByteArray()
    }

    override fun toString(): String {
        return "StunHeader(type=$type, length=$length, cookie=$cookie, id=$id)"
    }
}