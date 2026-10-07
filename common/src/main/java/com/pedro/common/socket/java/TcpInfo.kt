package com.pedro.common.socket.java

import androidx.annotation.Keep

@Keep
internal object TcpInfo {

    private val loaded by lazy {
        runCatching { System.loadLibrary("rootencoder_common") }.isSuccess
    }

    fun getRttMicros(fd: Int): Long? {
        if (!loaded) return null
        return runCatching { nativeGetRttMicros(fd) }
            .getOrNull()
            ?.takeIf { it > 0 }
    }

    private external fun nativeGetRttMicros(fd: Int): Long
}
