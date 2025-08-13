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
import com.pedro.common.validMessage
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.whip.dtls.DtlsConnection
import com.pedro.whip.dtls.DtlsTransport
import com.pedro.whip.dtls.test.DTLS
import com.pedro.whip.webrtc.Candidate
import com.pedro.whip.webrtc.CandidateType
import com.pedro.whip.webrtc.CommandsManager
import com.pedro.whip.webrtc.stun.AttributeType
import com.pedro.whip.webrtc.stun.CandidatePair
import com.pedro.whip.webrtc.stun.CandidatePairConnection
import com.pedro.whip.webrtc.stun.GatheringMode
import com.pedro.whip.webrtc.stun.HeaderType
import com.pedro.whip.webrtc.stun.StunAttribute
import com.pedro.whip.webrtc.stun.StunAttributeValueParser
import com.pedro.whip.webrtc.stun.StunCommandReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import java.net.URISyntaxException
import java.nio.ByteBuffer

class WhipClient(private val connectChecker: ConnectChecker) {

    private val TAG = "WhipClient"

    private val validSchemes = arrayOf("http")

    private var scope = CoroutineScope(Dispatchers.IO)
    private var scopeRetry = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var jobRetry: Job? = null
    private var mutex = Mutex(locked = true)
    private var mutexSuccessConnection = Mutex(locked = true)

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

                    val localCandidates = commandsManager.gatheringCandidates(socketType, GatheringMode.LOCAL)
                    Log.i(TAG, "found ${localCandidates.size} candidates")
                    val offerResponse = commandsManager.writeOffer()
                    Log.i(TAG, offerResponse.body)


                    val localFrag = commandsManager.localSdpInfo?.uFrag ?: return@launch
                    val remoteFrag = commandsManager.remoteSdpInfo?.uFrag ?: return@launch
                    val priority = commandsManager.calculatePriority(CandidateType.LOCAL, 65535L, 1)

                    val remoteCandidates = commandsManager.remoteSdpInfo?.candidates?: return@launch
                    val host = remoteCandidates[0].getRealHost()
                    val port = remoteCandidates[0].getRealPort()
                    val socket = StreamSocket.createUdpSocket(socketType, host, port, receiveSize = RtpConstants.MTU)
                    socket.connect()

                    val requestId = commandsManager.generateTransactionId()
                    val userName = StunAttributeValueParser.createUserName(localFrag, remoteFrag)
                    val attributes = listOf(
                        StunAttribute(AttributeType.USERNAME, userName),
                        StunAttribute(AttributeType.ICE_CONTROLLING, commandsManager.tieBreak),
                        StunAttribute(AttributeType.PRIORITY, priority.toUInt32()),
                    )
                    commandsManager.writeStun(HeaderType.REQUEST, requestId, attributes, socket)

                    var candidateResponses = 0
                    var requestSuccessReceived = false
                    while (candidateResponses < 1 || !requestSuccessReceived) {
                        val command = commandsManager.readStun(socket)
                        if (command.header.id.contentEquals(requestId) && command.header.type == HeaderType.SUCCESS) {
                            requestSuccessReceived = true
                        } else if (command.header.type == HeaderType.REQUEST) {
                            candidateResponses++
                            val xorAddress = StunAttributeValueParser.createXorMappedAddress(command.header.id, host, port, true)
                            val attributes = listOf(
                                StunAttribute(AttributeType.XOR_MAPPED_ADDRESS, xorAddress)
                            )
                            commandsManager.writeStun(HeaderType.SUCCESS, command.header.id, attributes, socket)
                        }
                    }

                    val nominateId = commandsManager.generateTransactionId()
                    val nominateAttributes = listOf(
                        StunAttribute(AttributeType.PRIORITY, priority.toUInt32()),
                        StunAttribute(AttributeType.USERNAME, userName),
                        StunAttribute(AttributeType.ICE_CONTROLLING, commandsManager.tieBreak),
                        StunAttribute(AttributeType.USE_CANDIDATE, byteArrayOf()),
                    )
                    commandsManager.writeStun(HeaderType.REQUEST, nominateId, nominateAttributes, socket)

                    var nominateSuccessReceived = false
                    while (!nominateSuccessReceived) {
                        val command = commandsManager.readStun(socket)
                        if (command.header.id.contentEquals(nominateId) && command.header.type == HeaderType.SUCCESS) {
                            nominateSuccessReceived = true
                        } else if (command.header.type == HeaderType.REQUEST) {
                            candidateResponses++
                            val xorAddress = StunAttributeValueParser.createXorMappedAddress(command.header.id, host, port, true)
                            val attributes = listOf(
                                StunAttribute(AttributeType.XOR_MAPPED_ADDRESS, xorAddress)
                            )
                            commandsManager.writeStun(HeaderType.SUCCESS, command.header.id, attributes, socket)
                        }
                    }

                    Log.i(TAG, "stun connected!!")

                    //TODO DTLS handshake
                    val certificate = commandsManager.certificate ?: return@launch
                    val fingerprint = commandsManager.remoteSdpInfo?.fingerprint ?: return@launch
//                    val dtlsConnection = DtlsConnection(certificate)
//                    dtlsConnection.connect(socket)

                    val dtls = DTLS()
                    Log.i(TAG, "connecting dtls...")
                    val dtlsTransport = DtlsTransport(socket)
                    dtls.start(dtlsTransport, fingerprint, certificate.crypto, certificate.key, certificate.certificate)
                    mutexSuccessConnection.lock()
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

    suspend fun handleMessages(
      socket: UdpStreamSocket,
      onStunConnectedReceived: suspend (ByteArray, String, Int) -> Unit = { b, s, i -> },
      onDtlsDataReceived: suspend (ByteArray) -> Unit = {},
      onRtcpDataReceived: suspend (ByteArray) -> Unit = {}
    ) {
        try {
            val packet = socket.readPacket()
            val host = packet.host ?: return
            val port = packet.port ?: return
            val type = packet.data[0]
            when (type) {
                in 20..63 -> {
                    onDtlsDataReceived(packet.data)
                    return
                }
                in 128..191 -> {
                    onRtcpDataReceived(packet.data)
                    return
                }
            }
            onStunConnectedReceived(packet.data, host, port)
        } catch (_: SocketTimeoutException) {}
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