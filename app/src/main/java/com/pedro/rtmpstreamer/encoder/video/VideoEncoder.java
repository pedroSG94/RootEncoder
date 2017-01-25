package com.pedro.rtmpstreamer.encoder.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.pedro.rtmpstreamer.input.video.GetCameraData;
import com.pedro.rtmpstreamer.utils.YUVUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 * This class need use same resolution and fps that CameraManager
 */

public class VideoEncoder implements GetCameraData {

    private String TAG = "VideoEncoder";
    private MediaCodec videoEncoder;
    private GetH264Data getH264Data;
    private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
    private long mPresentTimeUs;
    private boolean running;

    //default parameters for encoder
    private String codec = "video/avc";
    private int width = 640;
    private int height = 480;
    private int fps = 24;
    private int bitRate = 1200 * 1000; //in kbps
    private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420;

    public VideoEncoder(GetH264Data getH264Data) {
        this.getH264Data = getH264Data;
    }


    /**
     * Prepare encoder with custom parameters
     */
    public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, FormatVideoEncoder formatVideoEncoder) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitRate = bitRate;
        this.formatVideoEncoder = formatVideoEncoder;
        MediaCodecInfo encoder = chooseVideoEncoder(null);
        try {
            if (encoder != null) {
                videoEncoder = MediaCodec.createByCodecName(encoder.getName());
            } else {
                Log.e(TAG, "valid encoder not found");
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "create videoEncoder failed.");
            e.printStackTrace();
            return false;
        }

        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat(codec, width, height);
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
    public boolean prepareVideoEncoder() {
        Log.i(TAG, "preparing videoEncoder with 640X480 resolution, 24fps, 1200kbps bitrate, YUV420 format");
        return prepareVideoEncoder(width, height, fps, bitRate, formatVideoEncoder);
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
        byte[] i420 = YUVUtil.YV12toYUV420Planar(buffer, width, height);
        getDataFromEncoder(i420);
    }

    @Override
    public void inputNv21Data(byte[] buffer, int width, int height) {
        byte[] i420 = YUVUtil.NV21toYUV420Planar(buffer, width, height);
        getDataFromEncoder(i420);
    }

    /**
     * call it to encoder YUV 420, 422, 444
     * remember create encoder with correct color format before
     */
    public void inputYuv4XX(byte[] buffer) {
        getDataFromEncoder(buffer);
    }

    private void getDataFromEncoder(byte[] buffer) {
        int inBufferIndex = videoEncoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = videoEncoder.getInputBuffer(inBufferIndex);
            bb.clear();
            bb.put(buffer, 0, buffer.length);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            videoEncoder.queueInputBuffer(inBufferIndex, 0, buffer.length, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex >= 0) {
                //This ByteBuffer is H264
                ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
                getH264Data.getH264Data(bb, videoInfo);
                videoEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }

    }

    /**
     * choose the video encoder by name.
     */
    private MediaCodecInfo chooseVideoEncoder(String name) {
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
        for (MediaCodecInfo mci : mediaCodecInfos) {
            if (!mci.isEncoder()) {
                continue;
            }
            String[] types = mci.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase("video/avc")) {
                    Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), type));
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
}
