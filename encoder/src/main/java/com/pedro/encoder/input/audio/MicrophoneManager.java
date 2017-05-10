package com.pedro.encoder.input.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.util.Log;
import com.pedro.encoder.audio.DataTaken;

/**
 * Created by pedro on 19/01/17.
 */

public class MicrophoneManager {

  private final String TAG = "MicrophoneManager";
  public static final int BUFFER_SIZE = 4096;
  private AudioRecord audioRecord;
  private GetMicrophoneData getMicrophoneData;
  private byte[] pcmBuffer = new byte[BUFFER_SIZE];
  private byte[] pcmBufferMuted = new byte[11];
  private boolean running = false;

  //default parameters for microphone
  private int sampleRate = 44100; //hz
  private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
  private int channel = AudioFormat.CHANNEL_IN_STEREO;
  private boolean muted = false;
  //echo canceler
  private AcousticEchoCanceler acousticEchoCanceler;
  private AutomaticGainControl automaticGainControl;

  public MicrophoneManager(GetMicrophoneData getMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData;
  }

  /**
   * Create audio record
   */
  public void createMicrophone() {
    createMicrophone(sampleRate, true);
    Log.i(TAG, "Microphone created, 44100hz, Stereo");
  }

  /**
   * Create audio record with params
   */
  public void createMicrophone(int sampleRate, boolean isStereo) {
    this.sampleRate = sampleRate;
    if (!isStereo) channel = AudioFormat.CHANNEL_IN_MONO;
    audioRecord =
        new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, channel, audioFormat,
            getPcmBufferSize() * 4);
    enableAudioEchoCanceler(audioRecord.getAudioSessionId());
    String chl = (isStereo) ? "Stereo" : "Mono";
    Log.i(TAG, "Microphone created, " + sampleRate + "hz, " + chl);
  }

  private void enableAudioEchoCanceler(int id) {
    if (AcousticEchoCanceler.isAvailable() && acousticEchoCanceler == null) {
      acousticEchoCanceler = AcousticEchoCanceler.create(id);
      acousticEchoCanceler.setEnabled(true);
    }
    if (AutomaticGainControl.isAvailable() && automaticGainControl == null) {
      automaticGainControl = AutomaticGainControl.create(id);
      automaticGainControl.setEnabled(true);
    }
  }

  private void disableAudioEchoCanceler(){
    if(acousticEchoCanceler != null){
      acousticEchoCanceler.setEnabled(false);
      acousticEchoCanceler.release();
      acousticEchoCanceler = null;
    }

    if(automaticGainControl != null){
      automaticGainControl.setEnabled(false);
      automaticGainControl.release();
      automaticGainControl = null;
    }
  }
  /**
   * Start record and get data
   */
  public void start() {
    init();
    new Thread(new Runnable() {
      @Override
      public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        while (running && !Thread.interrupted()) {
          DataTaken dataTaken = read();
          if (dataTaken != null) {
            getMicrophoneData.inputPcmData(dataTaken.getPcmBuffer(), dataTaken.getSize());
          } else {
            running = false;
          }
        }
      }
    }).start();
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
  private DataTaken read() {
    int size;
    if (muted) {
      size = audioRecord.read(pcmBufferMuted, 0, pcmBufferMuted.length);
    } else {
      size = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
    }
    if (size <= 0) {
      return null;
    }
    return new DataTaken(pcmBuffer, size);
  }

  /**
   * Stop and release microphone
   */
  public void stop() {
    running = false;
    if (audioRecord != null) {
      audioRecord.setRecordPositionUpdateListener(null);
      audioRecord.stop();
      audioRecord.release();
      audioRecord = null;
    }
    disableAudioEchoCanceler();
    Log.i(TAG, "Microphone stopped");
  }

  /**
   * Get PCM buffer size
   */
  private int getPcmBufferSize() {
    int pcmBufSize =
        AudioRecord.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT) + 8191;
    return pcmBufSize - (pcmBufSize % 8192);
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
}
