package com.pedro.rtplibrary.base;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceView;

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
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.util.RecordController;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.OffScreenGlThread;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

/**
 * Wrapper to stream display screen of your device and microphone.
 * Can be executed in background.
 *
 * API requirements:
 * API 21+.
 *
 * Created by pedro on 9/08/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class DisplayBase implements GetAacData, GetVideoData, GetMicrophoneData {

  private OffScreenGlThread glInterface;
  protected Context context;
  private MediaProjection mediaProjection;
  private MediaProjectionManager mediaProjectionManager;
  protected VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;
  protected SurfaceView surfaceView;
  private boolean videoEnabled = true;
  private int dpi = 320;
  private VirtualDisplay virtualDisplay;
  private int resultCode = -1;
  private Intent data;
  protected RecordController recordController;
  private FpsListener fpsListener = new FpsListener();
  private boolean audioInitialized = false;

  public DisplayBase(Context context, boolean useOpengl) {
    this.context = context;
    if (useOpengl) {
      glInterface = new OffScreenGlThread(context);
      glInterface.init();
    }
    mediaProjectionManager =
        ((MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE));
    this.surfaceView = null;
    videoEncoder = new VideoEncoder(this);
    audioEncoder = new AudioEncoder(this);
    //Necessary use same thread to read input buffer and encode it with internal audio or audio is choppy.
    setMicrophoneMode(MicrophoneMode.SYNC);
    recordController = new RecordController();
  }

  /**
   * Must be called before prepareAudio.
   *
   * @param microphoneMode mode to work accord to audioEncoder. By default SYNC:
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
   * @param callback get fps while record or stream
   */
  public void setFpsListener(FpsListener.Callback callback) {
    fpsListener.setCallback(callback);
  }

  /**
   * Basic auth developed to work with Wowza. No tested with other server
   *
   * @param user auth.
   * @param password auth.
   */
  public abstract void setAuthorization(String user, String password);

  /**
   * Call this method before use @startStream. If not you will do a stream without video.
   *
   * @param width resolution in px.
   * @param height resolution in px.
   * @param fps frames per second of the stream.
   * @param bitrate H264 in bps.
   * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
   * if you are streaming in Portrait). This only affect to stream result. This work rotating with
   * encoder.
   * NOTE: Rotation with encoder is silence ignored in some devices.
   * @param dpi of your screen device.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation, int dpi,
      int avcProfile, int avcProfileLevel, int iFrameInterval) {
    this.dpi = dpi;
    boolean result =
        videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, iFrameInterval,
            FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel);
    if (glInterface != null) {
      if (rotation == 90 || rotation == 270) {
        glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
    }
    return result;
  }

  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation, int dpi) {
    return prepareVideo(width, height, fps, bitrate, rotation, dpi, -1, -1, 2);
  }

  public boolean prepareVideo(int width, int height, int bitrate) {
    return prepareVideo(width, height, 30, bitrate, 0, 320);
  }

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
    audioInitialized = audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
        microphoneManager.getMaxInputSize());
    return audioInitialized;
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
   * Call this method before use @startStream for streaming internal audio only.
   *
   * @param bitrate AAC in kb.
   * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
   * @param isStereo true if you want Stereo audio (2 audio channels), false if you want Mono audio
   * (1 audio channel).
   * @see AudioPlaybackCaptureConfiguration.Builder#Builder(MediaProjection)
   */
  @RequiresApi(api = Build.VERSION_CODES.Q)
  public boolean prepareInternalAudio(int bitrate, int sampleRate, boolean isStereo,
      boolean echoCanceler, boolean noiseSuppressor) {
    if (mediaProjection == null) {
      mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
    }

    AudioPlaybackCaptureConfiguration config =
        new AudioPlaybackCaptureConfiguration.Builder(mediaProjection).addMatchingUsage(
            AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build();
    if (!microphoneManager.createInternalMicrophone(config, sampleRate, isStereo, echoCanceler,
        noiseSuppressor)) {
      return false;
    }
    prepareAudioRtp(isStereo, sampleRate);
    audioInitialized = audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
        microphoneManager.getMaxInputSize());
    return audioInitialized;
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  public boolean prepareInternalAudio(int bitrate, int sampleRate, boolean isStereo) {
    return prepareInternalAudio(bitrate, sampleRate, isStereo, false, false);
  }

  /**
   * Same to call:
   * rotation = 0;
   * if (Portrait) rotation = 90;
   * prepareVideo(640, 480, 30, 1200 * 1024, true, 0);
   *
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo() {
    return prepareVideo(640, 480, 30, 1200 * 1024, 0, 320);
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

  @RequiresApi(api = Build.VERSION_CODES.Q)
  public boolean prepareInternalAudio() {
    return prepareInternalAudio(64 * 1024, 32000, true);
  }

  /**
   * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
    videoEncoder.setForce(forceVideo);
    audioEncoder.setForce(forceAudio);
  }

  /**
   * Starts recording an MP4 video. Needs to be called while streaming.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  public void startRecord(@NonNull String path, @Nullable RecordController.Listener listener)
      throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders(resultCode, data);
    } else if (videoEncoder.isRunning()) {
      resetVideoEncoder(false);
    }
  }

  public void startRecord(@NonNull final String path) throws IOException {
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
      startEncoders(resultCode, data);
    } else if (videoEncoder.isRunning()) {
      resetVideoEncoder(false);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd) throws IOException {
    startRecord(fd, null);
  }

  /**
   * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
   */
  public void stopRecord() {
    recordController.stopRecord();
    if (!streaming) stopStream();
  }

  protected abstract void startStreamRtp(String url);

  /**
   * Create Intent used to init screen capture with startActivityForResult.
   *
   * @return intent to startActivityForResult.
   */
  public Intent sendIntent() {
    return mediaProjectionManager.createScreenCaptureIntent();
  }

  public void setIntentResult(int resultCode, Intent data) {
    this.resultCode = resultCode;
    this.data = data;
  }

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
      startEncoders(resultCode, data);
    } else {
      resetVideoEncoder(true);
    }
    startStreamRtp(url);
  }

  private void startEncoders(int resultCode, Intent data) {
    if (data == null) {
      throw new RuntimeException("You need send intent data before startRecord or startStream");
    }
    videoEncoder.start();
    if (audioInitialized) audioEncoder.start();
    if (glInterface != null) {
      glInterface.init();
      glInterface.setFps(videoEncoder.getFps());
      glInterface.start();
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    }
    Surface surface =
        (glInterface != null) ? glInterface.getSurface() : videoEncoder.getInputSurface();
    if (mediaProjection == null) {
      mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
    }
    if (glInterface != null && videoEncoder.getRotation() == 90
        || videoEncoder.getRotation() == 270) {
      virtualDisplay =
          mediaProjection.createVirtualDisplay("Stream Display", videoEncoder.getHeight(),
              videoEncoder.getWidth(), dpi, 0, surface, null, null);
    } else {
      virtualDisplay =
          mediaProjection.createVirtualDisplay("Stream Display", videoEncoder.getWidth(),
              videoEncoder.getHeight(), dpi, 0, surface, null, null);
    }
    if (audioInitialized) microphoneManager.start();
  }

  private void resetVideoEncoder(boolean reset) {
    virtualDisplay.setSurface(null);
    if (glInterface != null) {
      glInterface.removeMediaCodecSurface();
    }
    if (reset) videoEncoder.reset(); else videoEncoder.forceKeyFrame();
    if (glInterface != null) {
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    }
    virtualDisplay.setSurface(
        glInterface != null ? glInterface.getSurface() : videoEncoder.getInputSurface());
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    if (streaming) {
      streaming = false;
      stopStreamRtp();
    }
    if (!recordController.isRecording()) {
      if (audioInitialized) microphoneManager.stop();
      if (mediaProjection != null) {
        mediaProjection.stop();
      }
      if (glInterface != null) {
        glInterface.removeMediaCodecSurface();
        glInterface.stop();
      }
      videoEncoder.stop();
      audioEncoder.stop();
      data = null;
      recordController.resetFormats();
    }
  }

  /**
   * Retries to connect with the given delay. You can pass an optional backupUrl
   * if you'd like to connect to your backup server instead of the original one.
   * Given backupUrl replaces the original one.
   */
  public boolean reTry(long delay, String reason, @Nullable String backupUrl) {
    boolean result = shouldRetry(reason);
    if (result) {
      resetVideoEncoder(true);
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

  public GlInterface getGlInterface() {
    if (glInterface != null) {
      return glInterface;
    } else {
      throw new RuntimeException("You can't do it. You are not using Opengl");
    }
  }

  /**
   * Mute microphone, can be called before, while and after stream.
   */
  public void disableAudio() {
    if (audioInitialized) {
      microphoneManager.mute();
    }
  }

  /**
   * Enable a muted microphone, can be called before, while and after stream.
   */
  public void enableAudio() {
    if (audioInitialized) {
      microphoneManager.unMute();
    }
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
   * Get video camera state
   *
   * @return true if disabled, false if enabled
   */
  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public int getBitrate() {
    return videoEncoder.getBitRate();
  }

  public int getResolutionValue() {
    return videoEncoder.getWidth() * videoEncoder.getHeight();
  }

  public int getStreamWidth() {
    return videoEncoder.getWidth();
  }

  public int getStreamHeight() {
    return videoEncoder.getHeight();
  }

  /**
   * Set video bitrate of H264 in bits per second while stream.
   *
   * @param bitrate H264 in bits per second.
   */
  public void setVideoBitrateOnFly(int bitrate) {
    videoEncoder.setVideoBitrateOnFly(bitrate);
  }

  /**
   * Set limit FPS while stream. This will be override when you call to prepareVideo method.
   * This could produce a change in iFrameInterval.
   *
   * @param fps frames per second
   */
  public void setLimitFPSOnFly(int fps) {
    videoEncoder.setFps(fps);
  }

  /**
   * Get stream state.
   *
   * @return true if streaming, false if not streaming.
   */
  public boolean isStreaming() {
    return streaming;
  }

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

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    recordController.recordAudio(aacBuffer, info);
    if (streaming) getAacDataRtp(aacBuffer, info);
  }

  protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  @Override
  public void onSpsPps(ByteBuffer sps, ByteBuffer pps) {
    if (streaming) onSpsPpsVpsRtp(sps, pps, null);
  }

  @Override
  public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    if (streaming) onSpsPpsVpsRtp(sps, pps, vps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    fpsListener.calculateFps();
    recordController.recordVideo(h264Buffer, info);
    if (streaming) getH264DataRtp(h264Buffer, info);
  }

  @Override
  public void inputPCMData(Frame frame) {
    audioEncoder.inputPCMData(frame);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    recordController.setVideoFormat(mediaFormat);
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    recordController.setAudioFormat(mediaFormat);
  }

  public abstract void setLogs(boolean enable);
}

