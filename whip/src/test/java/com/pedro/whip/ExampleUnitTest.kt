package com.pedro.whip

import com.pedro.common.toUInt16
import com.pedro.whip.webrtc.stun.StunAttributeValueParser
import com.pedro.whip.webrtc.stun.StunCommandReader
import org.junit.Test

import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    val output = ByteArrayOutputStream()
    output.write(0x01)
    output.toByteArray()
    output.write(0x02)
    assertArrayEquals(output.toByteArray(), byteArrayOf(0x01, 0x02))
  }

  @Test
  fun datatobytes() {
    val data = "000100882112a442a73fa9b89a198bb6c8cd80a900240004fdfffffe0006001e46416577764943464c694e7350566d6a3a31756a3077613778666e7a67340000802200297c706970657c20776562525443206167656e7420666f7220496f542068747470733a2f2f70692e7065000000802a000822f2d972b16f7210"
    val extra = "000800140581111c6890e5fe8ba43c0fb5ad343de6de5e9280280004df1963fb"
    val integrityHeader = "00080014"
    val integrityValue = "0581111c6890e5fe8ba43c0fb5ad343de6de5e92"
    val finger = "df1963fb"

    val localPass = "6m0jej0mu9688g839vkgr9sdat"
    val remotePass = "RyvNHaZnWJUDYrXYETeMWAgQilBljZNn"
    val localFrag = "1uj0wa7xfnzg4"
    val remoteFrag = "FAewvICFLiNsPVmj"
    val bytes = hexStringToByteArray(data)
    val iResult = hexStringToByteArray(integrityValue)

    val l = 128.toUInt16()
    bytes[2] = l[0]
    bytes[3] = l[1]

    val result = StunAttributeValueParser.createMessageIntegrity(bytes, remotePass)
    assertArrayEquals(iResult, result)
  }

  fun hexStringToByteArray(hexString: String): ByteArray {
    return hexString.chunked(2)
      .map { it.toInt(16).toByte() }
      .toByteArray()
  }
}