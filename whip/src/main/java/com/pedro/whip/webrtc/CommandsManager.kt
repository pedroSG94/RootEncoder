package com.pedro.whip.webrtc

import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.rtsp.rtsp.commands.SdpBody.createAV1Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createAacBody
import com.pedro.rtsp.rtsp.commands.SdpBody.createG711Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH264Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createH265Body
import com.pedro.rtsp.rtsp.commands.SdpBody.createOpusBody
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
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
    var audioCodec = AudioCodec.AAC
    private val timeout = 5000
    private val timeStamp: Long
    val spsString: String
        get() = sps?.getData()?.encodeToString() ?: ""
    val ppsString: String
        get() = pps?.getData()?.encodeToString() ?: ""
    val vpsString: String
        get() = vps?.getData()?.encodeToString() ?: ""

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

    fun openConnection(host: String, port: Int, path: String, secured: Boolean) {
        val socket = configureSocket(host, port, path, secured)
        try {
            socket.connect()
            socket.outputStream.write(createBody().toByteArray())
            val bytes = socket.inputStream.readBytes()
            var input: InputStream? = null
            if (bytes.size > 1) input = ByteArrayInputStream(bytes, 1, bytes.size)
            val success = socket.responseCode == HttpURLConnection.HTTP_CREATED
            if (!success) throw IOException("send packet failed: ${socket.responseMessage}, broken pipe")
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

    private fun createBody(): String {
        var videoBody = ""
        if (!videoDisabled) {
            videoBody = when (videoCodec) {
                VideoCodec.H264 -> {
                    createH264Body(RtpConstants.trackVideo, spsString, ppsString)
                }
                VideoCodec.H265 -> {
                    createH265Body(RtpConstants.trackVideo, spsString, ppsString, vpsString)
                }
                VideoCodec.AV1 -> {
                    createAV1Body(RtpConstants.trackVideo)
                }
            }
        }
        var audioBody = ""
        if (!audioDisabled) {
            audioBody = when (audioCodec) {
                AudioCodec.G711 -> createG711Body(RtpConstants.trackAudio, sampleRate, isStereo)
                AudioCodec.AAC -> createAacBody(RtpConstants.trackAudio, sampleRate, isStereo)
                AudioCodec.OPUS -> createOpusBody(RtpConstants.trackAudio)
            }
        }
        return "v=0\r\n" +
                "o=- $timeStamp $timeStamp IN IP4 127.0.0.1\r\n" +
                "s=Unnamed\r\n" +
                "i=N/A\r\n" +
                "c=IN IP4 $host\r\n" +
                "t=0 0\r\n" +
                "a=recvonly\r\n" +

                "a=group:BUNDLE 0 1\r\n" +
                "a=group:LS 0 1\r\n" +
                "a=msid-semantic:WMS *\r\n" +
                "a=setup:actpass\r\n" +
                "a=ice-ufrag:p+2y\r\n" +
                "a=ice-pwd:gvhiwDeOn6Q7cXjyoksq/m\r\n" +
                "a=ice-options:ice2,trickle\r\n" +
                "a=fingerprint:sha-256 DF:62:36:A7:30:8C:E1:00:C8:39:9A:6B:9F:5D:69:7D:57:9A:EB:F0:5F:DA:BA:FD:75:69:DC:D0:DD:82:32:2B\r\n" +

                videoBody + audioBody
    }
}