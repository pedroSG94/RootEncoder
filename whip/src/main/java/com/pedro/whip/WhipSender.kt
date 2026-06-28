package com.pedro.whip

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.base.BaseSender
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.socket.base.UdpStreamSocket
import com.pedro.common.validMessage
import com.pedro.rtsp.rtcp.BaseSenderReport
import com.pedro.rtsp.rtp.packets.AacPacket
import com.pedro.rtsp.rtp.packets.Av1Packet
import com.pedro.rtsp.rtp.packets.BasePacket
import com.pedro.rtsp.rtp.packets.G711Packet
import com.pedro.rtsp.rtp.packets.H264Packet
import com.pedro.rtsp.rtp.packets.H265Packet
import com.pedro.rtsp.rtp.packets.OpusPacket
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.CryptoProperties
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.whip.webrtc.CommandsManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.nio.ByteBuffer

class WhipSender(
    connectChecker: ConnectChecker,
    private val commandsManager: CommandsManager
): BaseSender(connectChecker, "WhipSender") {

    private var videoPacket: BasePacket = H264Packet(commandsManager.rtpTracks.trackVideo)
    private var audioPacket: BasePacket = AacPacket(commandsManager.rtpTracks.trackAudio)
    private var rtpSocket: BaseRtpSocket? = null
    private var baseSenderReport: BaseSenderReport? = null

    @Throws(IOException::class)
    fun setSocketsInfo(socket: UdpStreamSocket) {
        rtpSocket = BaseRtpSocket.getInstance(socket)
        baseSenderReport = BaseSenderReport.getInstance(commandsManager.rtpTracks, socket)
    }

    fun setCrypto(properties: CryptoProperties) {
        baseSenderReport?.setCrypto(properties)
        videoPacket.setCryptoProperties(properties)
        audioPacket.setCryptoProperties(properties)
    }

    override fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
        videoPacket = when (commandsManager.videoCodec) {
            VideoCodec.H264 -> {
                if (pps == null) throw IllegalArgumentException("pps can't be null with h264")
                H264Packet(commandsManager.rtpTracks.trackVideo).apply { sendVideoInfo(sps, pps) }
            }
            VideoCodec.H265 -> {
                if (vps == null || pps == null) throw IllegalArgumentException("pps or vps can't be null with h265")
                H265Packet(commandsManager.rtpTracks.trackVideo).apply { sendVideoInfo(sps, pps, vps) }
            }
            VideoCodec.AV1 -> Av1Packet(commandsManager.rtpTracks.trackVideo)
        }
    }

    override fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
        audioPacket = when (commandsManager.audioCodec) {
            AudioCodec.G711 -> G711Packet(commandsManager.rtpTracks.trackAudio).apply { setAudioInfo(sampleRate) }
            AudioCodec.AAC -> AacPacket(commandsManager.rtpTracks.trackAudio).apply { setAudioInfo(sampleRate) }
            AudioCodec.OPUS -> OpusPacket(commandsManager.rtpTracks.trackAudio).apply { setAudioInfo(sampleRate) }
        }
    }

    override suspend fun onRun() {
        val ssrcVideo = commandsManager.videoSsrc
        val ssrcAudio = commandsManager.audioSsrc
        baseSenderReport?.setSSRC(ssrcVideo, ssrcAudio)
        videoPacket.setSSRC(ssrcVideo)
        audioPacket.setSSRC(ssrcAudio)
        val isTcp = rtpSocket is RtpSocketTcp
        while (scope.isActive && running) {
            val error = runCatching {
                val mediaFrame = runInterruptible { queue.take() }
                getRtpPackets(mediaFrame) { rtpFrames ->
                    var size = 0L
                    var isVideo = false
                    rtpFrames.forEach { rtpFrame ->
                        rtpSocket?.sendFrame(rtpFrame)
                        //4 is tcp header length
                        val packetSize = (if (isTcp) rtpFrame.length + 4 else rtpFrame.length).toLong()
                        bytesSend.addAndGet(packetSize)
                        bytesSendPerSecond.addAndGet(packetSize)
                        size += packetSize
                        isVideo = rtpFrame.isVideoFrame(commandsManager.rtpTracks.trackVideo)
                        if (isVideo) videoFramesSent.incrementAndGet()
                        else audioFramesSent.incrementAndGet()
                        if (baseSenderReport?.update(rtpFrame) == true) {
                            //4 is tcp header length
                            val reportSize = (if (isTcp) RtpConstants.REPORT_PACKET_LENGTH + 4 else RtpConstants.REPORT_PACKET_LENGTH).toLong()
                            bytesSend.addAndGet(reportSize)
                            bytesSendPerSecond.addAndGet(reportSize)
                            if (isEnableLogs) Log.i(TAG, "wrote report")
                        }
                    }
                    rtpSocket?.flush()
                    if (isEnableLogs) {
                        val type = if (isVideo) "Video" else "Audio"
                        Log.i(TAG, "wrote $type packet, size $size")
                    }
                }
            }.exceptionOrNull()
            if (error != null) {
                onMainThread {
                    connectChecker.onConnectionFailed("Error send packet, ${error.validMessage()}")
                }
                Log.e(TAG, "send error: ", error)
                running = false
                return
            }
        }
    }

    override suspend fun stopImp(clear: Boolean) {
        baseSenderReport?.reset()
        baseSenderReport?.close()
        rtpSocket?.close()
        audioPacket.reset()
        videoPacket.reset()
    }

    private suspend fun getRtpPackets(mediaFrame: MediaFrame?, callback: suspend (List<RtpFrame>) -> Unit) {
        if (mediaFrame == null) return
        when (mediaFrame.type) {
            MediaFrame.Type.VIDEO -> videoPacket.createAndSendPacket(mediaFrame) { callback(it) }
            MediaFrame.Type.AUDIO -> audioPacket.createAndSendPacket(mediaFrame) { callback(it) }
        }
    }
}