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

package com.pedro.encoder.input.audio;

import android.os.HandlerThread;
import android.util.Log;

import com.pedro.encoder.Frame;
import com.pedro.encoder.GetFrame;

import java.nio.ByteBuffer;

/**
 * Similar to MicrophoneManager but samples are not read automatically.
 * The owner must manually call read(...) as often as samples are needed.
 */
public class MicrophoneManagerManual extends MicrophoneManager implements GetFrame {

  private final String TAG = "MicMM";

  public MicrophoneManagerManual() {
    super(null);
  }

  /**
   * Start record and get data
   */
  @Override
  public synchronized void start() {
    init();
  }

  private void init() {
    if (audioRecord != null) {
      audioRecord.startRecording();
      running = true;
      Log.i(TAG, "Microphone started");
    } else {
      Log.e(TAG, "Error starting, microphone was stopped or not created, "
          + "use createMicrophone() before start()");
    }
  }

  /**
   * Call when you need mic samples.
   * This method will block until numBytes worth of samples are ready.
   */
  public int read(ByteBuffer directBuffer, int numBytes) {
    directBuffer.rewind();
    // write to the buffer and return number of bytes written.
    return audioRecord.read(directBuffer, numBytes);
  }

  /**
   * Stop and release microphone
   */
  public synchronized void stop() {
    // handlerThread must not be null, else the stop impl will throw
    handlerThread = new HandlerThread("nothing");
    super.stop();
  }

  public GetFrame getGetFrame() {
    return this;
  }

  @Override
  public Frame getInputFrame() {
    return read();
  }
}
