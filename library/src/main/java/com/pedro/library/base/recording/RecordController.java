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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    void setAudioFormat(MediaFormat audioFormat, boolean isOnlyAudio);
    void resetFormats();

    interface Listener {
        void onStatusChange(Status status);
    }

    enum Status {
        STARTED, STOPPED, RECORDING, PAUSED, RESUMED
    }
}
