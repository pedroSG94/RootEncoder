package com.pedro.whip.webrtc.stun

import java.io.ByteArrayOutputStream

class StunCommand(
    val header: StunHeader,
    val attributes: List<StunAttribute>,
) {
    fun toByteArray(remotePass: String): ByteArray {
        val output = ByteArrayOutputStream()

        val bodyBytes = attributes.map { it.toByteArray() }
        val bodyLength = bodyBytes.sumOf { it.size }.plus(24 + 8) //integrity + fingerprint
        val headerBytes = header.toByteArray(bodyLength)
        output.write(headerBytes)
        bodyBytes.forEach { output.write(it) }

        //all command contain message integrity and fingerprint
        val hmac = StunAttributeValueParser.createMessageIntegrity(output.toByteArray(), remotePass)
        val messageIntegrity = StunAttribute(AttributeType.MESSAGE_INTEGRITY, hmac)
        output.write(messageIntegrity.toByteArray())

        val crc32 = StunAttributeValueParser.createFingerprint(output.toByteArray())
        val fingerprint = StunAttribute(AttributeType.FINGERPRINT, crc32)
        output.write(fingerprint.toByteArray())

        return output.toByteArray()
    }

    override fun toString(): String {
        return "StunCommand(header=$header, attributes=$attributes)"
    }
}