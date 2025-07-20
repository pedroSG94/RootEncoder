package com.pedro.whip

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.UrlParser
import com.pedro.common.VideoCodec
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.toMediaFrameInfo
import com.pedro.common.toUInt32
import com.pedro.common.toUInt64
import com.pedro.common.validMessage
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.whip.webrtc.Candidate
import com.pedro.whip.webrtc.CommandsManager
import com.pedro.whip.webrtc.stun.AttributeType
import com.pedro.whip.webrtc.stun.GatheringMode
import com.pedro.whip.webrtc.stun.StunAttribute
import com.pedro.whip.webrtc.stun.StunAttributeValueParser
import com.pedro.whip.webrtc.stun.StunCommandReader
import com.pedro.whip.webrtc.stun.HeaderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.security.SecureRandom

class WhipClient(private val connectChecker: ConnectChecker) {

    private val TAG = "WhipClient"

    private val validSchemes = arrayOf("http")

    private var scope = CoroutineScope(Dispatchers.IO)
    private var scopeRetry = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var jobRetry: Job? = null
    private var mutex = Mutex(locked = true)

    @Volatile
    var isStreaming = false
        private set

    //for secure transport
    private var tlsEnabled = false
    private val commandsManager: CommandsManager = CommandsManager()
    private val whipSender: WhipSender = WhipSender(connectChecker, commandsManager)
    private var url: String? = null
    private var doingRetry = false
    private var numRetry = 0
    private var reTries = 0
    private var publishPermitted = false
    private var checkServerAlive = false
    var socketType = SocketType.KTOR

    val droppedAudioFrames: Long
        get() = whipSender.droppedAudioFrames
    val droppedVideoFrames: Long
        get() = whipSender.droppedVideoFrames

    val cacheSize: Int
        get() = whipSender.getCacheSize()
    val sentAudioFrames: Long
        get() = whipSender.getSentAudioFrames()
    val sentVideoFrames: Long
        get() = whipSender.getSentVideoFrames()

    fun setDelay(millis: Long) {
        whipSender.setDelay(millis)
    }

    fun setAuthorization(user: String?, password: String?) {
        TODO("unimplemented")
    }

    /**
     * Check periodically if server is alive using Echo protocol.
     */
    fun setCheckServerAlive(enabled: Boolean) {
        checkServerAlive = enabled
    }

    /**
     * Must be called before connect
     */
    fun setOnlyAudio(onlyAudio: Boolean) {
        if (onlyAudio) {
            RtpConstants.trackAudio = 0
            RtpConstants.trackVideo = 1
        } else {
            RtpConstants.trackVideo = 0
            RtpConstants.trackAudio = 1
        }
        commandsManager.audioDisabled = false
        commandsManager.videoDisabled = onlyAudio
    }

    /**
     * Must be called before connect
     */
    fun setOnlyVideo(onlyVideo: Boolean) {
        RtpConstants.trackVideo = 0
        RtpConstants.trackAudio = 1
        commandsManager.videoDisabled = false
        commandsManager.audioDisabled = onlyVideo
    }

    fun setReTries(reTries: Int) {
        numRetry = reTries
        this.reTries = reTries
    }

    fun shouldRetry(reason: String): Boolean {
        val validReason = doingRetry && !reason.contains("Endpoint malformed")
        return validReason && reTries > 0
    }

    fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
        Log.i(TAG, "send sps and pps")
        commandsManager.setVideoInfo(sps, pps, vps)
        if (mutex.isLocked) runCatching { mutex.unlock() }
    }

    fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
        commandsManager.setAudioInfo(sampleRate, isStereo)
    }

    fun setVideoCodec(videoCodec: VideoCodec) {
        if (!isStreaming) {
            commandsManager.videoCodec = videoCodec
        }
    }

    fun setAudioCodec(audioCodec: AudioCodec) {
        if (!isStreaming) {
            commandsManager.audioCodec = when (audioCodec) {
                AudioCodec.AAC -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
                else -> audioCodec
            }
        }
    }

    fun connect(url: String?) {
        connect(url, false)
    }

    fun connect(url: String?, isRetry: Boolean) {
        if (!isRetry) doingRetry = true
        if (!isStreaming || isRetry) {
            isStreaming = true

            job = scope.launch {
                if (url == null) {
                    isStreaming = false
                    onMainThread {
                        connectChecker.onConnectionFailed("Endpoint malformed, should be: http://ip:port/appname/streamname")
                    }
                    return@launch
                }
                this@WhipClient.url = url
                onMainThread { connectChecker.onConnectionStarted(url) }

                val urlParser = try {
                    UrlParser.parse(url, validSchemes)
                } catch (_: URISyntaxException) {
                    isStreaming = false
                    onMainThread {
                        connectChecker.onConnectionFailed("Endpoint malformed, should be: http://ip:port/appname/streamname")
                    }
                    return@launch
                }

                tlsEnabled = urlParser.scheme.endsWith("s")
                val host = urlParser.host
                val port = urlParser.port ?: if (tlsEnabled) 443 else 8889
                val app = urlParser.getAppName()
                val streamName = urlParser.getStreamName()
                if (app.isEmpty()) {
                    isStreaming = false
                    onMainThread {
                        connectChecker.onConnectionFailed("Endpoint malformed, should be: http://ip:port/appname/streamname")
                    }
                    return@launch
                }

                val error = runCatching {
                    commandsManager.setUrl(host, port, app, streamName)
                    if (!commandsManager.audioDisabled) {
                        whipSender.setAudioInfo(commandsManager.sampleRate, commandsManager.isStereo)
                    }
                    if (!commandsManager.videoDisabled) {
                        if (!commandsManager.videoInfoReady()) {
                            Log.i(TAG, "waiting for sps and pps")
                            withTimeoutOrNull(5000) {
                                mutex.lock()
                            }
                            if (!commandsManager.videoInfoReady()) {
                                onMainThread {
                                    connectChecker.onConnectionFailed("sps or pps is null")
                                }
                                return@launch
                            }
                        }
                        whipSender.setVideoInfo(commandsManager.sps!!, commandsManager.pps, commandsManager.vps)
                    }

                    commandsManager.writeOptions()
                    val candidates = commandsManager.gatheringCandidates(socketType, GatheringMode.ALL)
                    Log.i(TAG, "found ${candidates.size} candidates")
                    candidates.forEach { Log.i(TAG, "candidate: $it") }
                    val sockets = candidates.map {
                        StreamSocket.createUdpSocket(socketType, it.localAddress, it.localPort, receiveSize = RtpConstants.MTU).apply {
                            bind()
                        }
                    }
                    sockets.forEach {
                        async(Dispatchers.IO) {
                            while (!publishPermitted) {
                                //Handle all command received and send response for it.
                                handleMessages(it)
                            }
                        }
                    }

                    val offerResponse = commandsManager.writeOffer(candidates)
                    Log.i(TAG, offerResponse.body)

                    val tieBreak = SecureRandom().nextLong().toUInt64()
                    sockets.forEachIndexed { index, socket ->
                        commandsManager.remoteSdpInfo?.candidates?.filter { candidate ->
                            candidate.protocol == 1 //only rtp
                        }?.forEach { candidate ->
                            async(Dispatchers.IO) {
                                Log.e(TAG, "candidate: $candidate")
                                sendBindingRequestToCandidate(candidates[index], candidate, tieBreak, socket)
                            }
                        }
                    }
                    //TODO DTLS handshake

                    //TODO connection success ready to send SRTP/SRTCP

                }.exceptionOrNull()
                if (error != null) {
                    Log.e(TAG, "connection error", error)
                    onMainThread {
                        connectChecker.onConnectionFailed("Error configure stream, ${error.validMessage()}")
                    }
                    return@launch
                }
            }
        }
    }

    suspend fun handleMessages(socket: UdpStreamSocket) {
        val packet = socket.readPacket()
        val host = packet.host ?: return
        val port = packet.port ?: return
        val command = StunCommandReader.readPacket(packet.data)
        when (command.header.type) {
          HeaderType.REQUEST -> {
              sendSuccess(command.header.id, host, port, socket)
              Log.e(TAG, "send response")
          }
          HeaderType.INDICATION -> throw IllegalArgumentException("unsupported for now")
          HeaderType.SUCCESS -> {
            throw IllegalArgumentException("success received?????")
          }
          HeaderType.ERROR -> throw IllegalArgumentException("unsupported for now")
        }
    }

    suspend fun sendBindingRequestToCandidate(
        localCandidate: Candidate,
        remoteCandidate: Candidate,
        tieBreak: ByteArray,
        socket: UdpStreamSocket
    ) {
        val localFrag = commandsManager.localSdpInfo?.uFrag ?: return
        val remoteFrag = commandsManager.remoteSdpInfo?.uFrag ?: return
        val host = remoteCandidate.publicAddress ?: remoteCandidate.localAddress
        val port = remoteCandidate.publicPort ?: remoteCandidate.localPort
        val timeout = arrayOf(100L, 200L, 400L, 800L, 1500L, 2000)
        for (i in 0..timeout.size) {
            val id = commandsManager.generateTransactionId()
            val userName = StunAttributeValueParser.createUserName(localFrag, remoteFrag)
            val attributes = listOf(
                StunAttribute(AttributeType.PRIORITY, localCandidate.priority.toUInt32()),
                StunAttribute(AttributeType.USERNAME, userName),
                StunAttribute(AttributeType.ICE_CONTROLLING, tieBreak),
            )
            commandsManager.writeStun(HeaderType.REQUEST, id, attributes, socket, host, port)
            Log.i(TAG, "candidate request attempt: $i\nlocalCandidate: $localCandidate\nremoteCandidate: $remoteCandidate")
            delay(timeout[i])
        }
    }

    suspend fun sendSuccess(id: ByteArray, host: String, port: Int, socket: UdpStreamSocket) {
        val localFrag = commandsManager.localSdpInfo?.uFrag ?: return
        val remoteFrag = commandsManager.remoteSdpInfo?.uFrag ?: return
        val userNameValue = StunAttributeValueParser.createUserName(remoteFrag, localFrag)
        val xorAddress = StunAttributeValueParser.createXorMappedAddress(id, host, port, true)
        val attributes = listOf(
            StunAttribute(AttributeType.USERNAME, userNameValue),
            StunAttribute(AttributeType.XOR_MAPPED_ADDRESS, xorAddress)
        )
        commandsManager.writeStun(HeaderType.SUCCESS, id, attributes, socket, host, port)
    }

    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            disconnect(true)
        }
    }

    private suspend fun disconnect(clear: Boolean) {
        if (isStreaming) whipSender.stop()
        val error = runCatching {
            //TODO write delete command
            Log.i(TAG, "write delete success")
        }.exceptionOrNull()
        if (error != null) {
            Log.e(TAG, "disconnect error", error)
        }
        if (clear) {
            commandsManager.clear()
            reTries = numRetry
            doingRetry = false
            isStreaming = false
            onMainThread {
                connectChecker.onDisconnect()
            }
            mutex = Mutex(true)
            jobRetry?.cancelAndJoin()
            jobRetry = null
            scopeRetry.cancel()
            scopeRetry = CoroutineScope(Dispatchers.IO)
        } else {
            commandsManager.clear()
        }
        job?.cancelAndJoin()
        job = null
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
        publishPermitted = false
    }

    fun sendVideo(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!commandsManager.videoDisabled) {
            whipSender.sendMediaFrame(MediaFrame(videoBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.VIDEO))
        }
    }

    fun sendAudio(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!commandsManager.audioDisabled) {
            whipSender.sendMediaFrame(MediaFrame(audioBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.AUDIO))
        }
    }

    @JvmOverloads
    @Throws(IllegalArgumentException::class)
    fun hasCongestion(percentUsed: Float = 20f): Boolean {
        return whipSender.hasCongestion(percentUsed)
    }

    @JvmOverloads
    fun reConnect(delay: Long, backupUrl: String? = null) {
        jobRetry = scopeRetry.launch {
            reTries--
            disconnect(false)
            delay(delay)
            val reconnectUrl = backupUrl ?: url
            connect(reconnectUrl, true)
        }
    }

    fun resetSentAudioFrames() {
        whipSender.resetSentAudioFrames()
    }

    fun resetSentVideoFrames() {
        whipSender.resetSentVideoFrames()
    }

    fun resetDroppedAudioFrames() {
        whipSender.resetDroppedAudioFrames()
    }

    fun resetDroppedVideoFrames() {
        whipSender.resetDroppedVideoFrames()
    }

    @Throws(RuntimeException::class)
    fun resizeCache(newSize: Int) {
        whipSender.resizeCache(newSize)
    }

    fun setLogs(enable: Boolean) {
        whipSender.setLogs(enable)
    }

    fun clearCache() {
        whipSender.clearCache()
    }

    fun getItemsInCache(): Int = whipSender.getItemsInCache()

    /**
     * @param factor values from 0.1f to 1f
     * Set an exponential factor to the bitrate calculation to avoid bitrate spikes
     */
    fun setBitrateExponentialFactor(factor: Float) {
        whipSender.setBitrateExponentialFactor(factor)
    }

    /**
     * Get the exponential factor used to calculate the bitrate. Default 1f
     */
    fun getBitrateExponentialFactor() = whipSender.getBitrateExponentialFactor()
}