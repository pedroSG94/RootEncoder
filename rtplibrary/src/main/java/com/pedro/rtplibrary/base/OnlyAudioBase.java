package com.pedro.rtplibrary.base;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.rtplibrary.util.RecordController;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wrapper to stream only audio. It is under tests.
 *
 * Created by pedro on 10/07/18.
 */
public abstract class OnlyAudioBase implements GetAacData, GetMicrophoneData {

  protected RecordController recordController;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;

  public OnlyAudioBase() {
    setMicrophoneMode(MicrophoneMode.ASYNC);
    recordController = new RecordController();
  }

  /**
   * Must be called before prepareAudio.
   *
   * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
   * SYNC using same thread. This mode could solve choppy audio or audio frame discarded.
   * ASYNC using other thread.
   */
  public void setMicrophoneMode(MicrophoneMode microphoneMode) {
    switch (microphoneMode) {
      case SYNC:
        microphoneManager = new MicrophoneManagerManual();
        audioEncoder = new AudioEncoder(this);
        audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
        break;
      case ASYNC:
        microphoneManager = new MicrophoneManager(this);
        audioEncoder = new AudioEncoder(this);
        break;
    }
  }

  /**
   * Set an audio effect modifying microphone's PCM buffer.
   */
  public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
    microphoneManager.setCustomAudioEffect(customAudioEffect);
  }

  /**
   * Basic auth developed to work with Wowza. No tested with other server
   *
   * @param user auth.
   * @param password auth.
   */
  public abstract void setAuthorization(String user, String password);

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  /**
   * Call this method before use @startStream. If not you will do a stream without audio.
   *
   * @param bitrate AAC in kb.
   * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
   * @param isStereo true if you want Stereo audio (2 audio channels), false if you want Mono audio
   * (1 audio channel).
   * @param echoCanceler true enable echo canceler, false disable.
   * @param noiseSuppressor true enable noise suppressor, false  disable.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio(int audioSource, int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    if (!microphoneManager.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)) {
      return false;
    }
    prepareAudioRtp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
        microphoneManager.getMaxInputSize());
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    return prepareAudio(MediaRecorder.AudioSource.DEFAULT, bitrate, sampleRate, isStereo, echoCanceler,
        noiseSuppressor);
  }

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
    return prepareAudio(bitrate, sampleRate, isStereo, false, false);
  }

  /**
   * Same to call:
   * prepareAudio(64 * 1024, 32000, true, false, false);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a AAC encoder).
   */
  public boolean prepareAudio() {
    return prepareAudio(64 * 1024, 32000, true, false, false);
  }

  /**
   * Starts recording an MP4 video. Needs to be called while streaming.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(String path, RecordController.Listener listener) throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(final String path) throws IOException {
    startRecord(path, null);
  }

  /**
   * Starts recording an MP4 video. Needs to be called while streaming.
   *
   * @param fd Where the file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd,
      @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(fd, listener);
    if (!streaming) {
      startEncoders();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd) throws IOException {
    startRecord(fd, null);
  }

  /**
   * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    recordController.stopRecord();
    if (!streaming) stopStream();
  }

  protected abstract void startStreamRtp(String url);

  /**
   * Need be called after @prepareVideo or/and @prepareAudio.
   *
   * @param url of the stream like:
   * protocol://ip:port/application/streamName
   *
   * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94
   * RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
   * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94
   * RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
   */
  public void startStream(String url) {
    streaming = true;
    if (!recordController.isRunning()) {
      startEncoders();
    }
    startStreamRtp(url);
  }

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    streaming = false;
    stopStreamRtp();
    if (!recordController.isRecording()) {
      microphoneManager.stop();
      audioEncoder.stop();
      recordController.resetFormats();
    }
  }

  private void startEncoders() {
    audioEncoder.start();
    microphoneManager.start();
  }

  protected abstract void stopStreamRtp();

  /**
   * Get record state.
   *
   * @return true if recording, false if not recoding.
   */
  public boolean isRecording() {
    return recordController.isRunning();
  }

  public void pauseRecord() {
    recordController.pauseRecord();
  }

  public void resumeRecord() {
    recordController.resumeRecord();
  }

  public RecordController.Status getRecordStatus() {
    return recordController.getStatus();
  }

  /**
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  public boolean reTry(long delay, String reason, @Nullable String backupUrl) {
    boolean result = shouldRetry(reason);
    if (result) {
      reConnect(delay, backupUrl);
    }
    return result;
  }

  public boolean reTry(long delay, String reason) {
    return reTry(delay, reason, null);
  }

  protected abstract boolean shouldRetry(String reason);

  public abstract void setReTries(int reTries);

  protected abstract void reConnect(long delay, @Nullable String backupUrl);

  //cache control
  public abstract boolean hasCongestion();

  public abstract void resizeCache(int newSize) throws RuntimeException;

  public abstract int getCacheSize();

  public abstract long getSentAudioFrames();

  public abstract long getSentVideoFrames();

  public abstract long getDroppedAudioFrames();

  public abstract long getDroppedVideoFrames();

  public abstract void resetSentAudioFrames();

  public abstract void resetSentVideoFrames();

  public abstract void resetDroppedAudioFrames();

  public abstract void resetDroppedVideoFrames();

  /**
   * Mute microphone, can be called before, while and after stream.
   */
  public void disableAudio() {
    microphoneManager.mute();
  }

  /**
   * Enable a muted microphone, can be called before, while and after stream.
   */
  public void enableAudio() {
    microphoneManager.unMute();
  }

  /**
   * Get mute state of microphone.
   *
   * @return true if muted, false if enabled
   */
  public boolean isAudioMuted() {
    return microphoneManager.isMuted();
  }

  /**
   * Get stream state.
   *
   * @return true if streaming, false if not streaming.
   */
  public boolean isStreaming() {
    return streaming;
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      recordController.recordAudio(aacBuffer, info);
    }
    if (streaming) getAacDataRtp(aacBuffer, info);
  }

  @Override
  public void inputPCMData(Frame frame) {
    audioEncoder.inputPCMData(frame);
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    recordController.setAudioFormat(mediaFormat, true);
  }

  public abstract void setLogs(boolean enable);
}
