/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.library.base.recording;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.pedro.common.AudioCodec;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.rtsp.utils.RtpConstants;

import java.nio.ByteBuffer;

public abstract class BaseRecordController implements RecordController {

    protected Status status = Status.STOPPED;
    protected VideoCodec videoCodec = VideoCodec.H264;
    protected AudioCodec audioCodec = AudioCodec.AAC;
    protected long pauseMoment = 0;
    protected long pauseTime = 0;
    protected Listener listener;
    protected int videoTrack = -1;
    protected int audioTrack = -1;
    protected final MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
    protected final MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
    protected boolean isOnlyAudio = false;
    protected boolean isOnlyVideo = false;

    public void setVideoCodec(VideoCodec videoCodec) {
        this.videoCodec = videoCodec;
    }

    public void setAudioCodec(AudioCodec audioCodec) {
        this.audioCodec = audioCodec;
    }

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

    protected boolean isKeyFrame(ByteBuffer videoBuffer) {
        byte[] header = new byte[5];
        if (videoBuffer.remaining() < header.length) return false;
        videoBuffer.duplicate().get(header, 0, header.length);
        if (videoCodec == VideoCodec.AV1) {
            //TODO find the way to check it
            return false;
        } else if (videoCodec == VideoCodec.H264 && (header[4] & 0x1F) == RtpConstants.IDR) {  //h264
            return true;
        } else { //h265
            return videoCodec == VideoCodec.H265
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

    public void setVideoFormat(MediaFormat videoFormat) {
        setVideoFormat(videoFormat, false);
    }

    public void setAudioFormat(MediaFormat audioFormat) {
        setAudioFormat(audioFormat, false);
    }
}
