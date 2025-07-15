package com.pedro.whip.webrtc

import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.rtsp.commands.SdpBody
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import com.pedro.whip.dtls.CryptoUtils
import com.pedro.whip.utils.Network
import com.pedro.whip.utils.RequestResponse
import com.pedro.whip.utils.Requests
import com.pedro.whip.webrtc.stun.Attribute
import com.pedro.whip.webrtc.stun.AttributeType
import com.pedro.whip.webrtc.stun.Candidate
import com.pedro.whip.webrtc.stun.CandidateType
import com.pedro.whip.webrtc.stun.GatheringMode
import com.pedro.whip.webrtc.stun.StunCommand
import com.pedro.whip.webrtc.stun.StunCommandReader
import com.pedro.whip.webrtc.stun.StunHeader
import com.pedro.whip.webrtc.stun.Type
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.random.Random

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
        val uri = "http://$host:$port/$app"
        val path: String? = streamName
        val headers = mutableMapOf<String, String>().apply {
            put("Content-Type", "application/sdp")
            if (path != null) put("Authorization", "Bearer $path")
        }

        Requests.makeRequest(
            uri, "OPTIONS", headers, null, timeout, false
        )
    }

    suspend fun gatheringCandidates(socketType: SocketType, gatheringMode: GatheringMode): List<Candidate> {
        val addresses = Network.getNetworks()
        var localPort = 5000
        return when (gatheringMode) {
          GatheringMode.LOCAL -> addresses.map {
              val type = CandidateType.LOCAL
              val priority: Long = (type.preference shl 24) or (65535L shl 8) or (256 - 1)
              Candidate(
                  type = type,
                  priority = priority,
                  localAddress = it.hostAddress ?: "",
                  localPort = localPort++,
                  publicAddress = null,
                  publicPort = null
              )
          }
          GatheringMode.ALL -> filterNetworksWithStun(socketType, addresses)
        }
    }
    
    fun writeOffer(candidates: List<Candidate>): RequestResponse {
        val secureRandom = SecureRandom()
        val uFrag = secureRandom.nextLong().toString(36).replace("-", "")
        val uPass = (BigInteger(130, secureRandom).toString(32)).replace("-", "")
        val certificate = CryptoUtils.generateCert("RootEncoder", secureRandom)
        val videoSsrc = Random.nextLong()
        val audioSsrc = Random.nextLong()
        val body = createBody(
            videoSsrc, audioSsrc, candidates, uFrag, uPass, certificate.fingerprint
        )
        val uri = "http://$host:$port/$app"
        val path: String? = streamName
        val headers = mutableMapOf<String, String>().apply {
            put("Content-Type", "application/sdp")
            if (path != null) put("Authorization", "Bearer $path")
        }
        val answer = Requests.makeRequest(
            uri, "POST", headers, body, timeout, false
        )
        return answer
    }

    private suspend fun filterNetworksWithStun(
        socketType: SocketType,
        networks: List<InetAddress>
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
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
            if (isSuccess) //candidates.add(it)
            candidate.close()
        }
        return candidates
    }

    suspend fun writeStun(stunCommand: StunCommand, socket: UdpStreamSocket) {
        socket.write(stunCommand.toByteArray())
    }

    suspend fun readStun(socket: UdpStreamSocket): StunCommand {
        val data = socket.read()
        return readStun(data)
    }

    suspend fun readStun(data: ByteArray): StunCommand {
        return StunCommandReader.readPacket(data)
    }

    private fun createBody(
        videoSsrc: Long, audioSsrc: Long, candidates: List<Candidate>,
        uFrag: String, uPass: String, fingerprint: String
    ): String {
        val cName = "RootEncoder"
        var videoBody = ""
        if (!videoDisabled) {
            videoBody = when (videoCodec) {
                VideoCodec.H264 -> {
                    SdpBody.createH264Body(RtpConstants.trackVideo, spsString, ppsString, true)
                }
                VideoCodec.H265 -> {
                    SdpBody.createH265Body(RtpConstants.trackVideo, spsString, ppsString, vpsString, true)
                }
                VideoCodec.AV1 -> {
                    SdpBody.createAV1Body(RtpConstants.trackVideo, true)
                }
            }
        }
        var audioBody = ""
        if (!audioDisabled) {
            audioBody = when (audioCodec) {
                AudioCodec.G711 -> SdpBody.createG711Body(RtpConstants.trackAudio, sampleRate, isStereo, true)
                AudioCodec.OPUS -> SdpBody.createOpusBody(RtpConstants.trackAudio, true)
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
                "a=ice-ufrag:$uFrag\r\n" +
                "a=ice-pwd:$uPass\r\n" +
                "a=fingerprint:sha-256 $fingerprint\r\n" +
                videoBody +
                "a=ssrc:" + videoSsrc + " cname: $cName\r\n" +
                audioBody +
                "a=ssrc:" + audioSsrc + " cname: $cName\r\n" +
                addCandidates(candidates)
    }

    private fun addCandidates(candidates: List<Candidate>): String {
        var sdpCandidates = ""
        candidates.forEachIndexed { index, candidate ->
            val address = candidate.publicAddress ?: candidate.localAddress
            val port = candidate.publicPort ?: candidate.localPort
            sdpCandidates += "a=candidate:$index 1 UDP ${candidate.priority} $address $port typ ${candidate.type.value} ${
                if (candidate.type == CandidateType.SRFLX) {
                    "raddr ${candidate.localAddress} rport ${candidate.localPort}"
                } else ""
            }\r\n"
        }
        sdpCandidates += "a=end-of-candidates\r\n"
        return sdpCandidates
    }
}