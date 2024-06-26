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
import com.pedro.rtmp.amf.v0.AmfXmlDocument
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by pedro on 10/9/23.
 */
class AmfXmlDocumentTest {

  private val xml = "<note>\n" +
      "<to>Tove</to>\n" +
      "<from>Jani</from>\n" +
      "<heading>Reminder</heading>\n" +
      "<body>Don't forget me this weekend!</body>\n" +
      "</note>"

  @Test
  fun `GIVEN a buffer WHEN read buffer THEN get expected amf xml document`() {
    val buffer = byteArrayOf(15, 0, 0, 0, 117, 60, 110, 111, 116, 101, 62, 10, 60, 116, 111, 62, 84, 111, 118, 101, 60, 47, 116, 111, 62, 10, 60, 102, 114, 111, 109, 62, 74, 97, 110, 105, 60, 47, 102, 114, 111, 109, 62, 10, 60, 104, 101, 97, 100, 105, 110, 103, 62, 82, 101, 109, 105, 110, 100, 101, 114, 60, 47, 104, 101, 97, 100, 105, 110, 103, 62, 10, 60, 98, 111, 100, 121, 62, 68, 111, 110, 39, 116, 32, 102, 111, 114, 103, 101, 116, 32, 109, 101, 32, 116, 104, 105, 115, 32, 119, 101, 101, 107, 101, 110, 100, 33, 60, 47, 98, 111, 100, 121, 62, 10, 60, 47, 110, 111, 116, 101, 62)

    val input = ByteArrayInputStream(buffer)
    val amfXmlDocument = AmfData.getAmfData(input)

    assertTrue(amfXmlDocument is AmfXmlDocument)
    val document = (amfXmlDocument as AmfXmlDocument).getDocument()
    assertEquals("note", document.documentElement.tagName)
  }

  @Test
  fun `GIVEN a amf xml document WHEN write in a buffer THEN get expected buffer`() {
    val expectedBuffer = byteArrayOf(15, 0, 0, 0, 117, 60, 110, 111, 116, 101, 62, 10, 60, 116, 111, 62, 84, 111, 118, 101, 60, 47, 116, 111, 62, 10, 60, 102, 114, 111, 109, 62, 74, 97, 110, 105, 60, 47, 102, 114, 111, 109, 62, 10, 60, 104, 101, 97, 100, 105, 110, 103, 62, 82, 101, 109, 105, 110, 100, 101, 114, 60, 47, 104, 101, 97, 100, 105, 110, 103, 62, 10, 60, 98, 111, 100, 121, 62, 68, 111, 110, 39, 116, 32, 102, 111, 114, 103, 101, 116, 32, 109, 101, 32, 116, 104, 105, 115, 32, 119, 101, 101, 107, 101, 110, 100, 33, 60, 47, 98, 111, 100, 121, 62, 10, 60, 47, 110, 111, 116, 101, 62)
    val output = ByteArrayOutputStream()

    val amfXmlDocument = AmfXmlDocument(xml)
    amfXmlDocument.writeHeader(output)
    amfXmlDocument.writeBody(output)

    assertArrayEquals(expectedBuffer, output.toByteArray())
  }
}