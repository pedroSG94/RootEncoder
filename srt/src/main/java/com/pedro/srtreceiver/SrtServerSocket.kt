package com.pedro.srtreceiver

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class SrtServerSocket {
    
    companion object {
        private const val TAG = "SrtServerSocket"
        private const val BUFFER_SIZE = 1316 // 7 TS packets (188 * 7)
        
        init {
            try {
                System.loadLibrary("nativesrt")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    private var serverFd: Int = -1
    private var clientFd: Int = -1
    private val isRunning = AtomicBoolean(false)
    private var receiveThread: Thread? = null
    
    @Volatile
    var onDataReceived: ((ByteArray, Int) -> Unit)? = null
    
    @Volatile
    var onClientConnected: (() -> Unit)? = null
    
    @Volatile
    var onClientDisconnected: (() -> Unit)? = null
    
    private external fun nativeInit(): Int
    private external fun nativeStartServer(port: Int): Int
    private external fun nativeAccept(serverFd: Int): Int
    private external fun nativeRecv(socketFd: Int, buffer: ByteArray): Int
    private external fun nativeClose(socketFd: Int)
    
    fun start(port: Int): Boolean {
        if (isRunning.get()) {
            Log.w(TAG, "Server already running")
            return false
        }
        
        // Initialize SRT
        if (nativeInit() < 0) {
            Log.e(TAG, "Failed to initialize SRT")
            return false
        }
        
        // Start server
        serverFd = nativeStartServer(port)
        if (serverFd < 0) {
            Log.e(TAG, "Failed to start server on port $port")
            return false
        }
        
        isRunning.set(true)
        Log.i(TAG, "SRT server started on port $port")
        
        // Start accept thread
        receiveThread = Thread {
            acceptLoop()
        }.apply {
            name = "SRT-Accept-Thread"
            start()
        }
        
        return true
    }
    
    private fun acceptLoop() {
        while (isRunning.get()) {
            try {
                Log.i(TAG, "Waiting for client connection...")
                clientFd = nativeAccept(serverFd)
                
                if (clientFd < 0) {
                    Log.e(TAG, "Failed to accept client")
                    Thread.sleep(1000)
                    continue
                }
                
                Log.i(TAG, "Client connected, fd=$clientFd")
                onClientConnected?.invoke()
                
                // Start receive loop
                receiveLoop()
                
                // Client disconnected
                Log.i(TAG, "Client disconnected")
                onClientDisconnected?.invoke()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in accept loop", e)
            }
        }
    }
    
    private fun receiveLoop() {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesReceived = 0L
        var lastLogTime = System.currentTimeMillis()
        
        while (isRunning.get() && clientFd >= 0) {
            try {
                val received = nativeRecv(clientFd, buffer)
                
                if (received < 0) {
                    // Connection closed or error
                    break
                }
                
                if (received > 0) {
                    bytesReceived += received
                    onDataReceived?.invoke(buffer, received)
                    
                    // Log bitrate every 5 seconds
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime >= 5000) {
                        val bitrate = (bytesReceived * 8) / 5 / 1000 // kbps
                        Log.i(TAG, "Bitrate: $bitrate kbps")
                        bytesReceived = 0
                        lastLogTime = currentTime
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving data", e)
                break
            }
        }
        
        // Close client socket
        if (clientFd >= 0) {
            nativeClose(clientFd)
            clientFd = -1
        }
    }
    
    fun stop() {
        if (!isRunning.get()) {
            return
        }
        
        Log.i(TAG, "Stopping SRT server...")
        isRunning.set(false)
        
        // Close client socket
        if (clientFd >= 0) {
            nativeClose(clientFd)
            clientFd = -1
        }
        
        // Close server socket
        if (serverFd >= 0) {
            nativeClose(serverFd)
            serverFd = -1
        }
        
        // Wait for thread to finish
        receiveThread?.join(2000)
        receiveThread = null
        
        Log.i(TAG, "SRT server stopped")
    }
}
