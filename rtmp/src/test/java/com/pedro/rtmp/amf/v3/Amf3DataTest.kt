/*
 * Copyright (C) 2026 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtmp.amf.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Amf3DataTest {

  private fun write(data: Amf3Data): ByteArray {
    val output = ByteArrayOutputStream()
    data.writeHeader(output)
    data.writeBody(output)
    return output.toByteArray()
  }

  private fun read(bytes: ByteArray): Amf3Data = Amf3Data.getAmf3Data(ByteArrayInputStream(bytes))

  @Test
  fun `object writes anonymous dynamic format`() {
    val obj = Amf3Object()
    obj.setProperty("name", "pedro")
    val expected = byteArrayOf(
      0x0A, 0x0B, 0x01, //object marker, U29O-traits dynamic 0 sealed, empty class name
      0x09, 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'e'.code.toByte(),
      0x06, 0x0B, 'p'.code.toByte(), 'e'.code.toByte(), 'd'.code.toByte(), 'r'.code.toByte(), 'o'.code.toByte(),
      0x01 //end of dynamic members
    )
    val bytes = write(obj)
    assertArrayEquals(expected, bytes)
    assertEquals(bytes.size - 1, obj.getSize())
  }

  @Test
  fun `object round trip with nested values`() {
    val obj = Amf3Object()
    obj.setProperty("code", "NetConnection.Connect.Success")
    obj.setProperty("objectEncoding", 3.0)
    obj.setProperty("count", 500)
    obj.setProperty("enabled", true)
    obj.setProperty("empty")
    val nested = Amf3Object()
    nested.setProperty("inner", "value")
    obj.setProperty("data", nested)

    val bytes = write(obj)
    assertEquals(bytes.size - 1, obj.getSize())
    val result = read(bytes) as Amf3Object
    assertEquals(bytes.size - 1, result.getSize())
    assertEquals("NetConnection.Connect.Success", (result.getProperty("code") as Amf3String).value)
    assertEquals(3.0, (result.getProperty("objectEncoding") as Amf3Double).value, 0.0)
    assertEquals(500, (result.getProperty("count") as Amf3Integer).value)
    assertTrue(result.getProperty("enabled") is Amf3True)
    assertTrue(result.getProperty("empty") is Amf3Null)
    assertEquals("value", ((result.getProperty("data") as Amf3Object).getProperty("inner") as Amf3String).value)
  }

  @Test
  fun `object read sealed members with class name`() {
    //typed object: 1 sealed member "level" of class "org.Test", value "status"
    val output = ByteArrayOutputStream()
    output.write(0x0A)
    output.write(0x13) //U29O-traits: 1 sealed, not dynamic (0b10011)
    Amf3String("org.Test").writeBody(output)
    Amf3String("level").writeBody(output)
    val value = Amf3String("status")
    value.writeHeader(output)
    value.writeBody(output)

    val bytes = output.toByteArray()
    val result = read(bytes) as Amf3Object
    assertEquals("org.Test", result.className)
    assertEquals("status", (result.getProperty("level") as Amf3String).value)
    assertEquals(bytes.size - 1, result.getSize())
  }

  @Test
  fun `object reference is read without consuming extra bytes`() {
    val result = read(byteArrayOf(0x0A, 0x00)) as Amf3Object
    assertEquals(1, result.getSize())
  }

  @Test
  fun `array round trip with dense and associative parts`() {
    val array = Amf3Array(mutableListOf(Amf3String("hvc1"), Amf3Integer(7)))
    array.setProperty("extra", Amf3String("yes"))

    val bytes = write(array)
    assertEquals(bytes.size - 1, array.getSize())
    val result = read(bytes) as Amf3Array
    assertEquals(bytes.size - 1, result.getSize())
    assertEquals(2, result.items.size)
    assertEquals("hvc1", (result.items[0] as Amf3String).value)
    assertEquals(7, (result.items[1] as Amf3Integer).value)
    assertEquals("yes", (result.getProperty("extra") as Amf3String).value)
  }

  @Test
  fun `dictionary round trip`() {
    val dictionary = Amf3Dictionary()
    dictionary.setProperty("width", 1920.0)
    dictionary.setProperty("codec", "avc1")

    val bytes = write(dictionary)
    assertEquals(bytes.size - 1, dictionary.getSize())
    val result = read(bytes) as Amf3Dictionary
    assertEquals(bytes.size - 1, result.getSize())
    assertEquals(1920.0, (result.getProperty("width") as Amf3Double).value, 0.0)
    assertEquals("avc1", (result.getProperty("codec") as Amf3String).value)
  }

  @Test
  fun `integer u29 boundaries round trip`() {
    val cases = mapOf(
      0 to 1, 127 to 1, 128 to 2, 0x3FFF to 2, 0x4000 to 3,
      0x1FFFFF to 3, 0x200000 to 4, 0xFFFFFFF to 4, -1 to 4, -0x10000000 to 4
    )
    cases.forEach { (value, expectedSize) ->
      val integer = Amf3Integer(value)
      assertEquals("size of $value", expectedSize, integer.getSize())
      val bytes = write(integer)
      assertEquals("written size of $value", expectedSize, bytes.size - 1)
      assertEquals("round trip of $value", value, (read(bytes) as Amf3Integer).value)
    }
  }

  @Test
  fun `string u29 length boundary round trip`() {
    //length 64: the shifted U29 (129) needs 2 bytes while the raw length only needs 1
    val text = "a".repeat(64)
    val string = Amf3String(text)
    val bytes = write(string)
    assertEquals(bytes.size - 1, string.getSize())
    val result = read(bytes) as Amf3String
    assertEquals(text, result.value)
    assertEquals(bytes.size - 1, result.getSize())
  }
}
