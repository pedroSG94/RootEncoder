/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.encoder.input.audio

class VolumeEffect: CustomAudioEffect() {

    var volume = 1f

    override fun process(pcmBuffer: ByteArray): ByteArray {
        if (volume == 1f) return pcmBuffer

        for (i in pcmBuffer.indices step 2) {
            var buf1 = pcmBuffer[i + 1].toShort()
            var buf2 = pcmBuffer[i].toShort()
            buf1 = ((buf1.toInt() and 0xff) shl 8).toShort()
            buf2 = (buf2.toInt() and 0xff).toShort()
            var res = (buf1.toInt() or buf2.toInt()).toShort()
            res = (res * volume).toInt().toShort()
            pcmBuffer[i] = res.toByte()
            pcmBuffer[i + 1] = (res.toInt() shr 8).toByte()
        }
        return pcmBuffer
    }
}