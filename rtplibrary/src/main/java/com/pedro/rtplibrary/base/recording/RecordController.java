package com.pedro.rtplibrary.base.recording;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pedro.rtplibrary.base.recording.BaseRecordController;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface RecordController {

    void startRecord(@NonNull String path, @Nullable Listener listener) throws IOException;
    void startRecord(@NonNull FileDescriptor fd, @Nullable Listener listener) throws IOException;
    void stopRecord();
    void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo);
    void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo);
    void setVideoFormat(MediaFormat videoFormat, boolean isOnlyVideo);
    void setAudioFormat(MediaFormat audioFormat, boolean isOnlyVideo);
    void resetFormats();

    interface Listener {
        void onStatusChange(Status status);
    }

    enum Status {
        STARTED, STOPPED, RECORDING, PAUSED, RESUMED
    }
}
