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
}