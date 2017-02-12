package com.pedro.rtmpstreamer;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.view.SurfaceView;

import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAccData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.CameraManager;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtsp.rtp.PacketCreated;
import com.pedro.rtsp.rtp.RtpAccPacket;
import com.pedro.rtsp.rtp.RtpH264Packet;
import com.pedro.rtsp.rtp.RtpUDP;

import net.ossrs.rtmp.ConnectChecker;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/02/17.
 */

public class RtspBuilder implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData, PacketCreated {
    private int width;
    private int height;
    private CameraManager cameraManager;
    private VideoEncoder videoEncoder;
    private MicrophoneManager microphoneManager;
    private AudioEncoder audioEncoder;
    private boolean streaming;

    private RtpAccPacket rtpAccPacket;
    private RtpH264Packet rtpH264Packet;
    private RtpUDP rtpUDP;

    public RtspBuilder(SurfaceView surfaceView, ConnectChecker connectChecker) {
        cameraManager = new CameraManager(surfaceView, this);
        videoEncoder = new VideoEncoder(this);
        microphoneManager = new MicrophoneManager(this);
        audioEncoder = new AudioEncoder(this);
        streaming = false;
    }

    public void prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
        this.width = width;
        this.height = height;
        cameraManager.prepareCamera(width, height, fps, rotation, ImageFormat.NV21);
        videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, FormatVideoEncoder.YUV420PLANAR);
        rtpH264Packet = new RtpH264Packet(this);
    }

    public void prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
        microphoneManager.createMicrophone(sampleRate, isStereo);
        audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
        rtpAccPacket = new RtpAccPacket(sampleRate, this);
    }

    public void prepareVideo() {
        cameraManager.prepareCamera();
        videoEncoder.prepareVideoEncoder();
        width = videoEncoder.getWidth();
        height = videoEncoder.getHeight();
    }

    public void prepareAudio() {
        microphoneManager.createMicrophone();
        audioEncoder.prepareAudioEncoder();
    }

    public void startStream(String url) {
        //TODO connect to server rtsp
        rtpUDP = new RtpUDP(url, 1935, true);
        videoEncoder.start();
        audioEncoder.start();
        cameraManager.start();
        microphoneManager.start();
        streaming = true;
    }

    public void stopStream() {
        //TODO disconnect to server rtsp
        rtpUDP.close();
        cameraManager.stop();
        microphoneManager.stop();
        videoEncoder.stop();
        audioEncoder.stop();
        streaming = false;
    }

    public void enableDisableLantern() {
        if (isStreaming()) {
            if (cameraManager.isLanternEnable()) {
                cameraManager.disableLantern();
            } else {
                cameraManager.enableLantern();
            }
        }
    }

    public void switchCamera() {
        if (isStreaming()) {
            cameraManager.switchCamera();
        }
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setEffect(EffectManager effect) {
        if (isStreaming()) {
            cameraManager.setEffect(effect);
        }
    }

    @Override
    public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
        try {
            rtpAccPacket.createPacket(accBuffer, accBuffer.remaining(), info.presentationTimeUs);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        try {
            rtpH264Packet.createPacket(h264Buffer, h264Buffer.remaining(), info.presentationTimeUs);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void inputPcmData(byte[] buffer, int size) {
        audioEncoder.inputPcmData(buffer, size);
    }

    @Override
    public void inputYv12Data(byte[] buffer, int width, int height) {
        videoEncoder.inputYv12Data(buffer, width, height);
    }

    @Override
    public void inputNv21Data(byte[] buffer, int width, int height) {
        videoEncoder.inputNv21Data(buffer, width, height);
    }

    @Override
    public void onAccPacketCreated(ByteBuffer buffer) {
        //TODO send over udp to server
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        rtpUDP.sendPacket(bytes, 0, bytes.length);
    }

    @Override
    public void onH264PacketCreated(ByteBuffer buffer) {
        //TODO send over udp to server
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        rtpUDP.sendPacket(bytes, 0, bytes.length);
    }
}
