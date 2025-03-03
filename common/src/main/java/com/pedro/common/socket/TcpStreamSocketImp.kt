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

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.net.ssl.TrustManager

/**
 * Created by pedro on 22/9/24.
 */
class TcpStreamSocketImp(
  private val host: String,
  private val port: Int,
  private val secured: Boolean = false,
  private val certificate: TrustManager? = null
): TcpStreamSocket() {

  override suspend fun connect() = withContext(Dispatchers.IO) {
    buffer.close()
    buffer = ByteArrayOutputStream()
    group = NioEventLoopGroup()
    val sslContext: SslContext = SslContextBuilder
      .forClient()
      .build()

    val bootstrap = Bootstrap().group(group).channel(NioSocketChannel::class.java)
      .handler(object: ChannelInitializer<Channel>() {
        override fun initChannel(ch: Channel?) {
          val pipeline = ch?.pipeline()
          if (secured) {
            pipeline?.addLast(sslContext.newHandler(ch.alloc(), host, port))
          }
          pipeline?.addLast(object : SimpleChannelInboundHandler<ByteBuf>() {
            override fun channelActive(ctx: ChannelHandlerContext) {
              context = ctx
            }

            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
              synchronized(lock) {
                val bytes = ByteArray(msg.readableBytes())
                msg.readBytes(bytes)
                buffer.write(bytes)
              }
              semaphore.release()
            }
          })
        }
      })
    channel = bootstrap.connect(host, port).sync()
    return@withContext
  }

  override suspend fun close() = withContext(Dispatchers.IO) {
    try {
      channel?.channel()?.closeFuture()?.sync()
      channel = null
    } finally {
      group?.shutdownGracefully()
      group = null
    }
    return@withContext
  }

  override fun isConnected(): Boolean = channel?.channel()?.isActive ?: false

  override fun isReachable(): Boolean = channel?.channel()?.isActive ?: false
}