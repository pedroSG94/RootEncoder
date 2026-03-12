package com.pedro.srtreceiver

data class TsPacket(
    val pid: Int,
    val payloadStart: Boolean,
    val continuityCounter: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TsPacket

        if (pid != other.pid) return false
        if (payloadStart != other.payloadStart) return false
        if (continuityCounter != other.continuityCounter) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pid
        result = 31 * result + payloadStart.hashCode()
        result = 31 * result + continuityCounter
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
