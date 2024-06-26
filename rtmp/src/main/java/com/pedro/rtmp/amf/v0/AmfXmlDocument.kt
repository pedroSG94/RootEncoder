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

package com.pedro.rtmp.amf.v0

import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Created by pedro on 19/07/22.
 *
 * DOM representation of an XML document encoded as
 * a long string encoded in UTF-8 where 4 first bytes indicate string size
 */
class AmfXmlDocument(value: String = ""): AmfLongString(value) {

  @Throws(IOException::class, SAXException::class)
  fun getDocument(): Document {
    val dom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    return dom.parse(InputSource(StringReader(value)))
  }

  override fun getType(): AmfType = AmfType.XML_DOCUMENT

  override fun toString(): String {
    return "AmfXmlDocument value: $value"
  }
}