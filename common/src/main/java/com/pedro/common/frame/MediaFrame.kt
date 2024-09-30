package com.pedro.common.frame

import java.nio.ByteBuffer

data class MediaFrame(
    val data: ByteBuffer,
    val info: MediaFrame.Info,
    val type: MediaFrame.Type
) {
    data class Info(
        val offset: Int,
        val size: Int,
        val timestamp: Long,
        val flags: Int
    )

    enum class Type {
        VIDEO, AUDIO
    }
}
