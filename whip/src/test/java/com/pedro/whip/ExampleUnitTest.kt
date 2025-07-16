package com.pedro.whip

import org.junit.Test

import org.junit.Assert.*
import java.io.ByteArrayOutputStream

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
}