package com.pedro.rtmpstreamer.encoder.audio;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.pedro.rtmpstreamer.input.audio.GetMicrophoneData;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 * Encode PCM audio data to ACC and return in a callback
 */

public class AudioEncoder implements GetMicrophoneData {

    private String TAG = "AudioEncoder";
    private MediaCodec audioEncoder;
    private GetAccData getAccData;
    private MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
    private long mPresentTimeUs;
    private boolean running;

    //default parameters for encoder
    private String codec = "audio/mp4a-latm";
    private int bitRate = 128 * 1024;  //in kbps
    private int sampleRate = 44100; //in hz
    private int channel = AudioFormat.CHANNEL_IN_STEREO;

    public AudioEncoder(GetAccData getAccData) {
        this.getAccData = getAccData;
    }

    /**
     * Prepare encoder with custom parameters
     */
    public void prepareAudioEncoder(int sampleRate, int channel) {
        this.sampleRate = sampleRate;
        this.channel = channel;
        try {
            audioEncoder = MediaCodec.createEncoderByType(codec);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int a = channel == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(codec, sampleRate, a);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        running = false;
    }

    /**
     * Prepare encoder with default parameters
     */
    public void prepareAudioEncoder() {
        prepareAudioEncoder(sampleRate, channel);
    }


    public void start() {
        mPresentTimeUs = System.nanoTime() / 1000;
        audioEncoder.start();
        running = true;
        Log.i(TAG, "AudioEncoder started");
    }

    public void stop() {
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
        running = false;
        Log.i(TAG, "AudioEncoder stopped");
    }

    /**
     * Set custom PCM data.
     * Use it after prepareAudioEncoder(int sampleRate, int channel).
     * Used too with microphone.
     *
     * @param buffer PCM buffer
     * @param size   Min PCM buffer size
     */
    @Override
    public void inputPcmData(byte[] buffer, int size) {
        getDataFromEncoder(buffer, size);
    }

    private void getDataFromEncoder(byte[] data, int size) {
        int inBufferIndex = audioEncoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = audioEncoder.getInputBuffer(inBufferIndex);
            bb.clear();
            bb.put(data, 0, size);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            audioEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = audioEncoder.dequeueOutputBuffer(audioInfo, 0);
            if (outBufferIndex >= 0) {
                //This ByteBuffer is ACC
                ByteBuffer bb = audioEncoder.getOutputBuffer(outBufferIndex);
                getAccData.getAccData(bb, audioInfo);
                audioEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
