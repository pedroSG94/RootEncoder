package com.pedro.whip.webrtc.stun

class StunCommand(
    val header: StunHeader,
    val attributes: List<Attribute>
) {
    fun toByteArray(): ByteArray {
        val bodyBytes = attributes.map { it.toByteArray() }
        val bodyLength = bodyBytes.sumOf { it.size }
        val headerBytes = header.toByteArray(bodyLength)
        val bytes = ByteArray(headerBytes.size + bodyLength)
        headerBytes.copyInto(bytes, 0, headerBytes.size)
        var currentPosition = headerBytes.size
        bodyBytes.forEach {
            currentPosition += it.size
            it.copyInto(bytes, currentPosition, it.size)
        }
        return bytes
    }
}