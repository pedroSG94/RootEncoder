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
