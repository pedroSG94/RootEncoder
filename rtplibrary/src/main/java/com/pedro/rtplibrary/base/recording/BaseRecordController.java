package com.pedro.rtplibrary.base.recording;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.CallSuper;

import com.pedro.encoder.utils.CodecUtil;
import com.pedro.rtsp.utils.RtpConstants;

import java.nio.ByteBuffer;

public abstract class BaseRecordController implements RecordController {

    protected Status status = Status.STOPPED;
    protected String videoMime = CodecUtil.H264_MIME;
    protected long pauseMoment = 0;
    protected long pauseTime = 0;
    protected Listener listener;
    protected int videoTrack = -1;
    protected int audioTrack = -1;
    protected final MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
    protected final MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();



    public boolean isRunning() {
        return status == Status.STARTED
                || status == Status.RECORDING
                || status == Status.RESUMED
                || status == Status.PAUSED;
    }

    public boolean isRecording() {
        return status == Status.RECORDING;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public void resetFormats() {

    }

    public abstract void init();

    public void pauseRecord() {
        if (status == Status.RECORDING) {
            pauseMoment = System.nanoTime() / 1000;
            status = Status.PAUSED;
            if (listener != null) listener.onStatusChange(status);
        }
    }

    public void resumeRecord() {
        if (status == Status.PAUSED) {
            pauseTime += System.nanoTime() / 1000 - pauseMoment;
            status = Status.RESUMED;
            if (listener != null) listener.onStatusChange(status);
        }
    }

    @Override
    public void setVideoMime(String videoMime) {

    }


    @Override
    public void setAudioFormat(MediaFormat audioFormat) {

    }

    @Override
    public void setVideoFormat(MediaFormat videoFormat, boolean isOnlyVideo) {

    }

    @Override
    public void setAudioFormat(MediaFormat audioFormat, boolean isOnlyVideo) {

    }

    @Override
    @CallSuper
    public void stopRecord() {
        videoTrack = -1;
        audioTrack = -1;
    }

    protected boolean isKeyFrame(ByteBuffer videoBuffer) {
        byte[] header = new byte[5];
        videoBuffer.duplicate().get(header, 0, header.length);
        if (videoMime.equals(CodecUtil.H264_MIME) && (header[4] & 0x1F) == RtpConstants.IDR) {  //h264
            return true;
        } else { //h265
            return videoMime.equals(CodecUtil.H265_MIME)
                    && ((header[4] >> 1) & 0x3f) == RtpConstants.IDR_W_DLP
                    || ((header[4] >> 1) & 0x3f) == RtpConstants.IDR_N_LP;
        }
    }

    //We can't reuse info because could produce stream issues
    protected void updateFormat(MediaCodec.BufferInfo newInfo, MediaCodec.BufferInfo oldInfo) {
        newInfo.flags = oldInfo.flags;
        newInfo.offset = oldInfo.offset;
        newInfo.size = oldInfo.size;
        newInfo.presentationTimeUs = oldInfo.presentationTimeUs - pauseTime;
    }


}
