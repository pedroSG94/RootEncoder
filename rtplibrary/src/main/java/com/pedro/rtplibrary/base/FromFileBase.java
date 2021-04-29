package com.pedro.rtplibrary.base;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.decoder.AudioDecoder;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.encoder.input.decoder.LoopFileInterface;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.util.RecordController;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;

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
public abstract class FromFileBase
    implements GetVideoData, GetAacData, GetMicrophoneData, LoopFileInterface {

  private static final String TAG = "FromFileBase";
  private Context context;

  protected VideoEncoder videoEncoder;
  private AudioEncoder audioEncoder;
  private GlInterface glInterface;
  private boolean streaming = false;
  protected RecordController recordController;
  private FpsListener fpsListener = new FpsListener();

  private VideoDecoder videoDecoder;
  private AudioDecoder audioDecoder;

  private VideoDecoderInterface videoDecoderInterface;
  private AudioDecoderInterface audioDecoderInterface;

  private String videoPath, audioPath;
  protected boolean videoEnabled = false;
  private boolean audioEnabled = false;
  private final Object sync = new Object();
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
    this.context = context;
    glInterface = new OffScreenGlThread(context);
    glInterface.init();
    init(videoDecoderInterface, audioDecoderInterface);
  }

  public FromFileBase(OpenGlView openGlView, VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    context = openGlView.getContext();
    glInterface = openGlView;
    glInterface.init();
    init(videoDecoderInterface, audioDecoderInterface);
  }

  public FromFileBase(LightOpenGlView lightOpenGlView, VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    context = lightOpenGlView.getContext();
    glInterface = lightOpenGlView;
    glInterface.init();
    init(videoDecoderInterface, audioDecoderInterface);
  }

  private void init(VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    this.videoDecoderInterface = videoDecoderInterface;
    this.audioDecoderInterface = audioDecoderInterface;
    videoEncoder = new VideoEncoder(this);
    audioEncoder = new AudioEncoder(this);
    videoDecoder = new VideoDecoder(videoDecoderInterface, this);
    audioDecoder = new AudioDecoder(this, audioDecoderInterface, this);
    recordController = new RecordController();
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
   * @param filePath to video MP4 file.
   * @param bitRate H264 in bps.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareVideo(String filePath, int bitRate, int rotation, int avcProfile,
      int avcProfileLevel) throws IOException {
    videoPath = filePath;
    if (!videoDecoder.initExtractor(filePath)) return false;
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
            bitRate, rotation, 2, FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel);
    if (!result) return false;
    result = videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    videoEnabled = result;
    return result;
  }

  public boolean prepareVideo(String filePath, int bitRate, int rotation) throws IOException {
    return prepareVideo(filePath, bitRate, rotation, -1, -1);
  }

  public boolean prepareVideo(String filePath) throws IOException {
    return prepareVideo(filePath, 1200 * 1024, 0);
  }

  /**
   * @param filePath to video MP4 file.
   * @param bitRate AAC in kb.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareAudio(String filePath, int bitRate) throws IOException {
    audioPath = filePath;
    if (!audioDecoder.initExtractor(filePath)) return false;
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

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  /**
   * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
    if (videoEnabled) videoEncoder.setForce(forceVideo);
    if (audioEnabled) audioEncoder.setForce(forceAudio);
  }

  /**
   * Starts recording an MP4 video. Needs to be called while streaming.
   *
   * @param path Where file will be saved.
   * @throws IOException If initialized before a stream.
   */
  public void startRecord(@NonNull String path, @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
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
  public void startRecord(@NonNull final FileDescriptor fd, @Nullable RecordController.Listener listener) throws IOException {
    recordController.startRecord(fd, listener);
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      resetVideoEncoder(false);
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
      if (videoEnabled) resetVideoEncoder(true);
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

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void replaceView(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      replaceGlInterface(new OffScreenGlThread(context));
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void replaceView(OpenGlView openGlView) {
    replaceGlInterface(openGlView);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void replaceView(LightOpenGlView lightOpenGlView) {
    replaceGlInterface(lightOpenGlView);
  }

  /**
   * Replace glInterface used on fly. Ignored if you use SurfaceView or TextureView
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void replaceGlInterface(GlInterface glInterface) {
    if (this.glInterface != null && Build.VERSION.SDK_INT >= 18 && videoEnabled) {
      if (isStreaming() || isRecording()) {
        try {
          this.glInterface.removeMediaCodecSurface();
          this.glInterface.stop();
          this.glInterface = glInterface;
          if (!(glInterface instanceof OffScreenGlThread)) {
            glInterface.init();
          }
          prepareGlView();
          if (Build.VERSION.SDK_INT >= 23) {
            videoDecoder.changeOutputSurface(this.glInterface.getSurface());
          } else {
            double time = videoDecoder.getTime();
            videoDecoder.stop();
            videoDecoder = new VideoDecoder(videoDecoderInterface, this);
            if (!videoDecoder.initExtractor(videoPath)) {
              throw new IOException("fail to reset video file");
            }
            videoDecoder.prepareVideo(this.glInterface.getSurface());
            videoDecoder.start();
            videoDecoder.moveTo(time);
          }
        } catch (IOException e) {
          Log.e(TAG, "Error", e);
        }
      } else {
        this.glInterface = glInterface;
        this.glInterface.init();
      }
    }
  }

  private void prepareGlView() {
    if (glInterface != null) {
      if (glInterface instanceof OffScreenGlThread) {
        glInterface.init();
      }
      glInterface.setFps(videoEncoder.getFps());
      if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
        glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
      } else {
        glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
      }
      glInterface.setRotation(0);
      glInterface.start();
      if (videoEncoder.getInputSurface() != null) {
        videoDecoder.changeOutputSurface(this.glInterface.getSurface());
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
    }
  }

  private void resetVideoEncoder(boolean reset) {
    if (glInterface != null) {
      glInterface.removeMediaCodecSurface();
    }
    if (reset) videoEncoder.reset(); else videoEncoder.forceKeyFrame();
    if (glInterface != null) {
      glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
    } else {
      videoDecoder.reset(videoEncoder.getInputSurface());
    }
  }

  protected abstract void stopStreamRtp();

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
    if (videoEnabled) videoDecoder.setLoopMode(loopMode);
    if (audioEnabled) audioDecoder.setLoopMode(loopMode);
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
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
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

  @Override
  public void onReset(boolean isVideo) {
    synchronized (sync) {
      try {
        if (isVideo) {
          if (glInterface != null) {
            glInterface.removeMediaCodecSurface();
            glInterface.stop();
          }
          if (videoEnabled) {
            videoDecoder.stop();
            if (!videoDecoder.initExtractor(videoPath)) {
              throw new IOException("fail to reset video file");
            }
            prepareGlView();
            videoDecoder.start();
          }
        } else {
          if (audioEnabled) {
            audioDecoder.stop();
            if (!audioDecoder.initExtractor(audioPath)) {
              throw new IOException("fail to reset audio file");
            }
            audioDecoder.prepareAudio();
            audioDecoder.start();
          }
        }
      } catch (IOException e) {
        Log.e(TAG, "Error", e);
        if (isVideo) {
          videoDecoderInterface.onVideoDecoderFinished();
        } else {
          audioDecoderInterface.onAudioDecoderFinished();
        }
      }
    }
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
  public void onVideoFormat(MediaFormat mediaFormat) {
    recordController.setVideoFormat(mediaFormat);
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    recordController.recordAudio(aacBuffer, info);
    if (streaming) getAacDataRtp(aacBuffer, info);
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    recordController.setAudioFormat(mediaFormat, !videoEnabled);
  }

  @Override
  public void inputPCMData(Frame frame) {
    if (audioTrackPlayer != null) {
      audioTrackPlayer.write(frame.getBuffer(), frame.getOffset(), frame.getSize());
    }
    audioEncoder.inputPCMData(frame);
  }

  public abstract void setLogs(boolean enable);
}
