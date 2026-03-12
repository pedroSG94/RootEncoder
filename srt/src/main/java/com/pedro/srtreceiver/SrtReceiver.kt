package com.pedro.srtreceiver

import android.util.Log
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean

class SrtReceiver(private val surfaceView: SurfaceView) {
    
    companion object {
        private const val TAG = "SrtReceiver"
    }
    
    private val srtServerSocket = SrtServerSocket()
    private val dataQueue = BlockingByteQueue(maxSize = 200)
    private val tsDemuxer = TsDemuxer()
    private val h264Parser = H264Parser()
    private val aacParser = AacParser()
    
    private var videoDecoder: VideoDecoder? = null
    private var audioDecoder: AudioDecoder? = null
    
    private val isRunning = AtomicBoolean(false)
    private var demuxThread: Thread? = null
    
    fun start(port: Int) {
        if (isRunning.get()) {
            Log.w(TAG, "SrtReceiver already running")
            return
        }
        
        Log.i(TAG, "Starting SRT receiver on port $port")
        isRunning.set(true)
        
        // Initialize decoders
        videoDecoder = VideoDecoder(surfaceView.holder.surface)
        audioDecoder = AudioDecoder()
        
        // Setup SRT callbacks
        srtServerSocket.onClientConnected = {
            Log.i(TAG, "Client connected")
        }
        
        srtServerSocket.onClientDisconnected = {
            Log.i(TAG, "Client disconnected, resetting pipeline")
            resetPipeline()
        }
        
        srtServerSocket.onDataReceived = { data, size ->
            // Copy data to avoid race conditions
            val dataCopy = ByteArray(size)
            System.arraycopy(data, 0, dataCopy, 0, size)
            
            // Queue data for demuxing
            if (!dataQueue.offer(dataCopy)) {
                Log.w(TAG, "Data queue full, dropping packet")
            }
        }
        
        // Setup demuxer callbacks
        tsDemuxer.onVideoPes = { pesData, pts ->
            h264Parser.parse(pesData, pts)
        }
        
        tsDemuxer.onAudioPes = { pesData, pts ->
            aacParser.parse(pesData, pts)
        }
        
        // Setup H264 parser callbacks
        h264Parser.onConfigReady = { spsList, ppsList ->
            Log.i(TAG, "H264 config ready (SPS/PPS)")
            videoDecoder?.configure(spsList, ppsList)
        }
        
        h264Parser.onNalUnit = { nalUnit, nalType, pts ->
            if (h264Parser.hasSpsAndPps()) {
                videoDecoder?.decode(nalUnit, pts)
            }
        }
        
        // Setup AAC parser callbacks
        aacParser.onAacFrame = { frame ->
            if (audioDecoder?.isReady() != true) {
                audioDecoder?.configure(frame.sampleRate, frame.channelConfig)
            }
            audioDecoder?.decode(frame.data, frame.pts)
        }
        
        // Start SRT server
        if (!srtServerSocket.start(port)) {
            Log.e(TAG, "Failed to start SRT server")
            isRunning.set(false)
            return
        }
        
        // Start demux thread
        demuxThread = Thread {
            demuxLoop()
        }.apply {
            name = "SRT-Demux-Thread"
            start()
        }
        
        Log.i(TAG, "SRT receiver started successfully")
    }
    
    private fun demuxLoop() {
        while (isRunning.get()) {
            try {
                val data = dataQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (data != null) {
                    tsDemuxer.process(data, data.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in demux loop", e)
            }
        }
    }
    
    private fun resetPipeline() {
        // Clear queues
        dataQueue.clear()
        
        // Don't stop decoders, they can be reused for the next connection
        // Just clear any buffered state if needed
    }
    
    fun stop() {
        if (!isRunning.get()) {
            return
        }
        
        Log.i(TAG, "Stopping SRT receiver...")
        isRunning.set(false)
        
        // Stop SRT server
        srtServerSocket.stop()
        
        // Stop demux thread
        demuxThread?.join(2000)
        demuxThread = null
        
        // Stop decoders
        videoDecoder?.stop()
        audioDecoder?.stop()
        
        videoDecoder = null
        audioDecoder = null
        
        // Clear queue
        dataQueue.clear()
        
        Log.i(TAG, "SRT receiver stopped")
    }
}
