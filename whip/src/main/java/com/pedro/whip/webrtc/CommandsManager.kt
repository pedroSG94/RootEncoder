package com.pedro.whip.webrtc

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.nextBytes
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.rtsp.commands.SdpBody
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.RtpTracks
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import com.pedro.whip.dtls.CryptoUtils
import com.pedro.whip.dtls.DtlsCertificate
import com.pedro.whip.utils.Constants
import com.pedro.whip.utils.Network
import com.pedro.whip.utils.RequestResponse
import com.pedro.whip.utils.Requests
import com.pedro.whip.webrtc.stun.AttributeType
import com.pedro.whip.webrtc.stun.GatheringMode
import com.pedro.whip.webrtc.stun.HeaderType
import com.pedro.whip.webrtc.stun.StunAttribute
import com.pedro.whip.webrtc.stun.StunAttributeValueParser
import com.pedro.whip.webrtc.stun.StunCommand
import com.pedro.whip.webrtc.stun.StunCommandReader
import com.pedro.whip.webrtc.stun.StunHeader
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.math.BigInteger
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
    private val timeout = 5000
    private val timeStamp: Long
    private val secureRandom = SecureRandom()
    val rtpTracks = RtpTracks()
    val crypto = BcTlsCrypto(secureRandom)
    var remoteSdpInfo: SdpInfo? = null
        private set
    var localSdpInfo: SdpInfo? = null
        private set
    var certificate: DtlsCertificate? = null
        private set
    var tieBreak = ByteArray(8)
        private set
    var videoSsrc: Long = 0L
        private set
    var audioSsrc: Long = 0L
        private set
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
        remoteSdpInfo = null
    }

    suspend fun gatheringCandidates(socketType: SocketType, timeout: Long, gatheringMode: GatheringMode): List<Candidate> {
        val googleStunHost = "stun.l.google.com"
        val googleStunPort = 19302
        val startPort = 5000
        val hosts = Network.getNetworks(onlyV4 = true).mapNotNull { it.hostAddress }
        return when (gatheringMode) {
          GatheringMode.LOCAL -> getLocalCandidates(hosts, startPort)
          GatheringMode.ALL -> {
              val localCandidates = getLocalCandidates(hosts, startPort)
              val publicCandidates = getStunCandidates(socketType, googleStunHost, googleStunPort, timeout, hosts, startPort + localCandidates.size)
              localCandidates + publicCandidates
          }
        }
    }

    private fun getLocalCandidates(hosts: List<String>, startPort: Int): List<Candidate> {
        var port = startPort
        return hosts.map {
            val type = CandidateType.LOCAL
            val priority = calculatePriority(type, 65535L, 1)
            Candidate(
                type = type,
                protocol = 1,
                priority = priority,
                localAddress = it,
                localPort = port++,
                publicAddress = null,
                publicPort = null
            )
        }
    }

    fun calculatePriority(type: CandidateType, localPreference: Long, componentId: Long): Int {
        return ((type.preference shl 24) or (localPreference shl 8) or (256 - componentId)).toInt()
    }

    fun writeOffer(): RequestResponse {
        val uFrag = secureRandom.nextLong().toString(36).replace("-", "")
        val uPass = (BigInteger(130, secureRandom).toString(32)).replace("-", "")
        val certificate = CryptoUtils.generateCert("RootEncoder", crypto)
        videoSsrc = Random.nextInt().toLong() and 0xFFFFFFFFL
        audioSsrc = Random.nextInt().toLong() and 0xFFFFFFFFL
        val body = createBody(
            videoSsrc, audioSsrc, uFrag, uPass, certificate.fingerprint
        )
        this.certificate = certificate
        localSdpInfo = SdpInfo(uFrag, uPass, certificate.fingerprint, listOf())
        val uri = "http://$host:$port/$app"
        val path: String? = streamName
        val headers = mutableMapOf<String, String>().apply {
            put("Content-Type", "application/sdp")
            if (path != null) put("Authorization", "Bearer $path")
        }
        val answer = Requests.makeRequest(
            uri, "POST", headers, body, timeout, false
        )
        remoteSdpInfo = SdpParser.parseBodyAnswer(answer.body)
        tieBreak = secureRandom.nextBytes(8)
        Log.i(TAG, "remote info: $remoteSdpInfo")
        return answer
    }

    private suspend fun getStunCandidates(
        socketType: SocketType,
        stunHost: String,
        stunPort: Int,
        timeout: Long,
        hosts: List<String>,
        startPort: Int
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        var port = startPort
        hosts.forEach { host ->
            val candidateSocket = StreamSocket.createUdpSocket(
                type = socketType,
                host = host,
                port = port++,
                timeout = timeout,
                receiveSize = RtpConstants.MTU,
            )
            candidateSocket.bind()
            val command = StunCommand(
                header = StunHeader(HeaderType.REQUEST, 0, Constants.MAGIC_COOKIE, generateTransactionId()),
                attributes = listOf(),
                useIntegrity = false,
                useFingerprint = false
            )
            writeStun(command, candidateSocket)
            val result = readStun(candidateSocket)
            val isSuccess = result.header.type == HeaderType.SUCCESS
            if (isSuccess) {
                val xorMappedAddress = result.attributes.find { it.type == AttributeType.XOR_MAPPED_ADDRESS }?.value!!
                val value = StunAttributeValueParser.readXorMappedAddress(xorMappedAddress, result.header.id)
                val publicHost = value.split(":")[0]
                val publicPort = value.split(":")[1].toInt()
                val type = CandidateType.SRFLX
                val priority = calculatePriority(type, 65535L, 1)
                candidates.add(Candidate(
                    type, 1, priority, host, port, publicHost, publicPort
                ))
            }
            candidateSocket.close()
        }
        return candidates
    }

    suspend fun writeStun(stunCommand: StunCommand, socket: UdpStreamSocket) {
        val remotePass = if (!stunCommand.useIntegrity) "" else remoteSdpInfo?.uPass ?: throw IllegalStateException("remote sdp info no received yet")
        Log.i(TAG, "Write: $stunCommand")
        socket.write(stunCommand.toByteArray(remotePass))
    }

    suspend fun writeStun(type: HeaderType, id: ByteArray, attributes: List<StunAttribute>, socket: UdpStreamSocket) {
        val remotePass = if (type == HeaderType.SUCCESS) {
            localSdpInfo?.uPass ?: throw IllegalStateException("remote sdp info no received yet")
        } else {
            remoteSdpInfo?.uPass ?: throw IllegalStateException("remote sdp info no received yet")
        }

        val command = StunCommand(
            StunHeader(type, 0, Constants.MAGIC_COOKIE, id), attributes
        )
        Log.i(TAG, "Write: $command")
        socket.write(command.toByteArray(remotePass))
    }

    suspend fun writeIndication(socket: UdpStreamSocket) {
        val remotePass = remoteSdpInfo?.uPass ?: throw IllegalStateException("remote sdp info no received yet")
        val id = generateTransactionId()
        val command = StunCommand(
            StunHeader(HeaderType.INDICATION, 0, Constants.MAGIC_COOKIE, id), listOf(),
            useIntegrity = false
        )
        socket.write(command.toByteArray(remotePass))
    }

    suspend fun readStun(socket: UdpStreamSocket): StunCommand {
        val data = socket.read()
        val command = readStun(data)
        Log.i(TAG, "Read: $command")
        return command
    }

    suspend fun readStun(data: ByteArray): StunCommand {
        return StunCommandReader.readPacket(data)
    }

    private fun createBody(
        videoSsrc: Long, audioSsrc: Long,
        uFrag: String, uPass: String, fingerprint: String
    ): String {
        val cName = "RootEncoder"
        var videoBody = ""
        if (!videoDisabled) {
            val media = when (videoCodec) {
                VideoCodec.H264 -> {
                    SdpBody.createH264Body(rtpTracks.trackVideo, spsString, ppsString, true)
                }
                VideoCodec.H265 -> {
                    SdpBody.createH265Body(rtpTracks.trackVideo, spsString, ppsString, vpsString, true)
                }
                VideoCodec.AV1 -> {
                    SdpBody.createAV1Body(rtpTracks.trackVideo, true)
                }
            }
            videoBody = media +
                "a=rtcp-mux\r\n" +
                "a=ssrc:$videoSsrc cname:$cName\r\n"
        }
        var audioBody = ""
        if (!audioDisabled) {
            val media = when (audioCodec) {
                AudioCodec.G711 -> SdpBody.createG711Body(rtpTracks.trackAudio, sampleRate, isStereo, true)
                AudioCodec.OPUS -> SdpBody.createOpusBody(rtpTracks.trackAudio, true)
                else  -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
            }
            audioBody = media +
                "a=rtcp-mux\r\n" +
                "a=ssrc:$audioSsrc cname:$cName\r\n"
        }
        val bundleMids = listOfNotNull(
            if (!videoDisabled) rtpTracks.trackVideo else null,
            if (!audioDisabled) rtpTracks.trackAudio else null
        ).joinToString(" ")
        val sdpSha256 = fingerprint.chunked(2)
            .joinToString(":") { it.uppercase() }
        return "v=0\r\n" +
                "o=rtc $timeStamp $timeStamp IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "a=group:BUNDLE $bundleMids\r\n" +
                "a=msid-semantic:WMS *\r\n" +
                "a=setup:actpass\r\n" +
                "a=ice-ufrag:$uFrag\r\n" +
                "a=ice-pwd:$uPass\r\n" +
                "a=ice-options:trickle\r\n" +
                "a=fingerprint:sha-256 $sdpSha256\r\n" +
                videoBody +
                audioBody
    }

    fun generateTransactionId(): ByteArray {
        return secureRandom.nextBytes(12)
    }
}