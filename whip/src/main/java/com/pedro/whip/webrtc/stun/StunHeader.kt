package com.pedro.whip.webrtc.stun

import com.pedro.common.toByteArray
import com.pedro.whip.utils.Constants
import java.math.BigInteger
import java.nio.ByteBuffer

data class StunHeader(
    val type: Type,
    val id: BigInteger //12 bytes number
) {
    fun toByteArray(bodyLength: Int): ByteArray {
        val buffer = ByteBuffer.allocate(20)
        buffer.putShort(type.value.toShort())
        buffer.putShort(bodyLength.toShort())
        buffer.putInt(Constants.MAGIC_COOKIE) //constant
        buffer.put(id.toByteArray())
        return buffer.toByteArray()
    }
}