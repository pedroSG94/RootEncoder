package com.pedro.rtplibrary.base;

import android.media.MediaCodec;
import android.media.MediaFormat;
import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import java.nio.ByteBuffer;

/**
 * Wrapper to stream only audio. It is under tests.
 *
 * Created by pedro on 10/07/18.
 */
public abstract class OnlyAudioBase implements GetAacData, GetMicrophoneData {

  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;

  public OnlyAudioBase() {
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
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
  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
    prepareAudioRtp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
        microphoneManager.getMaxInputSize());
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
    audioEncoder.start();
    microphoneManager.start();
    startStreamRtp(url);
  }

  public void reTry(long delay) {
    reConnect(delay);
  }

  protected abstract void stopStreamRtp();

  //re connection
  public abstract void setReTries(int reTries);

  public abstract boolean shouldRetry(String reason);

  protected abstract void reConnect(long delay);

  //cache control
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
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    streaming = false;
    stopStreamRtp();
    microphoneManager.stop();
    audioEncoder.stop();
  }

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
    getAacDataRtp(aacBuffer, info);
  }

  @Override
  public void inputPCMData(Frame frame) {
    audioEncoder.inputPCMData(frame);
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    //ignored because record is not implemented
  }
}
