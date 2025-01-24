package com.pedro.whip.webrtc

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.TimeUtils
import com.pedro.common.VideoCodec
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.encodeToString
import com.pedro.rtsp.utils.getData
import com.pedro.whip.webrtc.SdpBody.createAV1Body
import com.pedro.whip.webrtc.SdpBody.createG711Body
import com.pedro.whip.webrtc.SdpBody.createH264Body
import com.pedro.whip.webrtc.SdpBody.createH265Body
import com.pedro.whip.webrtc.SdpBody.createOpusBody
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
    var audioCodec = AudioCodec.OPUS
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
            socket.outputStream.write(testSdp.toByteArray())
            val bytes = socket.inputStream.readBytes()
            if (bytes.size > 1) {
                Log.i("Pedro", "code: ${socket.responseCode}")
                Log.i("Pedro", String(bytes))
            }
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
                AudioCodec.OPUS -> createOpusBody(RtpConstants.trackAudio)
                else  -> throw IllegalArgumentException("Unsupported codec: ${audioCodec.name}")
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
                "a=setup:actpass\r\n" +
                "a=ice-ufrag:p+2y\r\n" +
                "a=ice-pwd:gvhiwDeOn6Q7cXjyoksq/m\r\n" +
                "a=ice-options:ice2,trickle\r\n" +
                "a=fingerprint:sha-256 DF:62:36:A7:30:8C:E1:00:C8:39:9A:6B:9F:5D:69:7D:57:9A:EB:F0:5F:DA:BA:FD:75:69:DC:D0:DD:82:32:2B\r\n" +

                videoBody + audioBody
    }

    private val testSdp = "v=0\r\n" +
            "o=- 5228595038118931041 2 IN IP4 127.0.0.1\r\n" +
            "s=-\r\n" +
            "t=0 0\r\n" +
            "a=group:BUNDLE 0 1\r\n" +
            "a=extmap-allow-mixed\r\n" +
            "a=ice-options:trickle ice2\r\n" +
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
            "c=IN IP4 0.0.0.0\r\n" +
            "a=rtcp:9 IN IP4 0.0.0.0\r\n" +
            "a=ice-ufrag:EsAw\r\n" +
            "a=ice-pwd:bP+XJMM09aR8AiX1jdukzR6Y\r\n" +
            "a=fingerprint:sha-256 DA:7B:57:DC:28:CE:04:4F:31:79:85:C4:31:67:EB:27:58:29:ED:77:2A:0D:24:AE:ED:AD:30:BC:BD:F1:9C:02\r\n" +
            "a=setup:actpass\r\n" +
            "a=mid:0\r\n" +
            "a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b ce326ecf-a081-453a-8f9f-0605d5ef4128\r\n" +
            "a=rtcp-mux\r\n" +
            "a=rtcp-mux-only\r\n" +
            "a=rtpmap:111 opus/48000/2\r\n" +
            "a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
            "m=video 0 UDP/TLS/RTP/SAVPF 96 97\r\n" +
            "a=mid:1\r\n" +
            "a=bundle-only\r\n" +
            "a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
            "a=extmap:10 urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
            "a=extmap:11 urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
            "a=sendonly\r\n" +
            "a=msid:d46fb922-d52a-4e9c-aa87-444eadc1521b 3956b460-40f4-4d05-acef-03abcdd8c6fd\r\n" +
            "a=rtpmap:96 VP8/90000\r\n" +
            "a=rtcp-fb:96 ccm fir\r\n" +
            "a=rtcp-fb:96 nack\r\n" +
            "a=rtcp-fb:96 nack pli\r\n" +
            "a=rtpmap:97 rtx/90000\r\n" +
            "a=fmtp:97 apt=96\r\n"
}