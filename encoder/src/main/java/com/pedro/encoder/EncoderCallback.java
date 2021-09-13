/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import androidx.annotation.NonNull;

/**
 * Created by pedro on 18/09/19.
 */
public interface EncoderCallback {
  void inputAvailable(@NonNull MediaCodec mediaCodec, int inBufferIndex)
      throws IllegalStateException;

  void outputAvailable(@NonNull MediaCodec mediaCodec, int outBufferIndex,
      @NonNull MediaCodec.BufferInfo bufferInfo) throws IllegalStateException;

  void formatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat);
}
