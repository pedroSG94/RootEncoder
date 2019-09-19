package com.pedro.encoder.input.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.pedro.encoder.Frame;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 */

public class MicrophoneManager {

  private final String TAG = "MicrophoneManager";
  private static final int BUFFER_SIZE = 4096;
  private AudioRecord audioRecord;
  private GetMicrophoneData getMicrophoneData;
  private ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
  private byte[] pcmBufferMuted = new byte[BUFFER_SIZE];
  private boolean running = false;
  private boolean created = false;

  //default parameters for microphone
  private int sampleRate = 32000; //hz
  private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
  private int channel = AudioFormat.CHANNEL_IN_STEREO;
  private boolean muted = false;
  private AudioPostProcessEffect audioPostProcessEffect;
  private HandlerThread handlerThread;

  public MicrophoneManager(GetMicrophoneData getMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData;
  }

  /**
   * Create audio record
   */
  public void createMicrophone() {
    createMicrophone(sampleRate, true, false, false);
    Log.i(TAG, "Microphone created, " + sampleRate + "hz, Stereo");
  }

  /**
   * Create audio record with params
   */
  public void createMicrophone(int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    this.sampleRate = sampleRate;
    if (!isStereo) channel = AudioFormat.CHANNEL_IN_MONO;
    audioRecord =
        new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, channel, audioFormat,
            getPcmBufferSize());
    audioPostProcessEffect = new AudioPostProcessEffect(audioRecord.getAudioSessionId());
    if (echoCanceler) audioPostProcessEffect.enableEchoCanceler();
    if (noiseSuppressor) audioPostProcessEffect.enableNoiseSuppressor();
    String chl = (isStereo) ? "Stereo" : "Mono";
    Log.i(TAG, "Microphone created, " + sampleRate + "hz, " + chl);
    created = true;
  }

  /**
   * Start record and get data
   */
  public synchronized void start() {
    init();
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    handler.post(new Runnable() {
      @Override
      public void run() {
        while (running) {
          Frame frame = read();
          if (frame != null) {
            getMicrophoneData.inputPCMData(frame);
          } else {
            running = false;
          }
        }
      }
    });
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

  public void mute() {
    muted = true;
  }

  public void unMute() {
    muted = false;
  }

  public boolean isMuted() {
    return muted;
  }

  /**
   * @return Object with size and PCM buffer data
   */
  private Frame read() {
    pcmBuffer.rewind();
    int size = audioRecord.read(pcmBuffer, pcmBuffer.remaining());
    if (size <= 0) {
      return null;
    }
    return new Frame(muted ? pcmBufferMuted : pcmBuffer.array(),
        muted ? 0 : pcmBuffer.arrayOffset(), size);
  }

  /**
   * Stop and release microphone
   */
  public synchronized void stop() {
    running = false;
    created = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      handlerThread.quitSafely();
    } else {
      handlerThread.quit();
    }
    if (audioRecord != null) {
      audioRecord.setRecordPositionUpdateListener(null);
      audioRecord.stop();
      audioRecord.release();
      audioRecord = null;
    }
    if (audioPostProcessEffect != null) {
      audioPostProcessEffect.releaseEchoCanceler();
      audioPostProcessEffect.releaseNoiseSuppressor();
    }
    Log.i(TAG, "Microphone stopped");
  }

  /**
   * Get PCM buffer size
   */
  private int getPcmBufferSize() {
    int pcmBufSize =
        AudioRecord.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT);
    return pcmBufSize * 5;
  }

  public int getMaxInputSize() {
    return BUFFER_SIZE;
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public int getAudioFormat() {
    return audioFormat;
  }

  public int getChannel() {
    return channel;
  }

  public boolean isRunning() {
    return running;
  }

  public boolean isCreated() {
    return created;
  }
}
