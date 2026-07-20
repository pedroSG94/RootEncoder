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

package com.pedro.rtmp.amf.v3

import com.pedro.common.getUInt29Size
import com.pedro.common.readUInt16
import com.pedro.common.readUInt29
import com.pedro.common.readUntil
import com.pedro.common.writeUInt29
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 29/04/21.
 *
 * Written always as an anonymous dynamic object (U29O-traits 0x0B, empty class name).
 * Reading supports inline traits with sealed and dynamic members; references to the
 * implicit tables can't be resolved without payload scope, so they are rejected.
 */
open class Amf3Object(protected val properties: LinkedHashMap<Amf3String, Amf3Data> = LinkedHashMap()): Amf3Data() {

  //traits U29 + empty class name + end of dynamic members
  protected var bodySize = 3
  protected var reference = -1
  var className = ""
    protected set

  init {
    properties.forEach {
      bodySize += it.key.getSize()
      bodySize += it.value.getSize() + 1
    }
  }

  fun getProperty(name: String): Amf3Data? {
    properties.forEach {
      if (it.key.value == name) {
        return it.value
      }
    }
    return null
  }

  open fun setProperty(name: String, data: String)  = putProperty(name, Amf3String(data))

  open fun setProperty(name: String, data: Boolean)  = putProperty(name, if (data) Amf3True() else Amf3False())

  open fun setProperty(name: String, data: Amf3Data) = putProperty(name, data)

  open fun setProperty(name: String) = putProperty(name, Amf3Null())

  open fun setProperty(name: String, data: Double) = putProperty(name, Amf3Double(data))

  open fun setProperty(name: String, data: Any) {
    val newValue = when (data) {
      is String -> Amf3String(data)
      is Int -> Amf3Integer(data)
      is Double -> Amf3Double(data)
      is Float -> Amf3Double(data.toDouble())
      is Boolean -> if (data) Amf3True() else Amf3False()
      is Amf3Data -> data
      else -> throw IllegalArgumentException("Unsupported value type: ${data::class.java.name}")
    }
    putProperty(name, newValue)
  }

  private fun putProperty(name: String, value: Amf3Data) {
    //Amf3String doesn't implement equals, so an existing key must be located by value
    val existingKey = properties.keys.find { it.value == name }
    if (existingKey != null) {
      val previous = properties.put(existingKey, value)
      bodySize += value.getSize() - (previous?.getSize() ?: 0)
    } else {
      val key = Amf3String(name)
      properties[key] = value
      bodySize += key.getSize() + value.getSize() + 1
    }
  }

  @Throws(IOException::class)
  override fun readBody(input: InputStream) {
    properties.clear()
    className = ""
    val u29 = input.readUInt29()
    bodySize = u29.getUInt29Size()
    if (u29 and 0x01 == 0) { //reference to a previous object of this payload, no tables to resolve it
      reference = u29 ushr 1
      return
    }
    reference = -1
    if (u29 and 0x02 == 0) throw IOException("AMF3 traits references are not supported")
    val isDynamic = u29 and 0x08 != 0
    val sealedCount = u29 ushr 4

    val name = Amf3String()
    name.readBody(input)
    if (name.isReference) throw IOException("AMF3 string reference as class name is not supported")
    bodySize += name.getSize()
    className = name.value

    if (u29 and 0x04 != 0) { //U29O-traits-ext: opaque bytes defined by the class
      readExternalizable(input)
      return
    }

    val sealedNames = mutableListOf<Amf3String>()
    repeat(sealedCount) {
      val key = Amf3String()
      key.readBody(input)
      if (key.isReference) throw IOException("AMF3 string reference as property name is not supported")
      bodySize += key.getSize()
      sealedNames.add(key)
    }
    sealedNames.forEach { key ->
      val value = getAmf3Data(input)
      bodySize += value.getSize() + 1
      properties[key] = value
    }
    if (isDynamic) {
      while (true) {
        val key = Amf3String()
        key.readBody(input)
        if (key.isReference) throw IOException("AMF3 string reference as property name is not supported")
        bodySize += key.getSize()
        if (key.value.isEmpty()) break
        val value = getAmf3Data(input)
        bodySize += value.getSize() + 1
        properties[key] = value
      }
    }
  }

  /**
   * Externalizable payloads are opaque bytes only known by the class, so only the Red5
   * status classes (the ones a client receives in practice) are supported:
   * Status (also with empty class name): double clientid + UTF code, description, details, level
   * StatusObject: UTF code, description, level + AMF3 values application, additional
   */
  @Throws(IOException::class)
  private fun readExternalizable(input: InputStream) {
    when {
      className.isEmpty() || className.endsWith(".Status") -> {
        val clientId = Amf3Double()
        clientId.readBody(input)
        bodySize += clientId.getSize()
        properties[Amf3String("clientid")] = clientId
        properties[Amf3String("code")] = readExternalUtf(input)
        properties[Amf3String("description")] = readExternalUtf(input)
        properties[Amf3String("details")] = readExternalUtf(input)
        properties[Amf3String("level")] = readExternalUtf(input)
      }
      className.endsWith("StatusObject") -> {
        properties[Amf3String("code")] = readExternalUtf(input)
        properties[Amf3String("description")] = readExternalUtf(input)
        properties[Amf3String("level")] = readExternalUtf(input)
        properties[Amf3String("application")] = readExternalValue(input)
        properties[Amf3String("additional")] = readExternalValue(input)
      }
      else -> throw IOException("AMF3 externalizable class $className is not supported")
    }
  }

  @Throws(IOException::class)
  private fun readExternalUtf(input: InputStream): Amf3String {
    val length = input.readUInt16()
    val bytes = ByteArray(length)
    input.readUntil(bytes)
    bodySize += 2 + length
    return Amf3String(String(bytes, Charsets.UTF_8))
  }

  @Throws(IOException::class)
  private fun readExternalValue(input: InputStream): Amf3Data {
    val value = getAmf3Data(input)
    bodySize += value.getSize() + 1
    return value
  }

  @Throws(IOException::class)
  override fun writeBody(output: OutputStream) {
    output.writeUInt29(0x0B) //U29O-traits: inline, dynamic, 0 sealed members
    output.write(0x01) //empty class name (anonymous object)
    properties.forEach { (key, value) ->
      key.writeBody(output)
      value.writeHeader(output)
      value.writeBody(output)
    }
    output.write(0x01) //empty string, end of dynamic members
  }

  override fun getType(): Amf3Type = Amf3Type.OBJECT

  override fun getSize(): Int = bodySize

  override fun toString(): String {
    return "Amf3Object(className='$className', properties=${properties.entries.joinToString { "${it.key.value}=${it.value}" }})"
  }
}
