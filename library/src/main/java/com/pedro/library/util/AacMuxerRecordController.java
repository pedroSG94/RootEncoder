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

package com.pedro.library.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.AudioUtils;
import com.pedro.common.BitrateManager;
import com.pedro.common.ExtensionsKt;
import com.pedro.library.base.recording.BaseRecordController;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Muxer to record AAC files (used in only audio by default).
 */
public class AacMuxerRecordController extends BaseRecordController {

    private OutputStream outputStream;
    private int sampleRate = -1;
    private int channels = -1;

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void startRecord(@NonNull String path, @Nullable Listener listener) throws IOException {
        outputStream = new FileOutputStream(path);
        start(listener);
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void startRecord(@NonNull FileDescriptor fd, @Nullable Listener listener) throws IOException {
        outputStream = new FileOutputStream(fd);
        start(listener);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void start(@Nullable Listener listener) throws IOException {
        if (audioCodec != AudioCodec.AAC) throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
        this.listener = listener;
        status = Status.STARTED;
        if (listener != null) {
            bitrateManager = new BitrateManager(listener);
            listener.onStatusChange(status);
        } else {
            bitrateManager = null;
        }
        if (sampleRate != -1 && channels != -1) init();
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopRecord() {
        status = Status.STOPPED;
        pauseMoment = 0;
        pauseTime = 0;
        sampleRate = -1;
        channels = -1;
        startTs = 0;
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) { } finally {
            outputStream = null;
        }
        if (listener != null) listener.onStatusChange(status);
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo) {

    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo) {
        if (status == Status.RECORDING) {
            updateFormat(this.audioInfo, audioInfo);
            //we need duplicate buffer to avoid problems with the buffer
            write(audioBuffer.duplicate(), this.audioInfo);
        }
    }

    @Override
    public void setVideoFormat(MediaFormat videoFormat, boolean isOnlyVideo) {
    }

    @Override
    public void setAudioFormat(MediaFormat audioFormat, boolean isOnlyAudio) {
        sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (status == Status.STARTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            init();
        }
    }

    @Override
    public void resetFormats() {
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void init() {
        status = Status.RECORDING;
        if (listener != null) listener.onStatusChange(status);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void write(ByteBuffer byteBuffer, MediaCodec.BufferInfo info) {
        try {
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                byte[] header = AudioUtils.INSTANCE.createAdtsHeader(2, info.size - info.offset, sampleRate, channels).array();
                outputStream.write(header);
                byte[] data = new byte[byteBuffer.remaining()];
                byteBuffer.get(data);
                outputStream.write(data);
                if (bitrateManager != null) bitrateManager.calculateBitrate(info.size * 8L, ExtensionsKt.getSuspendContext());
            }
        } catch (Exception e) {
            if (listener != null) listener.onError(e);
        }
    }
}