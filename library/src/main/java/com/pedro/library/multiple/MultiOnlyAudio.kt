/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pedro.library.multiple

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.base.OnlyAudioBase
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.SrtStreamClient
import com.pedro.library.util.streamclient.StreamBaseClient
import com.pedro.library.util.streamclient.UdpStreamClient
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.srt.srt.SrtClient
import com.pedro.udp.UdpClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 30/5/21.
 *
 * Support multiple streams in rtmp and rtsp at same time.
 * You must set the same number of ConnectChecker that you want use.
 *
 * For example. 2 RTMP and 1 RTSP:
 * stream1, stream2, stream3 (stream1 and stream2 are ConnectChecker for RTMP. stream3 is ConnectChecker for RTSP)
 *
 * MultiOnlyAudio multiOnlyAudio = new MultiOnlyAudio(new ConnectChecker[]{ stream1, stream2 },
 * new ConnectChecker[]{ stream3 });
 *
 * You can set an empty array or null if you don't want use a protocol
 * new MultiOnlyAudio(new ConnectChecker[]{ stream1, stream2 }, null); //RTSP protocol is not used
 *
 * In order to use start, stop and other calls you must send type of stream and index to execute it.
 * Example (using previous example interfaces):
 *
 * multiOnlyAudio.startStream(MultiType.RTMP, 1, endpoint); //stream2 is started
 * multiOnlyAudio.stopStream(MultiType.RTSP, 0); //stream3 is stopped
 * multiOnlyAudio.getStreamClient(MultiType.RTMP, 0).retry(delay, reason, backupUrl) //retry stream1
 *
 * NOTE:
 * If you call this methods nothing is executed:
 *
 * multiOnlyAudio.startStream(endpoint);
 * multiOnlyAudio.stopStream();
 *
 * The rest of methods without MultiType and index means that you will execute that command in all streams.
 * Read class code if you need info about any method.
 */
class MultiOnlyAudio(
    connectCheckerRtmpList: Array<ConnectChecker>?,
    connectCheckerRtspList: Array<ConnectChecker>?,
    connectCheckerSrtList: Array<ConnectChecker>?,
    connectCheckerUdpList: Array<ConnectChecker>?
) : OnlyAudioBase() {

    private val rtmpClients = ArrayList<RtmpClient>()
    private val rtspClients = ArrayList<RtspClient>()
    private val srtClients = ArrayList<SrtClient>()
    private val udpClients = ArrayList<UdpClient>()
    private val rtmpStreamClients = ArrayList<RtmpStreamClient>()
    private val rtspStreamClients = ArrayList<RtspStreamClient>()
    private val srtStreamClients = ArrayList<SrtStreamClient>()
    private val udpStreamClients = ArrayList<UdpStreamClient>()

    init {
        if (connectCheckerRtmpList.isNullOrEmpty() && connectCheckerRtspList.isNullOrEmpty()
            && connectCheckerSrtList.isNullOrEmpty() && connectCheckerUdpList.isNullOrEmpty()) {
            throw IllegalArgumentException("You need set at least one ConnectChecker interface")
        }
        connectCheckerRtmpList?.forEach {
            val client = RtmpClient(it).apply { setOnlyAudio(true) }
            rtmpClients.add(client)
            rtmpStreamClients.add(RtmpStreamClient(client, null))
        }
        connectCheckerRtspList?.forEach {
            val client = RtspClient(it).apply { setOnlyAudio(true) }
            rtspClients.add(client)
            rtspStreamClients.add(RtspStreamClient(client, null))
        }
        connectCheckerSrtList?.forEach {
            val client = SrtClient(it).apply { setOnlyAudio(true) }
            srtClients.add(client)
            srtStreamClients.add(SrtStreamClient(client, null))
        }
        connectCheckerUdpList?.forEach {
            val client = UdpClient(it).apply { setOnlyAudio(true) }
            udpClients.add(client)
            udpStreamClients.add(UdpStreamClient(client, null))
        }
    }

    fun getStreamClient(type: MultiType, index: Int): StreamBaseClient {
        return when (type) {
            MultiType.RTMP -> rtmpStreamClients[index]
            MultiType.RTSP -> rtspStreamClients[index]
            MultiType.SRT -> srtStreamClients[index]
            MultiType.UDP -> udpStreamClients[index]
        }
    }

    override fun getStreamClient(): StreamBaseClient {
        throw IllegalStateException("getStreamClient not allowed in Multi stream, use getStreamClient(type, index) instead")
    }

    override fun setAudioCodecImp(codec: AudioCodec) {
        for (rtmpClient in rtmpClients) rtmpClient.setAudioCodec(codec)
        for (rtspClient in rtspClients) rtspClient.setAudioCodec(codec)
        for (srtClient in srtClients) srtClient.setAudioCodec(codec)
        for (udpClient in udpClients) udpClient.setAudioCodec(codec)
    }

    fun startStream(type: MultiType, index: Int, url: String?) {
        var shouldStarEncoder = true
        for (rtmpClient in rtmpClients) {
            if (rtmpClient.isStreaming) {
                shouldStarEncoder = false
                break
            }
        }
        for (rtspClient in rtspClients) {
            if (rtspClient.isStreaming) {
                shouldStarEncoder = false
                break
            }
        }
        for (srtClient in srtClients) {
            if (srtClient.isStreaming) {
                shouldStarEncoder = false
                break
            }
        }
        for (udpClient in udpClients) {
            if (udpClient.isStreaming) {
                shouldStarEncoder = false
                break
            }
        }
        if (shouldStarEncoder) super.startStream("")
        when (type) {
            MultiType.RTMP -> rtmpClients[index].connect(url)
            MultiType.RTSP -> rtspClients[index].connect(url)
            MultiType.SRT -> srtClients[index].connect(url)
            MultiType.UDP -> udpClients[index].connect(url)
        }
    }

    override fun startStreamImp(url: String) {
    }

    fun stopStream(type: MultiType, index: Int) {
        var shouldStopEncoder = true
        for (rtmpClient in rtmpClients) {
            if (rtmpClient.isStreaming) {
                shouldStopEncoder = false
                break
            }
        }
        for (rtspClient in rtspClients) {
            if (rtspClient.isStreaming) {
                shouldStopEncoder = false
                break
            }
        }
        for (srtClient in srtClients) {
            if (srtClient.isStreaming) {
                shouldStopEncoder = false
                break
            }
        }
        for (udpClient in udpClients) {
            if (udpClient.isStreaming) {
                shouldStopEncoder = false
                break
            }
        }
        when (type) {
            MultiType.RTMP -> rtmpClients[index].disconnect()
            MultiType.RTSP -> rtspClients[index].disconnect()
            MultiType.SRT -> srtClients[index].disconnect()
            MultiType.UDP -> udpClients[index].disconnect()
        }
        if (shouldStopEncoder) super.stopStream()
    }

    override fun stopStreamImp() {
    }

    override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
        for (rtmpClient in rtmpClients) rtmpClient.setAudioInfo(sampleRate, isStereo)
        for (rtspClient in rtspClients) rtspClient.setAudioInfo(sampleRate, isStereo)
        for (srtClient in srtClients) srtClient.setAudioInfo(sampleRate, isStereo)
        for (udpClient in udpClients) udpClient.setAudioInfo(sampleRate, isStereo)
    }

    override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        for (rtmpClient in rtmpClients) rtmpClient.sendAudio(audioBuffer.duplicate(), info)
        for (rtspClient in rtspClients) rtspClient.sendAudio(audioBuffer.duplicate(), info)
        for (srtClient in srtClients) srtClient.sendAudio(audioBuffer.duplicate(), info)
        for (udpClient in udpClients) udpClient.sendAudio(audioBuffer.duplicate(), info)
    }
}
