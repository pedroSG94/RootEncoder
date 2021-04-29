package com.pedro.rtmp.flv

/**
 * Created by pedro on 8/04/21.
 */
data class FlvPacket(val buffer: ByteArray, val timeStamp: Long, val length: Int, val type: FlvType)