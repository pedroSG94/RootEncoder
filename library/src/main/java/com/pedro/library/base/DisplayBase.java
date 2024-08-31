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

package com.pedro.library.base;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

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

import com.pedro.common.AudioCodec;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.EncoderErrorCallback;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAudioData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.library.base.recording.BaseRecordController;
import com.pedro.library.base.recording.RecordController;
import com.pedro.library.util.AndroidMuxerRecordController;
import com.pedro.library.util.FpsListener;
import com.pedro.library.util.streamclient.StreamBaseClient;
import com.pedro.library.view.GlInterface;
import com.pedro.library.view.GlStreamInterface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

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
public abstract class DisplayBase {

  private GlInterface glInterface;
  private MediaProjection mediaProjection;
  private final MediaProjectionManager mediaProjectionManager;
  protected VideoEncoder videoEncoder;
  private MicrophoneManager microphoneManager;
  private AudioEncoder audioEncoder;
  private boolean streaming = false;
  protected SurfaceView surfaceView;
  private VirtualDisplay virtualDisplay;
  private int dpi = 320;
  private int resultCode = -1;
  private Intent data;
  private MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() { };
  protected BaseRecordController recordController;
  private final FpsListener fpsListener = new FpsListener();
  private boolean videoInitialized = false;
  private boolean audioInitialized = false;

  public DisplayBase(Context context, boolean useOpengl) {
    if (useOpengl) {
      glInterface = new GlStreamInterface(context);
    }
    mediaProjectionManager =
        ((MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE));
    this.surfaceView = null;
    videoEncoder = new VideoEncoder(getVideoData);
    audioEncoder = new AudioEncoder(getAudioData);
    //Necessary use same thread to read input buffer and encode it with internal audio or audio is choppy.
    setMicrophoneMode(MicrophoneMode.SYNC);
    recordController = new AndroidMuxerRecordController();
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
        audioEncoder = new AudioEncoder(getAudioData);
        audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
        break;
      case ASYNC:
        microphoneManager = new MicrophoneManager(getMicrophoneData);
        audioEncoder = new AudioEncoder(getAudioData);
        break;
    }
  }

  /**
   * Set a callback to know errors related with Video/Audio encoders
   * @param encoderErrorCallback callback to use, null to remove
   */
  public void setEncoderErrorCallback(EncoderErrorCallback encoderErrorCallback) {
    videoEncoder.setEncoderErrorCallback(encoderErrorCallback);
    audioEncoder.setEncoderErrorCallback(encoderErrorCallback);
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
   * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
   * @param level codec value from MediaCodecInfo.CodecProfileLevel class
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   */
  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation, int dpi,
      int profile, int level, int iFrameInterval) {
    this.dpi = dpi;
    videoInitialized =
        videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, iFrameInterval,
            FormatVideoEncoder.SURFACE, profile, level);
    if (glInterface != null) {
      int w = width;
      int h = height;
      boolean isPortrait = false;
      if (rotation == 90 || rotation == 270) {
        h = width;
        w = height;
        isPortrait = true;
      }
      glInterface.setEncoderSize(w, h);
      if (glInterface instanceof GlStreamInterface glStreamInterface) {
        glStreamInterface.setPreviewResolution(w, h);
        glStreamInterface.setIsPortrait(isPortrait);
      }
    }
    return videoInitialized;
  }

  public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation, int dpi) {
    return prepareVideo(width, height, fps, bitrate, rotation, dpi, -1, -1, 2);
  }

  public boolean prepareVideo(int width, int height, int bitrate) {
    return prepareVideo(width, height, 30, bitrate, 0, 320);
  }

  protected abstract void onAudioInfoImp(boolean isStereo, int sampleRate);

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
    onAudioInfoImp(isStereo, sampleRate);
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
    mediaProjection.registerCallback(mediaProjectionCallback, null);
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
    onAudioInfoImp(isStereo, sampleRate);
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
   * @param codecTypeVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   * @param codecTypeAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void forceCodecType(CodecUtil.CodecType codecTypeVideo, CodecUtil.CodecType codecTypeAudio) {
    videoEncoder.forceCodecType(codecTypeVideo);
    audioEncoder.forceCodecType(codecTypeAudio);
  }

  /**
   * Starts recording a MP4 video.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  public void startRecord(@NonNull String path, @Nullable RecordController.Listener listener)
      throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders(resultCode, data, mediaProjectionCallback);
    } else if (videoEncoder.isRunning()) {
      requestKeyFrame();
    }
  }

  public void startRecord(@NonNull final String path) throws IOException {
    startRecord(path, null);
  }

  /**
   * Starts recording a MP4 video.
   *
   * @param fd Where the file will be saved.
   * @throws IOException If initialized before a stream.
   */
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd,
      @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(fd, listener);
    if (!streaming) {
      startEncoders(resultCode, data, mediaProjectionCallback);
    } else if (videoEncoder.isRunning()) {
      requestKeyFrame();
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

  protected abstract void startStreamImp(String url);

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

  public void setMediaProjectionCallback(MediaProjection.Callback mediaProjectionCallback) {
    if (videoInitialized || audioInitialized) {
      throw new RuntimeException("You need to set MediaProjection callback before prepareVideo and prepareAudio");
    }
    this.mediaProjectionCallback = mediaProjectionCallback != null
            ? mediaProjectionCallback
            : new MediaProjection.Callback() { };
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
      startEncoders(resultCode, data, mediaProjectionCallback);
    } else {
      requestKeyFrame();
    }
    startStreamImp(url);
  }

  private void startEncoders(int resultCode, Intent data, MediaProjection.Callback mediaProjectionCallback) {
    if (data == null) {
      throw new RuntimeException("You need send intent data before startRecord or startStream");
    }
    long startTs = System.nanoTime() / 1000;
    videoEncoder.start(startTs);
    if (audioInitialized) audioEncoder.start(startTs);
    if (glInterface != null) {
      glInterface.start();
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    }
    Surface surface =
        (glInterface != null) ? glInterface.getSurface() : videoEncoder.getInputSurface();
    if (mediaProjection == null) {
      mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
    }
    mediaProjection.registerCallback(mediaProjectionCallback, null);
    VirtualDisplay.Callback callback = new VirtualDisplay.Callback() { };
    if (glInterface != null && videoEncoder.getRotation() == 90
        || videoEncoder.getRotation() == 270) {
      virtualDisplay = mediaProjection.createVirtualDisplay("Stream Display", videoEncoder.getHeight(),
          videoEncoder.getWidth(), dpi, 0, surface, callback, null);
    } else {
      virtualDisplay = mediaProjection.createVirtualDisplay("Stream Display", videoEncoder.getWidth(),
              videoEncoder.getHeight(), dpi, 0, surface, callback, null);
    }
    if (audioInitialized) microphoneManager.start();
  }

  public void requestKeyFrame() {
    if (videoEncoder.isRunning()) {
      videoEncoder.requestKeyframe();
    }
  }

  protected abstract void stopStreamImp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    if (streaming) {
      streaming = false;
      stopStreamImp();
    }
    if (!recordController.isRecording()) {
      if (audioInitialized) microphoneManager.stop();
      if (mediaProjection != null) {
        mediaProjection.stop();
        mediaProjection = null;
      }
      if (glInterface != null) {
        glInterface.removeMediaCodecSurface();
        glInterface.stop();
      }
      if (virtualDisplay != null) {
        virtualDisplay.release();
      }
      videoEncoder.stop();
      audioEncoder.stop();
      data = null;
      recordController.resetFormats();
    }
  }

  public GlInterface getGlInterface() {
    if (glInterface != null) {
      return glInterface;
    } else {
      throw new RuntimeException("You can't do it. You are not using Opengl");
    }
  }

  /**
   * Set a custom size of audio buffer input.
   * If you set 0 or less you can disable it to use library default value.
   * Must be called before of prepareAudio method.
   *
   * @param size in bytes. Recommended multiple of 1024 (2048, 4096, 8196, etc)
   */
  public void setAudioMaxInputSize(int size) {
    microphoneManager.setMaxInputSize(size);
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
   * Force stream to work with fps selected in prepareVideo method. Must be called before prepareVideo.
   * This is not recommend because could produce fps problems.
   *
   * @param enabled true to enabled, false to disable, disabled by default.
   */
  public void forceFpsLimit(boolean enabled) {
    int fps = enabled ? videoEncoder.getFps() : 0;
    videoEncoder.setForceFps(fps);
    if (glInterface != null) glInterface.forceFpsLimit(fps);
  }

  public boolean resetVideoEncoder() {
    if (glInterface != null) {
      glInterface.removeMediaCodecSurface();
      boolean result = videoEncoder.reset();
      if (!result) return false;
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    } else {
      boolean result = videoEncoder.reset();
      if (!result) return false;
      if (virtualDisplay != null) virtualDisplay.setSurface(videoEncoder.getInputSurface());
    }
    return true;
  }

  public boolean resetAudioEncoder() {
    return audioEncoder.reset();
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

  protected abstract void getAudioDataImp(ByteBuffer audioBuffer, MediaCodec.BufferInfo info);

  protected abstract void onVideoInfoImp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  protected abstract void getVideoDataImp(ByteBuffer videoBuffer, MediaCodec.BufferInfo info);

  public void setRecordController(BaseRecordController recordController) {
    if (!isRecording()) this.recordController = recordController;
  }

  private final GetMicrophoneData getMicrophoneData = frame -> {
    audioEncoder.inputPCMData(frame);
  };

  private final GetAudioData getAudioData = new GetAudioData() {
    @Override
    public void getAudioData(@NonNull ByteBuffer audioBuffer, @NonNull MediaCodec.BufferInfo info) {
      recordController.recordAudio(audioBuffer, info);
      if (streaming) getAudioDataImp(audioBuffer, info);
    }

    @Override
    public void onAudioFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setAudioFormat(mediaFormat);
    }
  };

  private final GetVideoData getVideoData = new GetVideoData() {
    @Override
    public void onVideoInfo(@NonNull ByteBuffer sps, @Nullable ByteBuffer pps, @Nullable ByteBuffer vps) {
      onVideoInfoImp(sps.duplicate(),  pps != null ? pps.duplicate(): null, vps != null ? vps.duplicate() : null);
    }

    @Override
    public void getVideoData(@NonNull ByteBuffer videoBuffer, @NonNull MediaCodec.BufferInfo info) {
      fpsListener.calculateFps();
      recordController.recordVideo(videoBuffer, info);
      if (streaming) getVideoDataImp(videoBuffer, info);
    }

    @Override
    public void onVideoFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setVideoFormat(mediaFormat, !audioInitialized);
    }
  };

  public abstract StreamBaseClient getStreamClient();

  public void setVideoCodec(VideoCodec codec) {
    setVideoCodecImp(codec);
    recordController.setVideoCodec(codec);
    String type = switch (codec) {
      case H264 -> CodecUtil.H264_MIME;
      case H265 -> CodecUtil.H265_MIME;
      case AV1 -> CodecUtil.AV1_MIME;
    };
    videoEncoder.setType(type);
  }

  public void setAudioCodec(AudioCodec codec) {
    setAudioCodecImp(codec);
    recordController.setAudioCodec(codec);
    String type = switch (codec) {
      case G711 -> CodecUtil.G711_MIME;
      case AAC -> CodecUtil.AAC_MIME;
      case OPUS -> CodecUtil.OPUS_MIME;
    };
    audioEncoder.setType(type);
  }

  protected abstract void setVideoCodecImp(VideoCodec codec);
  protected abstract void setAudioCodecImp(AudioCodec codec);
}

