/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.srt.mpeg2ts

import com.pedro.srt.srt.packets.PacketType
import com.pedro.srt.utils.put48Bits
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 24/8/23.
 *
 * Header (2 bytes):
 *
 * Adaptation field length -> 8 bits
 * Discontinuity indicator -> 1 bit
 * Random access indicator -> 1 bit
 * Elementary stream priority indicator -> 1 bit
 * PCR flag -> 1 bit
 * OPCR flag -> 1 bit
 * Splicing point flag -> 1 bit
 * Transport private data flag -> 1 bit
 * Adaptation field extension flag -> 1 bit
 *
 * Optional fields
 * PCR -> 48 bits
 * OPCR -> 48 bits
 * Splice countdown -> 8 bits
 * Transport private data length -> 8 bits
 *
 * Transport private data -> variable
 * Adaptation extension -> variable
 * Stuffing bytes -> variable
 */
data class AdaptationField(
    private val discontinuityIndicator: Boolean = false,
    private val randomAccessIndicator: Boolean = false,
    private val elementaryStreamPriorityIndicator: Boolean = false,
    //optionals
    private val pcr: Long? = null,
    private val opcr: Long? = null,
    private val spliceCountdown: Byte? = null,
    private val transportPrivateData: ByteArray? = null,
    private val adaptationExtension: ByteArray? = null,
    private val stuffingBytes: ByteArray? = null,
) {
    private val length: Int = calculateSize()
    private val transportPrivateDataLength: Int = transportPrivateData?.size ?: 0
        
    fun getData(): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        buffer.put(length.toByte())
        val pcrFlag = pcr != null
        val opcrFlag = opcr != null
        val splicingPointFlag = spliceCountdown != null
        val transportPrivateDataFlag = transportPrivateData != null
        val adaptationFieldExtensionFlag = adaptationExtension != null

        val indicatorsAndFlags: Byte = ((discontinuityIndicator.toInt() shl 7) or
            (randomAccessIndicator.toInt() shl 6) or
            (elementaryStreamPriorityIndicator.toInt() shl 5) or
            (pcrFlag.toInt() shl 4) or (opcrFlag.toInt() shl 3) or
            (splicingPointFlag.toInt() shl 2) or
            (transportPrivateDataFlag.toInt() shl 1) or adaptationFieldExtensionFlag.toInt()).toByte()

        buffer.put(indicatorsAndFlags)
        pcr?.let { buffer.put48Bits(it) }
        opcr?.let { buffer.put48Bits(it) }
        spliceCountdown?.let { buffer.put(it) }
        transportPrivateData?.let { buffer.put(it) }
        transportPrivateData?.let { buffer.put(it) }
        stuffingBytes?.let { buffer.put(it) }
        return buffer.array()
    }

    private fun calculateSize(): Int {
        return 2 + (if (pcr != null) 6 else 0) + (if (opcr != null) 6 else 0) + 
            (if (spliceCountdown != null) 1 else 0) +
            (if (transportPrivateDataLength > 0) transportPrivateDataLength + 1 else 0) +
            (adaptationExtension?.size ?: 0) + (stuffingBytes?.size ?: 0)
    }
    fun getSize(): Int = length
}