package com.pedro.whip.webrtc

import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import com.pedro.whip.utils.Network
import com.pedro.whip.utils.Requests
import com.pedro.whip.webrtc.SdpBody.createAV1Body
import com.pedro.whip.webrtc.SdpBody.createG711Body
import com.pedro.whip.webrtc.SdpBody.createH264Body
import com.pedro.whip.webrtc.SdpBody.createH265Body
import com.pedro.whip.webrtc.SdpBody.createOpusBody
import com.pedro.whip.webrtc.stun.Attribute
import com.pedro.whip.webrtc.stun.AttributeType
import com.pedro.whip.webrtc.stun.GatheringMode
import com.pedro.whip.webrtc.stun.StunCommand
import com.pedro.whip.webrtc.stun.StunCommandReader
import com.pedro.whip.webrtc.stun.StunHeader
import com.pedro.whip.webrtc.stun.Type
import java.net.InetAddress
import java.nio.ByteBuffer

class CommandsManager {

    var host: String? = null
        private set
    var port = 0
        private set
    var app: String? = null
        private set
    var streamName: String? = null
        private set
    var sps: ByteBuffer? = null
        private set
    var pps: ByteBuffer? = null
        private set
    var vps: ByteBuffer? = null
        private set
    var videoDisabled = false
    var audioDisabled = false
    var sampleRate = 32000
    var isStereo = true
    var videoCodec = VideoCodec.H264
    var audioCodec = AudioCodec.OPUS
    private var stunSeq = 0
    private val timeout = 5000
    private val timeStamp: Long
    val spsString: String
        get() = sps?.getData()?.encodeToString() ?: ""
    val ppsString: String
        get() = pps?.getData()?.encodeToString() ?: ""
    val vpsString: String
        get() = vps?.getData()?.encodeToString() ?: ""

    companion object {
        private const val TAG = "CommandsManager"
    }

    init {
        val uptime = TimeUtils.getCurrentTimeMillis()
        timeStamp = uptime / 1000 shl 32 and ((uptime - uptime / 1000 * 1000 shr 32)
                / 1000) // NTP timestamp
    }

    fun videoInfoReady(): Boolean {
        return when (videoCodec) {
            VideoCodec.H264 -> sps != null && pps != null
            VideoCodec.H265 -> sps != null && pps != null && vps != null
            VideoCodec.AV1 -> sps != null
        }
    }

    fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
        this.sps = sps
        this.pps = pps
        this.vps = vps
    }

    fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
        this.isStereo = isStereo
        this.sampleRate = sampleRate
    }

    fun setUrl(host: String, port: Int, app: String, streamName: String?) {
        this.host = host
        this.port = port
        this.app = app
        this.streamName = streamName
    }

    fun clear() {
        sps = null
        pps = null
        vps = null
    }

    fun writeOptions() {
        val uri = "$host:$port/$app"
        val path: String? = streamName
        val headers = mutableMapOf<String, String>().apply {
            put("Content-Type", "application/sdp")
            if (path != null) put("Authorization", "Bearer $path")
        }
        Requests.makeRequest(
            uri, "OPTIONS", headers, null, timeout, false
        )
    }

    suspend fun gatheringCandidates(socketType: SocketType, gatheringMode: GatheringMode): List<InetAddress> {
        val addresses = Network.getNetworks()
        return when (gatheringMode) {
          GatheringMode.LOCAL -> addresses
          GatheringMode.ALL -> filterNetworksWithStun(socketType, addresses)
        }
    }

    private suspend fun filterNetworksWithStun(
        socketType: SocketType,
        networks: List<InetAddress>
    ): List<InetAddress> {
        val candidates = mutableListOf<InetAddress>()
        val googleStunHost = "stun:stun4.l.google.com"
        val googleStunPort = 19302
        var port = 5000
        networks.forEach {
            val candidate = StreamSocket.createUdpSocket(
                type = socketType,
                host = googleStunHost,
                port = googleStunPort,
                receiveSize = RtpConstants.MTU,
                sourceHost = it.hostAddress,
                sourcePort = port++
            )
            candidate.connect()
            val candidateStunCommand = StunCommand(
                header = StunHeader(Type.REQUEST, stunSeq++.toBigInteger()),
                attributes = listOf(
                    Attribute(AttributeType.USERNAME, byteArrayOf()),
                    Attribute(AttributeType.ICE_CONTROLLED, byteArrayOf()),
                    Attribute(AttributeType.MESSAGE_INTEGRITY, byteArrayOf()),
                    Attribute(AttributeType.FINGERPRINT, byteArrayOf()),
                )
            )
            writeStun(candidateStunCommand, candidate)
            val result = readStun(candidate)
            val isSuccess = false
            if (isSuccess) candidates.add(it)
            candidate.close()
        }
        return candidates
    }

    suspend fun writeStun(stunCommand: StunCommand, socket: UdpStreamSocket) {
        socket.write(stunCommand.toByteArray())
    }

    suspend fun readStun(socket: UdpStreamSocket): StunCommand {
        val data = socket.read()
        return StunCommandReader.readPacket(data)
    }

    private fun createBody(port: Int): String {
        var videoBody = ""
        if (!videoDisabled) {
            videoBody = when (videoCodec) {
                VideoCodec.H264 -> {
                    createH264Body(RtpConstants.trackVideo, spsString, ppsString, port)
                }
                VideoCodec.H265 -> {
                    createH265Body(RtpConstants.trackVideo, spsString, ppsString, vpsString, port)
                }
                VideoCodec.AV1 -> {
                    createAV1Body(RtpConstants.trackVideo, port)
                }
            }
        }
        var audioBody = ""
        if (!audioDisabled) {
            audioBody = when (audioCodec) {
                AudioCodec.G711 -> createG711Body(RtpConstants.trackAudio, sampleRate, isStereo, port)
                AudioCodec.OPUS -> createOpusBody(RtpConstants.trackAudio, port)
                else  -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
            }
        }
        return "v=0\r\n" +
                "o=- $timeStamp $timeStamp IN IP4 127.0.0.1\r\n" +
                "s=Unnamed\r\n" +
                "i=N/A\r\n" +
                "c=IN IP4 $host\r\n" +
                "t=0 0\r\n" +
                "a=group:BUNDLE 0 1\r\n" +
                "a=ice-options:trickle ice2\r\n" +
                "a=ice-ufrag:EsAw\r\n" +
                "a=ice-pwd:bP+XJMM09aR8AiX1jdukzR6Y\r\n" +
                "a=fingerprint:sha-256 DA:7B:57:DC:28:CE:04:4F:31:79:85:C4:31:67:EB:27:58:29:ED:77:2A:0D:24:AE:ED:AD:30:BC:BD:F1:9C:02\r\n" +
                "a=setup:actpass\r\n" +

                videoBody + audioBody
    }
}