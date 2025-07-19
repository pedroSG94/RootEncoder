package com.pedro.whip.webrtc.stun

import java.io.ByteArrayOutputStream

class StunCommand(
    val header: StunHeader,
    val attributes: List<StunAttribute>,
) {
    fun toByteArray(remotePass: String): ByteArray {
        val integritySize = 24
        val fingerprintSize = 8
        val output = ByteArrayOutputStream()
        val bodyBytes = attributes.map { it.toByteArray() }
        val bodyLength = bodyBytes.sumOf { it.size }

        output.write(header.toByteArray(bodyLength + integritySize))
        bodyBytes.forEach { output.write(it) }

        //all command contain message integrity and fingerprint
        val hmac = StunAttributeValueParser.createMessageIntegrity(output.toByteArray(), remotePass)
        val messageIntegrity = StunAttribute(AttributeType.MESSAGE_INTEGRITY, hmac)

        val finalOutput = ByteArrayOutputStream()
        finalOutput.write(header.toByteArray(bodyLength + integritySize + fingerprintSize))
        bodyBytes.forEach { finalOutput.write(it) }
        finalOutput.write(messageIntegrity.toByteArray())

        val crc32 = StunAttributeValueParser.createFingerprint(finalOutput.toByteArray())
        val fingerprint = StunAttribute(AttributeType.FINGERPRINT, crc32)
        finalOutput.write(fingerprint.toByteArray())

        return finalOutput.toByteArray()
    }

    override fun toString(): String {
        return "StunCommand(header=$header, attributes=$attributes)"
    }
}