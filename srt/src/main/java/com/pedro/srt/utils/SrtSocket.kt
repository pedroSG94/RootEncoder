/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.srt.utils

import android.util.Log
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.srt.srt.packets.DataPacket
import com.pedro.srt.srt.packets.SrtPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Created by pedro on 22/8/23.
 */
class SrtSocket(val type: SocketType, val host: String, val port: Int, timeout: Long) {
    private var nativeSock: Int = -1
    private val socket =
        StreamSocket.createUdpSocket(type, host, port, timeout, receiveSize = Constants.MTU)

    init {
        if (type == SocketType.NATIVE) {
            nativeSock = nativeOpen()
        }
    }

    suspend fun connect(mode: String = "listener") = withContext(Dispatchers.IO) {
        if (type == SocketType.NATIVE) {
            if (mode.contains("listener", ignoreCase = true)) {
                Log.d("SrtSocket", "NATIVE: Starting Server (Listener) on port $port")
                if (nativeBindAndListen(
                        nativeSock,
                        port
                    ) < 0
                ) throw IOException("Native Bind/Listen failed")

                val clientSock = nativeAccept(nativeSock)

                if (clientSock < 0) throw IOException("Native Accept failed")

                nativeClose(nativeSock)
                nativeSock = clientSock
                return@withContext
            }

            if (nativeConnect(
                    nativeSock,
                    host,
                    port,
                    "live"
                ) < 0
            ) throw IOException("Native Connect failed")
            return@withContext
        }
        socket.connect()
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        if (type == SocketType.NATIVE && nativeSock != -1) {
            nativeClose(nativeSock)
            nativeSock = -1
        }
        socket.close()
    }

    fun isConnected(): Boolean {
        if (type == SocketType.NATIVE) {
            return nativeSock != -1
        }
        return socket.isConnected()
    }

    fun isReachable(): Boolean {
        if (type == SocketType.NATIVE) {
            return nativeSock != -1
        }
        return socket.isReachable()
    }

    suspend fun write(srtPacket: SrtPacket) = withContext(Dispatchers.IO) {
        if (type == SocketType.NATIVE && nativeSock != -1) {
            if (srtPacket is DataPacket) {
                val result = nativeWrite(nativeSock, srtPacket.payload)
                if (result < 0) {
                    throw IOException("Native write failed: Connection broken")
                }
            } else {
                nativeWrite(nativeSock, srtPacket.getData())
            }
            return@withContext
        }

        socket.write(srtPacket.getData())
    }

    suspend fun readBuffer(): ByteArray = withContext(Dispatchers.IO) {
        if (type == SocketType.NATIVE && nativeSock != -1) {
            val buffer = ByteArray(Constants.MTU)
            val size = nativeRead(nativeSock, buffer)
            return@withContext if (size > 0) buffer.copyOf(size) else ByteArray(0)
        }
        return@withContext socket.read()
    }

    private external fun nativeOpen(): Int
    private external fun nativeBindAndListen(sock: Int, port: Int): Int
    private external fun nativeAccept(sock: Int): Int
    private external fun nativeConnect(sock: Int, host: String, port: Int, streamId: String): Int
    private external fun nativeWrite(sock: Int, data: ByteArray): Int
    private external fun nativeRead(sock: Int, data: ByteArray): Int
    private external fun nativeClose(sock: Int)

    companion object {
        init {
            System.loadLibrary("nativesrt")
        }
    }
}