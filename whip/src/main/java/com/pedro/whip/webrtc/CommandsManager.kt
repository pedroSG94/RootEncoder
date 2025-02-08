package com.pedro.whip.webrtc

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.common.socket.UdpStreamSocket
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import com.pedro.whip.webrtc.SdpBody.createAV1Body
import com.pedro.whip.webrtc.SdpBody.createG711Body
import com.pedro.whip.webrtc.SdpBody.createH264Body
import com.pedro.whip.webrtc.SdpBody.createH265Body
import com.pedro.whip.webrtc.SdpBody.createOpusBody
import com.pedro.whip.webrtc.stun.StunCommand
import com.pedro.whip.webrtc.stun.StunCommandReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import javax.net.ssl.HttpsURLConnection

class CommandsManager {

    var host: String? = null
        private set
    var port = 0
        private set
    var path: String? = null
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

    fun setUrl(host: String?, port: Int, path: String?) {
        this.host = host
        this.port = port
        this.path = path
    }

    fun clear() {
        sps = null
        pps = null
        vps = null
    }

    suspend fun writeStun(stunCommand: StunCommand, socket: UdpStreamSocket) {
        socket.writePacket(stunCommand.toByteArray())
    }

    suspend fun readStun(socket: UdpStreamSocket): StunCommand {
        val data = socket.readPacket()
        return StunCommandReader.readPacket(data)
    }

    @Throws(IOException::class)
    fun openConnection(host: String, port: Int, stunPort: Int, path: String, secured: Boolean): String {
        val socket = configureSocket(host, port, path, secured)
        try {
            socket.connect()
            val body = createBody(stunPort)
            socket.outputStream.write(body.toByteArray())
            Log.i(TAG, body)
            val bytes = socket.inputStream.readBytes()
            val success = socket.responseCode == HttpURLConnection.HTTP_CREATED
            if (!success || bytes.isEmpty()) throw IOException("send packet failed: ${socket.responseMessage}, broken pipe")
            else return String(bytes)
        } finally {
            socket.disconnect()
        }
    }

    private fun configureSocket(host: String, port: Int, path: String, secured: Boolean): HttpURLConnection {
        val schema = if (secured) "https" else "http"
        val url = URL("$schema://$host:$port/$path")
        val socket = if (secured) {
            url.openConnection() as HttpsURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
        socket.requestMethod = "POST"
        val headers = mapOf(
            "Content-Type" to "application/sdp"
        )
        headers.forEach { (key, value) -> socket.addRequestProperty(key, value) }
        socket.doOutput = true
        socket.connectTimeout = timeout
        socket.readTimeout = timeout
        return socket
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