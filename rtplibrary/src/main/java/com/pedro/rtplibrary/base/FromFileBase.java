package com.pedro.rtplibrary.base;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
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
  private boolean videoEnabled = true;
  private RecordController recordController;
  private FpsListener fpsListener = new FpsListener();

  private VideoDecoder videoDecoder;
  private AudioDecoder audioDecoder;

  private VideoDecoderInterface videoDecoderInterface;
  private AudioDecoderInterface audioDecoderInterface;

  private String videoPath, audioPath;
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
   * @param bitRate H264 in kb.
   * @return true if success, false if you get a error (Normally because the encoder selected
   * doesn't support any configuration seated or your device hasn't a H264 encoder).
   * @throws IOException Normally file not found.
   */
  public boolean prepareVideo(String filePath, int bitRate, int rotation) throws IOException {
    videoPath = filePath;
    videoDecoder = new VideoDecoder(videoDecoderInterface, this);
    if (!videoDecoder.initExtractor(filePath)) return false;
    boolean hardwareRotation = glInterface == null;
    return videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
        bitRate, rotation, hardwareRotation, 2, FormatVideoEncoder.SURFACE);
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
    audioDecoder = new AudioDecoder(this, audioDecoderInterface, this);
    if (!audioDecoder.initExtractor(filePath)) return false;
    boolean result = audioEncoder.prepareAudioEncoder(bitRate, audioDecoder.getSampleRate(),
        audioDecoder.isStereo(), 0);
    prepareAudioRtp(audioDecoder.isStereo(), audioDecoder.getSampleRate());
    audioDecoder.prepareAudio();
    if (glInterface != null && !(glInterface instanceof OffScreenGlThread)) {
      int channel =
          audioDecoder.isStereo() ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
      int buffSize = AudioTrack.getMinBufferSize(audioDecoder.getSampleRate(), channel,
          AudioFormat.ENCODING_PCM_16BIT);
      audioTrackPlayer =
          new AudioTrack(AudioManager.STREAM_MUSIC, audioDecoder.getSampleRate(), channel,
              AudioFormat.ENCODING_PCM_16BIT, buffSize, AudioTrack.MODE_STREAM);
    }
    return result;
  }

  public boolean prepareAudio(String filePath) throws IOException {
    return prepareAudio(filePath, 64 * 1024);
  }

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  /**
   * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
   */
  public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
    videoEncoder.setForce(forceVideo);
    audioEncoder.setForce(forceAudio);
  }

  /**
   * Start record a MP4 video. Need be called while stream.
   *
   * @param path where file will be saved.
   * @throws IOException If you init it before start stream.
   */
  public void startRecord(String path, RecordController.Listener listener) throws IOException {
    recordController.startRecord(path, listener);
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      resetVideoEncoder();
    }
  }

  public void startRecord(final String path) throws IOException {
    startRecord(path, null);
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
      resetVideoEncoder();
    }
    startStreamRtp(url);
  }

  private void startEncoders() {
    videoEncoder.start();
    if (audioTrackPlayer != null) audioTrackPlayer.play();
    audioEncoder.start();
    prepareGlView();
    videoDecoder.start();
    audioDecoder.start();
  }

  private void prepareGlView() {
    if (glInterface != null) {
      if (glInterface instanceof OffScreenGlThread) {
        glInterface = new OffScreenGlThread(context);
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
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
      }
      videoDecoder.prepareVideo(glInterface.getSurface());
    } else {
      videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    }
  }

  private void resetVideoEncoder() {
    try {
      if (glInterface != null) {
        glInterface.removeMediaCodecSurface();
        glInterface.stop();
      }
      double time = videoDecoder.getTime();
      videoDecoder.stop();
      videoDecoder = new VideoDecoder(videoDecoderInterface, this);
      if (!videoDecoder.initExtractor(videoPath)) {
        throw new IOException("fail to reset video file");
      }
      videoEncoder.reset();
      prepareGlView();
      videoDecoder.start();
      videoDecoder.moveTo(time);
    } catch (IOException e) {
      Log.e(TAG, "Error", e);
    }
  }

  protected abstract void stopStreamRtp();

  public void reTry(long delay) {
    resetVideoEncoder();
    reConnect(delay);
  }

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
    if (streaming) {
      streaming = false;
      stopStreamRtp();
    }
    if (!recordController.isRecording()) {
      if (glInterface != null) {
        glInterface.removeMediaCodecSurface();
        glInterface.stop();
      }
      if (videoDecoder != null) videoDecoder.stop();
      if (audioDecoder != null) audioDecoder.stop();
      if (audioTrackPlayer != null
          && audioTrackPlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
        audioTrackPlayer.stop();
      }
      audioTrackPlayer = null;
      videoEncoder.stop();
      audioEncoder.stop();
      recordController.resetFormats();
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
    if (isStreaming()) audioDecoder.moveTo(videoDecoder.getTime());
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
   * Get video camera state
   *
   * @return true if disabled, false if enabled
   */
  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  /**
   * Set video bitrate of H264 in kb while stream.
   *
   * @param bitrate H264 in kb.
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
    return videoDecoder.getTime();
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
    videoDecoder.moveTo(time);
    audioDecoder.moveTo(time);
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
          videoDecoder.stop();
          videoDecoder = new VideoDecoder(videoDecoderInterface, this);
          if (!videoDecoder.initExtractor(videoPath)) {
            throw new IOException("fail to reset video file");
          }
          prepareGlView();
          videoDecoder.start();
        } else {
          audioDecoder.stop();
          audioDecoder = new AudioDecoder(this, audioDecoderInterface, this);
          if (!audioDecoder.initExtractor(audioPath)) {
            throw new IOException("fail to reset audio file");
          }
          audioDecoder.prepareAudio();
          audioDecoder.start();
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
    recordController.setAudioFormat(mediaFormat);
  }

  @Override
  public void inputPCMData(Frame frame) {
    if (audioTrackPlayer != null) {
      audioTrackPlayer.write(frame.getBuffer(), frame.getOffset(), frame.getSize());
    }
    audioEncoder.inputPCMData(frame);
  }
}
