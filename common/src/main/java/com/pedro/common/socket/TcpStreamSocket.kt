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

package com.pedro.common.socket

import android.util.Log
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.nio.NioEventLoopGroup
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.nio.charset.Charset
import java.util.concurrent.Semaphore

/**
 * Created by pedro on 22/9/24.
 */
abstract class TcpStreamSocket: StreamSocket {

  protected var group: NioEventLoopGroup? = null
  protected var channel: ChannelFuture? = null
  protected var context: ChannelHandlerContext? = null
  protected var buffer = ByteArrayOutputStream()
  protected val lock = Any()
  protected val semaphore = Semaphore(0)

  suspend fun flush() {
    context?.flush()
  }

  suspend fun write(b: Int) {
    suspendCancellableCoroutine {
      context?.writeAndFlush(Unpooled.wrappedBuffer(byteArrayOf(b.toByte())))?.addListener(object:
        ChannelFutureListener {
        override fun operationComplete(future: ChannelFuture?) {
          it.resumeWith(Result.success(Any()))
        }
      })
    }
  }

  suspend fun write(b: ByteArray) {
    suspendCancellableCoroutine {
      context?.writeAndFlush(Unpooled.wrappedBuffer(b))?.addListener(object:
        ChannelFutureListener {
        override fun operationComplete(future: ChannelFuture?) {
          it.resumeWith(Result.success(Any()))
        }
      })
    }
  }

  suspend fun write(b: ByteArray, offset: Int, size: Int) {
    suspendCancellableCoroutine {
      context?.writeAndFlush(Unpooled.wrappedBuffer(b, offset, size))?.addListener(object:
        ChannelFutureListener {
        override fun operationComplete(future: ChannelFuture?) {
          it.resumeWith(Result.success(Any()))
        }
      })
    }
  }

  suspend fun writeUInt16(b: Int) {
    write(byteArrayOf((b ushr 8).toByte(), b.toByte()))
  }

  suspend fun writeUInt24(b: Int) {
    write(byteArrayOf((b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
  }

  suspend fun writeUInt32(b: Int) {
    write(byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
  }

  suspend fun writeUInt32LittleEndian(b: Int) {
    writeUInt32(Integer.reverseBytes(b))
  }

  suspend fun write(string: String) {
    context?.write(string)
  }

  suspend fun read(): Int {
    val input = buffer
    while (input.size() < 1) semaphore.acquireUninterruptibly()
    synchronized(lock) {
      val i = ByteArrayInputStream(input.toByteArray())
      val r = i.read()
      buffer.close()
      buffer = ByteArrayOutputStream()
      buffer.write(i.readBytes())
      i.close()
      return r
    }
  }

  suspend fun readUInt16(): Int {
    val b = ByteArray(2)
    readUntil(b)
    return b[0].toInt() and 0xff shl 8 or (b[1].toInt() and 0xff)
  }

  suspend fun readUInt24(): Int {
    val b = ByteArray(3)
    readUntil(b)
    return b[0].toInt() and 0xff shl 16 or (b[1].toInt() and 0xff shl 8) or (b[2].toInt() and 0xff)
  }

  suspend fun readUInt32(): Int {
    val b = ByteArray(4)
    readUntil(b)
    return b[0].toInt() and 0xff shl 24 or (b[1].toInt() and 0xff shl 16) or (b[2].toInt() and 0xff shl 8) or (b[3].toInt() and 0xff)
  }

  suspend fun readUInt32LittleEndian(): Int {
    return Integer.reverseBytes(readUInt32())
  }

  suspend fun readUntil(b: ByteArray) {
    val input = buffer
    while (input.size() < 1) semaphore.acquireUninterruptibly()
    synchronized(lock) {
      val i = ByteArrayInputStream(input.toByteArray())
      i.read(b)
      buffer.close()
      buffer = ByteArrayOutputStream()
      buffer.write(i.readBytes())
      i.close()
    }
  }

  suspend fun readLine(): String {
    synchronized(lock) {
      val input = buffer ?: throw ConnectException("Read with socket closed, broken pipe")
      return ""
    }
  }
}