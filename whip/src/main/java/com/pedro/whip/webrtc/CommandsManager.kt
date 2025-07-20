package com.pedro.whip.webrtc

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.nextBytes
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.StreamSocket
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.toUInt32
import com.pedro.rtsp.rtsp.commands.SdpBody
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import com.pedro.whip.dtls.CryptoUtils
import com.pedro.whip.utils.Constants
import com.pedro.whip.utils.Network
import com.pedro.whip.utils.RequestResponse
import com.pedro.whip.utils.Requests
import com.pedro.whip.webrtc.stun.AttributeType
import com.pedro.whip.webrtc.stun.GatheringMode
import com.pedro.whip.webrtc.stun.StunAttribute
import com.pedro.whip.webrtc.stun.StunAttributeValueParser
import com.pedro.whip.webrtc.stun.StunCommand
import com.pedro.whip.webrtc.stun.StunCommandReader
import com.pedro.whip.webrtc.stun.StunHeader
import com.pedro.whip.webrtc.stun.HeaderType
import kotlinx.coroutines.delay
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.CRC32
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
    private val secureRandom = SecureRandom()
    var remoteSdpInfo: SdpInfo? = null
        private set
    var localSdpInfo: SdpInfo? = null
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
        val googleStunHost = "stun.l.google.com"
        val googleStunPort = 19302
        val startPort = 5000
        val hosts = Network.getNetworks(onlyV4 = true).mapNotNull { it.hostAddress }
        return when (gatheringMode) {
          GatheringMode.LOCAL -> getLocalCandidates(hosts, startPort)
          GatheringMode.ALL -> {
              val localCandidates = getLocalCandidates(hosts, startPort)
              val publicCandidates = getStunCandidates(socketType, googleStunHost, googleStunPort, hosts, startPort + localCandidates.size)
              return localCandidates + publicCandidates
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

    fun generateTieBreak(): ByteArray {
        return secureRandom.nextBytes(8)
    }
    
    fun writeOffer(candidates: List<Candidate>): RequestResponse {
        val uFrag = secureRandom.nextLong().toString(36).replace("-", "")
        val uPass = (BigInteger(130, secureRandom).toString(32)).replace("-", "")
        val certificate = CryptoUtils.generateCert("RootEncoder", secureRandom)
        val videoSsrc = Random.nextLong()
        val audioSsrc = Random.nextLong()
        val body = createBody(
            videoSsrc, audioSsrc, candidates, uFrag, uPass, certificate.fingerprint
        )
        localSdpInfo = SdpInfo(uFrag, uPass, certificate.fingerprint, candidates)
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
        Log.i(TAG, "remote info: $remoteSdpInfo")
        return answer
    }

    private suspend fun getStunCandidates(
        socketType: SocketType,
        stunHost: String,
        stunPort: Int,
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
                receiveSize = RtpConstants.MTU,
            )
            candidateSocket.bind()
            val command = StunCommand(
                header = StunHeader(HeaderType.REQUEST, 0, Constants.MAGIC_COOKIE, generateTransactionId()),
                attributes = listOf(),
                useIntegrity = false,
                useFingerprint = false
            )
            writeStun(command, candidateSocket, stunHost, stunPort)
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

    suspend fun writeStun(stunCommand: StunCommand, socket: UdpStreamSocket, host: String, port: Int) {
        val remotePass = if (!stunCommand.useIntegrity) "" else remoteSdpInfo?.uPass ?: throw IllegalStateException("remote sdp info no received yet")
        Log.i(TAG, stunCommand.toString())
        socket.write(stunCommand.toByteArray(remotePass), host, port)
    }

    suspend fun writeStun(type: HeaderType, id: ByteArray, attributes: List<StunAttribute>, socket: UdpStreamSocket, host: String, port: Int) {
        val remotePass = remoteSdpInfo?.uPass ?: throw IllegalStateException("remote sdp info no received yet")
        val command = StunCommand(
            StunHeader(type, 0, Constants.MAGIC_COOKIE, id), attributes
        )
        Log.i(TAG, command.toString())
        socket.write(command.toByteArray(remotePass), host, port)
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
                "a=rtcp-mux\r\n" + //Using same socket for all (SRTP, SRTCP and both tracks)
                "a=ssrc:" + videoSsrc + " cname: $cName\r\n" +
                audioBody +
                "a=rtcp-mux\r\n" + //Using same socket for all (SRTP, SRTCP and both tracks)
                "a=ssrc:" + audioSsrc + " cname: $cName\r\n" +
                addCandidates(candidates)
    }

    private fun addCandidates(candidates: List<Candidate>): String {
        var sdpCandidates = ""
        candidates.forEach { candidate ->
            val address = candidate.publicAddress ?: candidate.localAddress
            val port = candidate.publicPort ?: candidate.localPort
            sdpCandidates += "a=candidate:${generateFoundation(candidate.type, address, port)} ${candidate.protocol} UDP ${candidate.priority} $address $port typ ${candidate.type.value} ${
                if (candidate.type == CandidateType.SRFLX) {
                    "raddr ${candidate.localAddress} rport ${candidate.localPort}"
                } else ""
            }\r\n"
        }
        return sdpCandidates
    }

    private fun generateFoundation(type: CandidateType, host: String, port: Int): Long {
        val crc32 = CRC32()
        crc32.update("${type.value}$host${port}UDP".toByteArray(Charsets.UTF_8))
        return crc32.value
    }

    fun generateTransactionId(): ByteArray {
        return secureRandom.nextBytes(12)
    }

    suspend fun sendBindingRequestToCandidate(
        localCandidate: Candidate,
        remoteCandidate: Candidate,
        tieBreak: ByteArray,
        socket: UdpStreamSocket
    ) {
        val localFrag = localSdpInfo?.uFrag ?: return
        val remoteFrag = remoteSdpInfo?.uFrag ?: return
        val host = remoteCandidate.publicAddress ?: remoteCandidate.localAddress
        val port = remoteCandidate.publicPort ?: remoteCandidate.localPort
        val timeout = arrayOf(100L, 200L, 400L, 800L, 1500L, 2000)
        for (i in 0..timeout.size) {
            val id = generateTransactionId()
            val userName = StunAttributeValueParser.createUserName(localFrag, remoteFrag)
            val attributes = listOf(
                StunAttribute(AttributeType.PRIORITY, localCandidate.priority.toUInt32()),
                StunAttribute(AttributeType.USERNAME, userName),
                StunAttribute(AttributeType.ICE_CONTROLLING, tieBreak),
            )
            writeStun(HeaderType.REQUEST, id, attributes, socket, host, port)
            Log.i(TAG, "candidate request attempt: $i\nlocalCandidate: $localCandidate\nremoteCandidate: $remoteCandidate")
            delay(timeout[i])
        }
    }

    suspend fun sendSuccess(id: ByteArray, host: String, port: Int, socket: UdpStreamSocket) {
        val localFrag = localSdpInfo?.uFrag ?: return
        val remoteFrag = remoteSdpInfo?.uFrag ?: return
        val userNameValue = StunAttributeValueParser.createUserName(remoteFrag, localFrag)
        val xorAddress = StunAttributeValueParser.createXorMappedAddress(id, host, port, true)
        val attributes = listOf(
            StunAttribute(AttributeType.USERNAME, userNameValue),
            StunAttribute(AttributeType.XOR_MAPPED_ADDRESS, xorAddress)
        )
        writeStun(HeaderType.SUCCESS, id, attributes, socket, host, port)
    }
}