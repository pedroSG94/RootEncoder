package com.pedro.rtmpstreamer;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.util.Base64;
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

import com.pedro.rtsp.rtsp.RtspClient;
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

    private RtspClient rtspClient;

    public RtspBuilder(SurfaceView surfaceView, ConnectChecker connectChecker) {
        rtspClient = new RtspClient();
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

    }

    public void prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
        microphoneManager.createMicrophone(sampleRate, isStereo);
        audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
    }

    public void prepareVideo() {
        cameraManager.prepareCamera();
        videoEncoder.prepareVideoEncoder();
        width = videoEncoder.getWidth();
        height = videoEncoder.getHeight();
        rtpH264Packet = new RtpH264Packet(this);
    }

    public void prepareAudio() {
        microphoneManager.createMicrophone();
        audioEncoder.prepareAudioEncoder();
        rtpAccPacket = new RtpAccPacket(44100, this);
    }

    public void startStream(String url) {
        //TODO connect to server rtsp
        rtpUDP = new RtpUDP(url, true);
        videoEncoder.start();
        audioEncoder.start();
        cameraManager.start();
        microphoneManager.start();
        streaming = true;
    }

    public void stopStream() {
        //TODO disconnect to server rtsp
        rtspClient.disconnect();
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
    public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
        byte[] mSPS = new byte[sps.capacity() - 4];
        sps.position(4);
        sps.get(mSPS, 0, mSPS.length);
        byte[] mPPS = new byte[pps.capacity() - 4];
        pps.position(4);
        pps.get(mPPS, 0, mPPS.length);

        String sSPS = Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP);
        String sPPS = Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP);
        rtspClient.setSPSandPPS(sSPS, sPPS);
        rtspClient.connect();
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
    //6984-6985
    @Override
    public void onAccPacketCreated(ByteBuffer buffer) {
        //TODO send over udp to server
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        rtpUDP.sendPacket(bytes, 0, bytes.length, rtspClient.getAudioPorts()[0]);
    }
    //7042-7043
    @Override
    public void onH264PacketCreated(ByteBuffer buffer) {
        //TODO send over udp to server
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        rtpUDP.sendPacket(bytes, 0, bytes.length, rtspClient.getVideoPorts()[0]);
    }
}
