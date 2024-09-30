package com.pedro.common.frame

import android.media.MediaCodec
import java.nio.ByteBuffer

data class MediaFrame(
    val data: ByteBuffer,
    val info: MediaCodec.BufferInfo,
    val type: MediaFrameType
)
