package com.pedro.common

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KtorSocketPerformance {

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        private lateinit var serverSocket: ServerSocket

        @BeforeClass
        @JvmStatic
        fun setup() {
            serverSocket = ServerSocket(4200)
            executor.submit {
                val buffer = ByteArray(4096)
                while (!Thread.interrupted()) {
                    serverSocket.accept().use { socket ->
                        socket.getInputStream().use { stream ->
//                            while (true) {
//                                val read = stream.read(buffer)
//                                if (read == -1) {
//                                    socket.close()
//                                    return@use
//                                }
//                            }
                        }
                    }
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            serverSocket.close()
            executor.shutdown()
        }
    }

    @Test
    fun testKtorSocketWrite() = runTest {
        val t = System.currentTimeMillis()
        val buffer = ByteArray(4088)
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        aSocket(selectorManager).tcp().connect("localhost", 4200).use { socket ->
            val channel = socket.openWriteChannel(autoFlush = false)
            for (i in 1..32768) { //~100M
                channel.writeFully(buffer, 0, buffer.size)
                channel.flush()
            }
            channel.close()
        }
        selectorManager.close()
        executor.shutdown()
        println("time ktor: ${System.currentTimeMillis() - t}")
    }

    @Test
    fun testJvmSocketWrite() {
        val t = System.currentTimeMillis()
        Socket("localhost", 4200).use {
            val buffer = ByteArray(4088)
            it.getOutputStream().use { stream ->
                for (i in 1..32768) { //~100M
                    stream.write(buffer, 0, buffer.size)
                    stream.flush()
                }
            }
        }
        println("time jvm: ${System.currentTimeMillis() - t}")
    }

    @Test
    fun testJvmExecuteSocketWrite() = runTest {
        val executor = Executors.newSingleThreadExecutor()
        val t = System.currentTimeMillis()
        Socket("localhost", 4200).use {
            val buffer = ByteArray(4088)
            it.getOutputStream().use { stream ->
                for (i in 1..32768) { //~100M
                    suspendCancellableCoroutine<Any> { con ->
                        executor.execute {
                            stream.write(buffer, 0, buffer.size)
                            stream.flush()
                            con.resumeWith(Result.success(Any()))
                        }
                    }
                }
            }
        }
        println("time jvm executors: ${System.currentTimeMillis() - t}")
    }

}