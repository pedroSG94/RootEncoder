package com.pedro.whip.webrtc

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.addressToString
import com.pedro.common.VideoCodec
import com.pedro.common.nextBytes
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.rtsp.rtsp.commands.SdpBody
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.RtpTracks
import com.pedro.rtsp.utils.encodeToString
import com.pedro.common.getData
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
import javax.net.ssl.TrustManager
import kotlin.random.Random

class CommandsManager {

    var host: String? = null
        private set
    var port = 0
        private set
    var path: String? = null
        private set
    private val urlHost: String
        get() = host?.let { if (it.contains(":")) "[$it]" else it } ?: ""
    var token: String? = null
        private set
    private var shouldSendAuth = false
    private var tlsEnabled = false
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
    private var timeStamp = 0L
    private var sessionUrl: String? = null
    private val secureRandom = SecureRandom()
    val rtpTracks = RtpTracks()
    private var certificates: TrustManager? = null
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

    fun addCertificates(certificates: TrustManager?) {
        this.certificates = certificates
    }

    fun setAuth(token: String?) {
        this.token = token
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

    fun setUrl(host: String, port: Int, path: String, tlsEnabled: Boolean) {
        this.host = host
        this.port = port
        this.path = path
        this.tlsEnabled = tlsEnabled
    }

    fun clear() {
        sps = null
        pps = null
        vps = null
        remoteSdpInfo = null
        shouldSendAuth = false
        sessionUrl = null
    }

    suspend fun gatheringCandidates(socketType: SocketType, timeout: Long, gatheringMode: GatheringMode): List<Candidate> {
        val googleStunHost = "stun.l.google.com"
        val googleStunPort = 19302
        val startPort = 5000
        val hosts = Network.getNetworks(onlyV4 = false).map { it.addressToString() }
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

    fun writeOffer(sendAuth: Boolean = false): RequestResponse {
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
        val uri = "${if (tlsEnabled) "https" else "http"}://$urlHost:$port/$path"
        val headers = mutableMapOf<String, String>().apply {
            put("Content-Type", "application/sdp")
            if (!token.isNullOrEmpty() && sendAuth) put("Authorization", "Bearer $token")
        }
        val answer = Requests.makeRequest(
            uri, "POST", headers, body, timeout, tlsEnabled, certificates
        )
        if (answer.statusCode !in 200..299) return answer
        remoteSdpInfo = SdpParser.parseBodyAnswer(answer.body)
        tieBreak = secureRandom.nextBytes(8)
        sessionUrl = answer.headers.entries.firstOrNull { it.key.equals("location", true) }?.value
        Log.i(TAG, "remote info: $remoteSdpInfo")
        if (sendAuth) shouldSendAuth = true
        return answer
    }

    fun writeDelete(): RequestResponse {
        val uri = sessionUrl?.let {
            if (it.startsWith("http", true)) it
            else "${if (tlsEnabled) "https" else "http"}://$urlHost:$port/${it.removePrefix("/")}"
        } ?: "${if (tlsEnabled) "https" else "http"}://$urlHost:$port/$path"
        val headers = mutableMapOf<String, String>().apply {
            if (!token.isNullOrEmpty() && shouldSendAuth) put("Authorization", "Bearer $token")
        }
        return Requests.makeRequest(
            uri,
            "DELETE",
            headers,
            null,
            timeout,
            tlsEnabled,
            certificates
        )
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
                val (publicHost, publicPort) = StunAttributeValueParser.readXorMappedAddress(xorMappedAddress, result.header.id)
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

    fun updateTimestamp() {
        timeStamp = TimeUtils.getCurrentTimeNano()
    }
}