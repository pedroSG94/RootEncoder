package com.pedro.srt.srt

import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.utils.Constants
import com.pedro.srt.utils.SrtSocket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(MockitoJUnitRunner::class)
class SrtSenderTest {

    @Mock
    lateinit var connectChecker: ConnectChecker
    @Mock
    lateinit var socket: SrtSocket
    @Mock
    lateinit var commandsManager: CommandsManager
    private val output = ByteArrayOutputStream()
    private var latch = CountDownLatch(7)

    @Before
    fun setup() = runTest {
        output.reset()
        Mockito.`when`(commandsManager.audioCodec).thenReturn(AudioCodec.AAC)
        Mockito.`when`(commandsManager.videoCodec).thenReturn(VideoCodec.H264)
        Mockito.`when`(commandsManager.MTU).thenReturn(Constants.MTU)
        Mockito.lenient().`when`(commandsManager.writeData(any<MpegTsPacket>(), any<SrtSocket>())).then {
            val packet = it.arguments[0] as MpegTsPacket
            val size = packet.buffer.size
            output.write(packet.buffer)
            latch.countDown().let { size }
        }
    }

    @Test
    fun `GIVEN video and audio mediaFrames WHEN send to sender THEN write the expected packets`() = runTest {
        latch = CountDownLatch(7) //writeData must be called 4 times
        val srtSender = SrtSender(connectChecker, commandsManager)
        srtSender.setAudioInfo(44100, true)
        val sps = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 103, 100, 0, 30, -84, -76, 15, 2, -115, 53, 2, 2, 2, 7, -117, 23, 8))
        val pps = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 104, -18, 13, -117))
        srtSender.setVideoInfo(sps, pps, null)
        srtSender.socket = socket
        srtSender.start()

        val header = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x05)
        val videoData = ByteBuffer.wrap(header.plus(ByteArray(300) { 0x00 }))
        val audioData = ByteBuffer.wrap(ByteArray(256) { 0x00 })

        val videoFrame = MediaFrame(videoData, MediaFrame.Info(0, videoData.remaining(), 0, true), MediaFrame.Type.VIDEO)
        val audioFrame = MediaFrame(audioData, MediaFrame.Info(0, audioData.remaining(), 0, false), MediaFrame.Type.AUDIO)
        srtSender.sendMediaFrame(videoFrame)
        srtSender.sendMediaFrame(audioFrame)
        latch.await(1000, TimeUnit.MILLISECONDS)
        srtSender.stop()

        assertEquals(1692, output.toByteArray().size)
    }
}