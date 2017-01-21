package com.pedro.rtmpstreamer.encoder.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.pedro.rtmpstreamer.input.video.GetCameraData;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 */

public class VideoEncoder implements GetCameraData {

    private String TAG = "VideoEncoder";
    private MediaCodec videoEncoder;
    private GetH264Data getH264Data;
    private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
    private long mPresentTimeUs;
    private boolean running;

    public VideoEncoder(GetH264Data getH264Data) {
        this.getH264Data = getH264Data;
    }


    /**
     * Prepare encoder with custom parameters
     */
    public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, FormatVideoEncoder formatVideoEncoder) {
        MediaCodecInfo encoder = chooseVideoEncoder(null);
        try {
            videoEncoder = MediaCodec.createByCodecName(encoder.getName());
        } catch (IOException e) {
            Log.e(TAG, "create videoEncoder failed.");
            e.printStackTrace();
            return false;
        }

        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, formatVideoEncoder.getFormatCodec());
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        running = false;
        return true;
    }

    /**
     * Prepare encoder with default parameters
     */
    public boolean prepareVideoEncoder(){
        Log.i(TAG, "preparing videoEncoder with 640X480 resolution, 24fps, 1200kbps bitrate, YUV420 format");
        return prepareVideoEncoder(640, 480, 24, 1200 * 1000, FormatVideoEncoder.YUV420);
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        mPresentTimeUs = System.nanoTime() / 1000;
        videoEncoder.start();
        running = true;
    }

    public void stop() {
        running = false;
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
    }

    @Override
    public void inputYv12Data(byte[] buffer, int width, int height) {
        byte[] i420 = swapYV12toI420(buffer, width, height);
        getDataFromEncoder(i420);
    }

    /**call it to encoder YUV 420, 422, 444*/
    public void inputYuv4XX(byte[] buffer) {
        getDataFromEncoder(buffer);
    }

    private void getDataFromEncoder(byte[] buffer) {
        ByteBuffer[] inBuffers = videoEncoder.getInputBuffers();
        ByteBuffer[] outBuffers = videoEncoder.getOutputBuffers();

        int inBufferIndex = videoEncoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(buffer, 0, buffer.length);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            videoEncoder.queueInputBuffer(inBufferIndex, 0, buffer.length, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex >= 0) {
                //This ByteBuffer is H264
                ByteBuffer bb = outBuffers[outBufferIndex];
                getH264Data.getH264Data(bb, videoInfo);
                videoEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }

    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase("video/avc")) {
                    Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }

    private byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + (width / 2 * height / 2),
                i420bytes, width * height, width * height + (width / 2 * height / 2) - width * height);
        System.arraycopy(yv12bytes, width * height + (width / 2 * height / 2) - (width / 2 * height / 2),
                i420bytes, width * height + (width / 2 * height / 2),
                width * height + 2 * (width / 2 * height / 2) - (width * height + (width / 2 * height / 2)));
        return i420bytes;
    }
}
