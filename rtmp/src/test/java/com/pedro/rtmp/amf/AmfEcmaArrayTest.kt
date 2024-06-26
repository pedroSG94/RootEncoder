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
import com.pedro.rtmp.amf.v0.AmfEcmaArray
import com.pedro.rtmp.amf.v0.AmfNumber
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
class AmfEcmaArrayTest {

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf ecma array`() {
    val buffer = byteArrayOf(8, 0, 0, 0, 0, 0, 4, 110, 97, 109, 101, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 3, 97, 103, 101, 0, 64, 52, 0, 0, 0, 0, 0, 0, 0, 0, 9)
    val map = hashMapOf<AmfString, AmfData>()
    map[AmfString("name")] = AmfString("random")
    map[AmfString("age")] = AmfNumber(20.0)

    val input = ByteArrayInputStream(buffer)
    val amfEcmaArray = AmfData.getAmfData(input)

    assertTrue(amfEcmaArray is AmfEcmaArray)
    val ecmaArray = (amfEcmaArray as AmfEcmaArray).getProperties().mapKeys { it.key.value }
    map.forEach { (key, value) ->
      val readValue = ecmaArray[key.value]
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
  fun `GIVEN a amf ecma array WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(8, 0, 0, 0, 0, 0, 4, 110, 97, 109, 101, 2, 0, 6, 114, 97, 110, 100, 111, 109, 0, 3, 97, 103, 101, 0, 64, 52, 0, 0, 0, 0, 0, 0, 0, 0, 9)
    val output = ByteArrayOutputStream()

    val map = linkedMapOf<AmfString, AmfData>()
    map[AmfString("name")] = AmfString("random")
    map[AmfString("age")] = AmfNumber(20.0)
    val amfEcmaArray = AmfEcmaArray(map)
    amfEcmaArray.writeHeader(output)
    amfEcmaArray.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}