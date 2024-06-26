/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.rtmp.amf

import com.pedro.rtmp.amf.v0.AmfData
import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.amf.v0.AmfString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfObjectTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf object`() {
    val buffer = byteArrayOf(3, 0, 4, 110, 97, 109, 101, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 3, 97, 103, 101, 0, 64, 52, 0, 0, 0, 0, 0, 0, 0, 0, 9)
    val map = hashMapOf<AmfString, AmfData>()
    map[AmfString("name")] = AmfString("random")
    map[AmfString("age")] = AmfNumber(20.0)

    val input = ByteArrayInputStream(buffer)
    val amfObject = AmfData.getAmfData(input)

    assertTrue(amfObject is AmfObject)
    val myObject = (amfObject as AmfObject).getProperties().mapKeys { it.key.value }
    map.forEach { (key, value) ->
      val readValue = myObject[key.value]
      if (readValue is AmfString && value is AmfString) {
        assertEquals(value.value, readValue.value)
      } else if (readValue is AmfNumber && value is AmfNumber) {
        assertEquals(value.value, readValue.value, 0.0)
      } else {
        assertTrue(false)
      }
    }
  }

  @Test
  fun `GIVEN a amf object WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(3, 0, 4, 110, 97, 109, 101, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 3, 97, 103, 101, 0, 64, 52, 0, 0, 0, 0, 0, 0, 0, 0, 9)
    val output = ByteArrayOutputStream()

    val map = linkedMapOf(
      AmfString("name") to AmfString("random"),
      AmfString("age") to AmfNumber(20.0)
    )

    val amfObject = AmfObject(map)
    amfObject.writeHeader(output)
    amfObject.writeBody(output)

println(output.toByteArray().contentToString())
    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}