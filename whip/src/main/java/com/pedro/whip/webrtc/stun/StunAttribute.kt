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

package com.pedro.whip.webrtc.stun

import com.pedro.common.toByteArray
import java.nio.ByteBuffer

class StunAttribute(
    val type: AttributeType,
    val value: ByteArray
) {
    fun toByteArray(): ByteArray {
        //type 2 bytes + length 2 bytes
        val padding = (4 - (value.size % 4)) % 4
        val buffer = ByteBuffer.allocate(4 + value.size + padding)
        buffer.putShort(type.value.toShort())
        buffer.putShort(value.size.toShort())
        buffer.put(value)
        if (padding > 0) buffer.put(ByteArray(padding))
        return buffer.toByteArray()
    }

    override fun toString(): String {
        return "StunAttribute(type=$type, value=${value.contentToString()})"
    }
}