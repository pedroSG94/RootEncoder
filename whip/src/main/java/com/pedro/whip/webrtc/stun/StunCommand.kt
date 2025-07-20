package com.pedro.whip.webrtc.stun

import com.pedro.common.combine
import java.io.ByteArrayOutputStream

class StunCommand(
    val header: StunHeader,
    val attributes: List<StunAttribute>,
    val useIntegrity: Boolean = true,
    val useFingerprint: Boolean = true
) {

    fun toByteArray(remotePass: String = ""): ByteArray {
        val integritySize = 24
        val fingerprintSize = 8
        val integrityOutput = ByteArrayOutputStream()
        val finalOutput = ByteArrayOutputStream()
        val bodyBytes = attributes.map { it.toByteArray() }.combine()
        var size = bodyBytes.size

        if (useIntegrity) size += integritySize
        integrityOutput.write(header.toByteArray(size))
        integrityOutput.write(bodyBytes)

        val messageIntegrity = if (useIntegrity) {
            val hmac = StunAttributeValueParser.createMessageIntegrity(integrityOutput.toByteArray(), remotePass)
            StunAttribute(AttributeType.MESSAGE_INTEGRITY, hmac)
        } else null

        if (useFingerprint) size += fingerprintSize
        finalOutput.write(header.toByteArray(size))
        finalOutput.write(bodyBytes)
        messageIntegrity?.let { finalOutput.write(it.toByteArray()) }

        if (useFingerprint) {
            val crc32 = StunAttributeValueParser.createFingerprint(finalOutput.toByteArray())
            val fingerprint = StunAttribute(AttributeType.FINGERPRINT, crc32)
            finalOutput.write(fingerprint.toByteArray())
        }

        return finalOutput.toByteArray()
    }

    override fun toString(): String {
        return "StunCommand(header=$header, attributes=$attributes)"
    }
}