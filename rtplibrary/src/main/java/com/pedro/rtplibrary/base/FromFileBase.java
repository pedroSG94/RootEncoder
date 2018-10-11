package com.pedro.rtplibrary.base;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
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
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.OffScreenGlThread;
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
    implements GetH264Data, GetAacData, GetMicrophoneData, LoopFileInterface {

  private static final String TAG = "FromFileBase";
  private Context context;

  protected VideoEncoder videoEncoder;
  private AudioEncoder audioEncoder;
  private OffScreenGlThread glInterface;
  private boolean streaming = false;
  private boolean videoEnabled = true;
  //record
  private MediaMuxer mediaMuxer;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private boolean recording = false;
  private boolean canRecord = false;
  private MediaFormat videoFormat;
  private MediaFormat audioFormat;

  private VideoDecoder videoDecoder;
  private AudioDecoder audioDecoder;

  private VideoDecoderInterface videoDecoderInterface;
  private AudioDecoderInterface audioDecoderInterface;

  private String videoPath, audioPath;
  private final Object sync = new Object();

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
    init(videoDecoderInterface, audioDecoderInterface);
  }

  private void init(VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    this.videoDecoderInterface = videoDecoderInterface;
    this.audioDecoderInterface = audioDecoderInterface;
    videoEncoder = new VideoEncoder(this);
    audioEncoder = new AudioEncoder(this);
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
  public boolean prepareVideo(String filePath, int bitRate) throws IOException {
    videoPath = filePath;
    videoDecoder = new VideoDecoder(videoDecoderInterface, this);
    if (!videoDecoder.initExtractor(filePath)) return false;
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
            bitRate, 0, true, 2, FormatVideoEncoder.SURFACE);
    if (context != null) {
      glInterface = new OffScreenGlThread(context);
      glInterface.setEncoderSize(videoDecoder.getWidth(), videoDecoder.getHeight());
      glInterface.init();
    } else {
      videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    }
    return result;
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
        audioDecoder.isStereo());
    audioDecoder.prepareAudio();
    return result;
  }

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
  public void startRecord(String path) throws IOException {
    mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    recording = true;
    if (!streaming) {
      startEncoders();
    } else if (videoEncoder.isRunning()) {
      resetVideoEncoder();
    }
  }

  /**
   * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
   */
  public void stopRecord() {
    recording = false;
    if (mediaMuxer != null) {
      if (canRecord) {
        mediaMuxer.stop();
        mediaMuxer.release();
        canRecord = false;
      }
      mediaMuxer = null;
    }
    videoTrack = -1;
    audioTrack = -1;
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
    startStreamRtp(url);
    if (!recording) {
      startEncoders();
    } else {
      resetVideoEncoder();
    }
  }

  private void startEncoders() {
    videoEncoder.start();
    audioEncoder.start();
    videoDecoder.start();
    audioDecoder.start();
  }

  private void resetVideoEncoder() {
    try {
      if (context != null) {
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
      if (context != null) {
        glInterface.setFps(videoEncoder.getFps());
        glInterface.start();
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        videoDecoder.prepareVideo(glInterface.getSurface());
      } else {
        videoDecoder.prepareVideo(videoEncoder.getInputSurface());
      }
      videoDecoder.start();
      videoDecoder.moveTo(time);
    } catch (IOException e) {
      Log.e(TAG, "Error", e);
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
    if (!recording) {
      if (context != null) {
        glInterface.removeMediaCodecSurface();
        glInterface.stop();
      }
      if (videoDecoder != null) videoDecoder.stop();
      if (audioDecoder != null) audioDecoder.stop();
      videoEncoder.stop();
      audioEncoder.stop();
      videoFormat = null;
      audioFormat = null;
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

  /**
   * Disable send camera frames and send a black image with low bitrate(to reduce bandwith used)
   * instance it.
   */
  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  /**
   * Enable send MP4 file frames.
   */
  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
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
    return recording;
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
          if (context != null) {
            glInterface.removeMediaCodecSurface();
            glInterface.stop();
          }
          videoDecoder.stop();
          videoDecoder = new VideoDecoder(videoDecoderInterface, this);
          if (!videoDecoder.initExtractor(videoPath)) {
            throw new IOException("fail to reset video file");
          }
          if (context != null) {
            glInterface.setFps(videoEncoder.getFps());
            glInterface.start();
            glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
            videoDecoder.prepareVideo(glInterface.getSurface());
          } else {
            videoDecoder.prepareVideo(videoEncoder.getInputSurface());
          }
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

  protected abstract void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps);

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    if (streaming) onSPSandPPSRtp(sps, pps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    if (recording) {
      if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
          && !canRecord
          && videoFormat != null
          && audioFormat != null) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
        audioTrack = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();
        canRecord = true;
      }
      if (canRecord) mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
    }
    if (streaming) getH264DataRtp(h264Buffer, info);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    videoFormat = mediaFormat;
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (recording && canRecord) {
      mediaMuxer.writeSampleData(audioTrack, aacBuffer, info);
    }
    if (streaming) getAacDataRtp(aacBuffer, info);
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    audioFormat = mediaFormat;
  }

  @Override
  public void inputPCMData(byte[] buffer, int size) {
    audioEncoder.inputPCMData(buffer, size);
  }
}
