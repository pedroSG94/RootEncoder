package com.pedro.rtmp.flv

/**
 * Created by pedro on 8/04/21.
 */
data class FlvPacket(val buffer: ByteArray = byteArrayOf(), var timeStamp: Long = 0,
                     val length: Int = 0, val type: FlvType = FlvType.AUDIO)