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
class AdaptationField {
    private val length: Byte = 0
    private val discontinuityIndicator: Boolean = false
    private val randomAccessIndicator: Boolean = false
    private val elementaryStreamPriorityIndicator: Boolean = false
    private val pcrFlag: Boolean = false
    private val opcrFlag: Boolean = false
    private val splicingPointFlag: Boolean = false
    private val transportPrivateDataFlag: Boolean = false
    private val adaptationFieldExtensionFlag: Boolean = false
    //optionals
    private val pcr: Long = 0L
    private val opcr: Long = 0L
    private val spliceCountdown: Byte = 0
    private val transportPrivateDataLength: Byte = 0

    private val transportPrivateData: ByteArray = byteArrayOf()
    private val adaptationExtension: ByteArray = byteArrayOf()
    private val stuffingBytes: ByteArray = byteArrayOf() //always 0xFF
}