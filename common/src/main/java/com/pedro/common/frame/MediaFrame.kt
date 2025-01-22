package com.pedro.common.frame

import java.nio.ByteBuffer

data class MediaFrame(
    val data: ByteBuffer,
    val info: Info,
    val type: Type
) {
    data class Info(
        val offset: Int,
        val size: Int,
        val timestamp: Long,
        val isKeyFrame: Boolean
    )

    enum class Type {
        VIDEO, AUDIO
    }
}
