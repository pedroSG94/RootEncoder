/*
 * Copyright (C) 2023 pedroSG94.
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

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.EncoderErrorCallback;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.decoder.AudioDecoder;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.encoder.input.decoder.DecoderInterface;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
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
import com.pedro.library.view.OpenGlView;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wrapper to stream a MP4 file with H264 video codec. Only Video is streamed, no Audio.
 * Can be executed in background.
 *
 * API requirements:
 * API 18+.
 *
 * Created by pedro on 7/07/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class FromFileBase {

  private static final String TAG = "FromFileBase";

  protected VideoEncoder videoEncoder;
  private AudioEncoder audioEncoder;
  private GlInterface glInterface;
  private boolean streaming = false;
  protected BaseRecordController recordController;
  private final FpsListener fpsListener = new FpsListener();

  private VideoDecoder videoDecoder;
  private AudioDecoder audioDecoder;

  protected boolean videoEnabled = false;
  private boolean audioEnabled = false;
  private AudioTrack audioTrackPlayer;

  public FromFileBase(VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    init(videoDecoderInterface, audioDecoderInterface);
  }

  /**
   * OpenGl mode, necessary context.
   */
  public FromFileBase(Context context, VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    glInterface = new GlStreamInterface(context);
    init(videoDecoderInterface, audioDecoderInterface);
  }

  public FromFileBase(OpenGlView openGlView, VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    glInterface = openGlView;
    init(videoDecoderInterface, audioDecoderInterface);
  }

  private void init(VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    videoEncoder = new VideoEncoder(getVideoData);
    audioEncoder = new AudioEncoder(getAacData);
    videoDecoder = new VideoDecoder(videoDecoderInterface, decoderInterface);
    audioDecoder = new AudioDecoder(getMicrophoneData, audioDecoderInterface, decoderInterface);
    recordController = new AndroidMuxerRecordController();
  }

  /**
   * @param callback get fps while record or stream
   */
  public void setFpsListener(FpsListener.Callback callback) {
    fpsListener.setCallback(callback);
  }

  /**
   * @param filePath to video MP4 file.
   * @param bitRate H264 in bps.
   * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
   * @param level codec value from MediaCodecInfo.CodecProfileLevel class
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareVideo(String filePath, int bitRate, int rotation, int profile,
      int level) throws IOException {
    if (!videoDecoder.initExtractor(filePath)) return false;
    return finishPrepareVideo(bitRate, rotation, profile, level);
  }

  /**
   * @param fileDescriptor to video MP4 file.
   * @param bitRate H264 in bps.
   * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
   * @param level codec value from MediaCodecInfo.CodecProfileLevel class
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareVideo(FileDescriptor fileDescriptor, int bitRate, int rotation, int profile,
      int level) throws IOException {
    if (!videoDecoder.initExtractor(fileDescriptor)) return false;
    return finishPrepareVideo(bitRate, rotation, profile, level);
  }

  /**
   * @param uri Uri to video MP4 file.
   * @param bitRate H264 in bps.
   * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
   * @param level codec value from MediaCodecInfo.CodecProfileLevel class
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareVideo(Context context, Uri uri, int bitRate, int rotation, int profile,
      int level) throws IOException {
    if (!videoDecoder.initExtractor(context, uri, null)) return false;
    return finishPrepareVideo(bitRate, rotation, profile, level);
  }

  public boolean prepareVideo(String filePath, int bitRate, int rotation) throws IOException {
    return prepareVideo(filePath, bitRate, rotation, -1, -1);
  }

  public boolean prepareVideo(FileDescriptor fileDescriptor, int bitRate, int rotation) throws IOException {
    return prepareVideo(fileDescriptor, bitRate, rotation, -1, -1);
  }

  public boolean prepareVideo(String filePath) throws IOException {
    return prepareVideo(filePath, 1200 * 1024, 0);
  }

  public boolean prepareVideo(Context context, Uri uri, int bitRate, int rotation) throws IOException {
    return prepareVideo(context, uri, bitRate, rotation, -1, -1);
  }

  public boolean prepareVideo(Context context, Uri uri) throws IOException {
    return prepareVideo(context, uri, 1200 * 1024, 0);
  }

  public boolean prepareVideo(FileDescriptor fileDescriptor) throws IOException {
    return prepareVideo(fileDescriptor, 1200 * 1024, 0);
  }

  private boolean finishPrepareVideo(int bitRate, int rotation, int profile,  int level) {
    boolean result = videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), videoDecoder.getFps(),
            bitRate, rotation, 2, FormatVideoEncoder.SURFACE, profile, level);
    if (!result) return false;
    result = videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    videoEnabled = result;
    return result;
  }

  /**
   * @param filePath to audio file.
   * @param bitRate AAC in kb.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareAudio(String filePath, int bitRate) throws IOException {
    if (!audioDecoder.initExtractor(filePath)) return false;
    return finishPrepareAudio(bitRate);
  }

  /**
   * @param fileDescriptor to audio file.
   * @param bitRate AAC in kb.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareAudio(FileDescriptor fileDescriptor, int bitRate) throws IOException {
    if (!audioDecoder.initExtractor(fileDescriptor)) return false;
    return finishPrepareAudio(bitRate);
  }

  /**
   * @param uri Uri to audio file.
   * @param bitRate AAC in kb.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareAudio(Context context, Uri uri, int bitRate) throws IOException {
    if (!audioDecoder.initExtractor(context, uri, null)) return false;
    return finishPrepareAudio(bitRate);
  }

  private boolean finishPrepareAudio(int bitRate) {
    audioDecoder.prepareAudio();
    boolean result = audioEncoder.prepareAudioEncoder(bitRate, audioDecoder.getSampleRate(),
        audioDecoder.isStereo(), audioDecoder.getOutsize());
    prepareAudioRtp(audioDecoder.isStereo(), audioDecoder.getSampleRate());
    audioEnabled = result;
    return result;
  }

  public boolean isAudioDeviceEnabled() {
    return audioTrackPlayer != null
        && audioTrackPlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
  }

  /**
   * Set a callback to know errors related with Video/Audio encoders
   * @param encoderErrorCallback callback to use, null to remove
   */
  public void setEncoderErrorCallback(EncoderErrorCallback encoderErrorCallback) {
    videoEncoder.setEncoderErrorCallback(encoderErrorCallback);
    audioEncoder.setEncoderErrorCallback(encoderErrorCallback);
  }

  public void playAudioDevice() {
    if (audioEnabled) {
      if (isAudioDeviceEnabled()) {
        audioTrackPlayer.stop();
      }
      int channel =
          audioDecoder.isStereo() ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
      int buffSize = AudioTrack.getMinBufferSize(audioDecoder.getSampleRate(), channel,
          AudioFormat.ENCODING_PCM_16BIT);
      audioTrackPlayer =
          new AudioTrack(AudioManager.STREAM_MUSIC, audioDecoder.getSampleRate(), channel,
              AudioFormat.ENCODING_PCM_16BIT, buffSize, AudioTrack.MODE_STREAM);
      audioTrackPlayer.play();
    }
  }

  public void stopAudioDevice() {
    if (audioEnabled && isAudioDeviceEnabled()) {
      audioTrackPlayer.stop();
      audioTrackPlayer = null;
    }
  }

  public boolean prepareAudio(String filePath) throws IOException {
    return prepareAudio(filePath, 64 * 1024);
  }

  public boolean prepareAudio(Context context, Uri uri) throws IOException {
    return prepareAudio(context, uri, 64 * 1024);
  }

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  /**
   * @param codecTypeVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   * @param codecTypeAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void forceCodecType(CodecUtil.CodecType codecTypeVideo, CodecUtil.CodecType codecTypeAudio) {
    if (videoEnabled) videoEncoder.forceCodecType(codecTypeVideo);
    if (audioEnabled) audioEncoder.forceCodecType(codecTypeAudio);
  }

  /**
   * Starts recording a MP4 video.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  public void startRecord(@NonNull String path, @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
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
  public void startRecord(@NonNull final FileDescriptor fd, @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(fd, listener);
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      requestKeyFrame();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull final FileDescriptor fd) throws IOException{
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
   * Need be called after @prepareVideo.
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
    } else {
      if (videoEnabled) requestKeyFrame();
    }
    startStreamRtp(url);
  }

  private void startEncoders() {
    if (videoEnabled) videoEncoder.start();
    if (audioTrackPlayer != null) audioTrackPlayer.play();
    if (audioEnabled) audioEncoder.start();
    if (videoEnabled) prepareGlView();
    if (videoEnabled) videoDecoder.start();
    if (audioEnabled) audioDecoder.start();
  }

  public void replaceView(Context context) {
    replaceGlInterface(new GlStreamInterface(context));
  }

  public void replaceView(OpenGlView openGlView) {
    replaceGlInterface(openGlView);
  }

  /**
   * Replace glInterface used on fly. Ignored if you use SurfaceView or TextureView
   */
  private void replaceGlInterface(GlInterface glInterface) {
    if (this.glInterface != null && videoEnabled) {
      if (isStreaming() || isRecording()) {
        videoDecoder.pauseRender();
        this.glInterface.removeMediaCodecSurface();
        this.glInterface.stop();
        this.glInterface = glInterface;
        prepareGlView();
        videoDecoder.resumeRender();
      } else {
        this.glInterface = glInterface;
      }
    }
  }

  private void prepareGlView() {
    if (glInterface != null) {
      int w = videoEncoder.getWidth();
      int h = videoEncoder.getHeight();
      boolean isPortrait = false;
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        h = videoEncoder.getWidth();
        w = videoEncoder.getHeight();
        isPortrait = true;
      }
      glInterface.setEncoderSize(w, h);
      if (glInterface instanceof GlStreamInterface glStreamInterface) {
        glStreamInterface.setPreviewResolution(w, h);
        glStreamInterface.setIsPortrait(isPortrait);
      }
      glInterface.setRotation(0);
      glInterface.start();
      if (videoEncoder.getInputSurface() != null) {
        videoDecoder.changeOutputSurface(this.glInterface.getSurface());
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
    }
  }

  public void requestKeyFrame() {
    if (videoEncoder.isRunning()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        videoEncoder.requestKeyframe();
      } else {
        if (glInterface != null) {
          glInterface.removeMediaCodecSurface();
        }
        videoEncoder.reset();
        if (glInterface != null) {
          glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        } else {
          videoDecoder.changeOutputSurface(videoEncoder.getInputSurface());
        }
      }
    }
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
      if (glInterface != null) {
        glInterface.removeMediaCodecSurface();
        glInterface.stop();
      }
      if (videoEnabled) videoDecoder.stop();
      if (audioEnabled) audioDecoder.stop();
      if (audioEnabled && isAudioDeviceEnabled()) {
        audioTrackPlayer.stop();
      }
      audioTrackPlayer = null;
      if (videoEnabled) videoEncoder.stop();
      if (audioEnabled) audioEncoder.stop();
      recordController.resetFormats();
      videoEnabled = false;
      audioEnabled = false;
    }
  }

  /**
   * If you want reproduce video in loop.
   * This mode clear all effects or stream object when video is restarted. TODO: No clear it.
   *
   * @param loopMode true in loop, false stop stream when video finish.
   */
  public void setLoopMode(boolean loopMode) {
    videoDecoder.setLoopMode(loopMode);
    audioDecoder.setLoopMode(loopMode);
  }

  public void reSyncFile() {
    if (isStreaming() && videoEnabled && audioEnabled) audioDecoder.moveTo(videoDecoder.getTime());
  }

  public GlInterface getGlInterface() {
    if (glInterface != null) {
      return glInterface;
    } else {
      throw new RuntimeException("You can't do it. You are not using Opengl");
    }
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
  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
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

  /**
   * @return return time in second. 0 if no streaming
   */
  public double getVideoTime() {
    return videoDecoder.getTime();
  }

  /**
   * @return return time in seconds. 0 if no streaming
   */
  public double getAudioTime() {
    return audioDecoder.getTime();
  }

  /**
   * @return return duration in seconds. 0 if no streaming
   */
  public double getVideoDuration() {
    return videoDecoder.getDuration();
  }

  /**
   * @return return duration in seconds. 0 if no streaming
   */
  public double getAudioDuration() {
    return audioDecoder.getDuration();
  }

  /**
   * Working but it is too slow. You need wait few seconds after call it to continue :(
   *
   * @param time second to move.
   */
  public void moveTo(double time) {
    if (videoEnabled) videoDecoder.moveTo(time);
    if (audioEnabled) audioDecoder.moveTo(time);
  }

  protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  public void setRecordController(BaseRecordController recordController) {
    if (!isRecording()) this.recordController = recordController;
  }

  private final GetMicrophoneData getMicrophoneData = frame -> {
    if (audioTrackPlayer != null) {
      audioTrackPlayer.write(frame.getBuffer(), frame.getOffset(), frame.getSize());
    }
    audioEncoder.inputPCMData(frame);
  };

  private final GetAacData getAacData = new GetAacData() {
    @Override
    public void getAacData(@NonNull ByteBuffer aacBuffer, @NonNull MediaCodec.BufferInfo info) {
      recordController.recordAudio(aacBuffer, info);
      if (streaming) getAacDataRtp(aacBuffer, info);
    }

    @Override
    public void onAudioFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setAudioFormat(mediaFormat, !videoEnabled);
    }
  };

  private final GetVideoData getVideoData = new GetVideoData() {
    @Override
    public void onSpsPpsVps(@NonNull ByteBuffer sps, @Nullable ByteBuffer pps, @Nullable ByteBuffer vps) {
      onSpsPpsVpsRtp(sps.duplicate(),  pps != null ? pps.duplicate(): null, vps != null ? vps.duplicate() : null);
    }

    @Override
    public void getVideoData(@NonNull ByteBuffer h264Buffer, @NonNull MediaCodec.BufferInfo info) {
      fpsListener.calculateFps();
      recordController.recordVideo(h264Buffer, info);
      if (streaming) getH264DataRtp(h264Buffer, info);
    }

    @Override
    public void onVideoFormat(@NonNull MediaFormat mediaFormat) {
      recordController.setVideoFormat(mediaFormat, !audioEnabled);
    }
  };

  private final DecoderInterface decoderInterface = new DecoderInterface() {

    private int trackFinished = 0;

    @Override
    public void onLoop() {
      int maxTracks = 0;
      if (audioEnabled) maxTracks++;
      if (videoEnabled) maxTracks++;
      trackFinished++;
      if (trackFinished >= maxTracks) {
        reSyncFile();
        trackFinished = 0;
      }
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
