package com.pedro.rtmp.amf.v3

import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class Amf3XmlDocument(value: String = ""): Amf3String(value) {

  @Throws(IOException::class, SAXException::class)
  fun getDocument(): Document {
    val dom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    return dom.parse(InputSource(StringReader(value)))
  }

  override fun getType(): Amf3Type = Amf3Type.XML_DOC

  override fun toString(): String {
    return "Amf3XmlDocument value: $value"
  }
}