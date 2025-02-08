package com.pedro.whip.webrtc.stun

enum class Type(val value: Int) {
    REQUEST(0x0001), INDICATION(0x0011), SUCCESS(0x0101), ERROR(0x0111)
}