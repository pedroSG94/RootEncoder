package com.pedro.encoder.input.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import com.pedro.encoder.audio.DataTaken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by pedro on 19/01/17.
 */

public class MicrophoneManager {

  private final static Logger logger = LoggerFactory.getLogger(MicrophoneManager.class);
  public static final int BUFFER_SIZE = 4096;
  private AudioRecord audioRecord;
  private GetMicrophoneData getMicrophoneData;
  private byte[] pcmBuffer = new byte[BUFFER_SIZE];
  private byte[] pcmBufferMuted = new byte[11];
  private boolean running = false;
  private boolean created = false;

  //default parameters for microphone
  private int sampleRate = 44100; //hz
  private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
  private int channel = AudioFormat.CHANNEL_IN_STEREO;
  private boolean muted = false;
  private AudioPostProcessEffect audioPostProcessEffect;

  public MicrophoneManager(GetMicrophoneData getMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData;
  }

  /**
   * Create audio record
   */
  public void createMicrophone() {
    createMicrophone(sampleRate, true, false, false);
    logger.info("Microphone created, {}hz, Stereo", sampleRate);
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
            getPcmBufferSize() * 4);
    audioPostProcessEffect = new AudioPostProcessEffect(audioRecord.getAudioSessionId());
    if (echoCanceler) audioPostProcessEffect.enableEchoCanceler();
    if (noiseSuppressor) audioPostProcessEffect.enableNoiseSuppressor();
    String chl = (isStereo) ? "Stereo" : "Mono";
    logger.info("Microphone created, {}hz, {}", sampleRate, chl);
    created = true;
  }

  /**
   * Start record and get data
   */
  public void start() {
    if (isCreated()) {
      init();
      new Thread(new Runnable() {
        @Override
        public void run() {
          while (running && !Thread.interrupted()) {
            DataTaken dataTaken = read();
            if (dataTaken != null) {
              getMicrophoneData.inputPCMData(dataTaken.getPcmBuffer(), dataTaken.getSize());
            } else {
              running = false;
            }
          }
        }
      }).start();
    } else {
      logger.error("Microphone no created, MicrophoneManager not enabled");
    }
  }

  private void init() {
    if (audioRecord != null) {
      audioRecord.startRecording();
      running = true;
      logger.info("Microphone started");
    } else {
      logger.error("Error starting, microphone was stopped or not created, "
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
    created = false;
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
    logger.info("Microphone stopped");
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

  public boolean isCreated() {
    return created;
  }
}
