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

enum class AttributeType(val value: Int) {
    Reserved(0x0000),
    MAPPED_ADDRESS(0x0001),
    RESPONSE_ADDRESS(0x0002),
    CHANGE_ADDRESS(0x0003),
    SOURCE_ADDRESS(0x0004),
    CHANGED_ADDRESS(0x0005),
    USERNAME(0x0006),
    PASSWORD(0x0007),
    MESSAGE_INTEGRITY(0x0008),
    ERROR_CODE(0x0009),
    UNKNOWN_ATTRIBUTES(0x000A),
    REFLECTED_FROM(0x000B),
    REALM(0x0014),
    NONCE(0x0015),
    XOR_MAPPED_ADDRESS(0x0020),
    PRIORITY(0x0024),
    SOFTWARE(0x8022),
    ALTERNATE_SERVER(0x8023),
    FINGERPRINT(0x8028),
    ICE_CONTROLLED(0x8029),
    ICE_CONTROLLING(0x802a),
    USE_CANDIDATE(0x0025)
}