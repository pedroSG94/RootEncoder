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

package com.pedro.library.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.library.base.recording.BaseRecordController;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Muxer to record AAC files (used in only audio by default).
 */
public class AacMuxerRecordController extends BaseRecordController {

    private static final String TAG = "AacMuxRecordController";
    private OutputStream outputStream;
    private final Integer[] AudioSampleRates = new Integer[] {
            96000,  // 0
            88200,  // 1
            64000,  // 2
            48000,  // 3
            44100,  // 4
            32000,  // 5
            24000,  // 6
            22050,  // 7
            16000,  // 8
            12000,  // 9
            11025,  // 10
            8000,  // 11
            7350,  // 12
            -1,  // 13
            -1,  // 14
            -1
    };
    private int sampleRate = -1;
    private int channels = -1;

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void startRecord(@NonNull String path, @Nullable Listener listener) throws IOException {
        if (audioCodec == AudioCodec.G711) throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
        outputStream = new FileOutputStream(path);
        this.listener = listener;
        status = Status.STARTED;
        if (listener != null) listener.onStatusChange(status);
        if (sampleRate != -1 && channels != -1) init();
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void startRecord(@NonNull FileDescriptor fd, @Nullable Listener listener) throws IOException {
        if (audioCodec == AudioCodec.G711) throw new IOException("Unsupported AudioCodec: " + audioCodec.name());
        throw new IOException("FileDescriptor unsupported");
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopRecord() {
        status = Status.STOPPED;
        pauseMoment = 0;
        pauseTime = 0;
        sampleRate = -1;
        channels = -1;
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
                byte[] header = createAdtsHeader(info.size - info.offset);
                outputStream.write(header);
                byte[] data = new byte[byteBuffer.remaining()];
                byteBuffer.get(data);
                outputStream.write(data);
            }
        } catch (IllegalStateException | IllegalArgumentException | IOException e) {
            Log.i(TAG, "Write error", e);
        }
    }

    private byte[] createAdtsHeader(int length) {
        int frameLength = length + 7;
        byte[] adtsHeader = new byte[7];
        int sampleRateIndex = Arrays.asList(AudioSampleRates).indexOf(sampleRate);

        adtsHeader[0] = (byte) 0xFF; // Sync Word
        adtsHeader[1] = (byte) 0xF1; // MPEG-4, Layer (0), No CRC
        adtsHeader[2] = (byte) ((MediaCodecInfo.CodecProfileLevel.AACObjectLC - 1) << 6);
        adtsHeader[2] |= (((byte) sampleRateIndex) << 2);
        adtsHeader[2] |= (((byte) channels) >> 2);
        adtsHeader[3] = (byte) (((channels & 3) << 6) | ((frameLength >> 11) & 0x03));
        adtsHeader[4] = (byte) ((frameLength >> 3) & 0xFF);
        adtsHeader[5] = (byte) (((frameLength & 0x07) << 5) | 0x1f);
        adtsHeader[6] = (byte) 0xFC;
        return adtsHeader;
    }
}