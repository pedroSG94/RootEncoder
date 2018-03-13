package com.pedro.rtplibrary.base;

import android.content.Context;
import android.graphics.PointF;
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
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.OffScreenGlThread;
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
  protected AudioEncoder audioEncoder;
  private OffScreenGlThread offScreenGlThread;
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
    this.videoDecoderInterface = videoDecoderInterface;
    this.audioDecoderInterface = audioDecoderInterface;
    videoEncoder = new VideoEncoder(this);
    audioEncoder = new AudioEncoder(this);
  }

  /**
   * OpenGl mode, necessary context.
   */
  public FromFileBase(Context context, VideoDecoderInterface videoDecoderInterface,
      AudioDecoderInterface audioDecoderInterface) {
    this.context = context;
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
      offScreenGlThread =
          new OffScreenGlThread(context, videoDecoder.getWidth(), videoDecoder.getHeight());
      offScreenGlThread.init();
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
    if (streaming) {
      mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      videoTrack = mediaMuxer.addTrack(videoFormat);
      audioTrack = mediaMuxer.addTrack(audioFormat);
      mediaMuxer.start();
      recording = true;
      if (videoEncoder.isRunning()) {
        if (offScreenGlThread != null) offScreenGlThread.removeMediaCodecSurface();
        videoEncoder.reset();
        if (offScreenGlThread != null) {
          offScreenGlThread.addMediaCodecSurface(videoEncoder.getInputSurface());
        }
      }
    } else {
      throw new IOException("Need be called while stream");
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
    startStreamRtp(url);
    if (context != null) {
      offScreenGlThread.start();
      offScreenGlThread.addMediaCodecSurface(videoEncoder.getInputSurface());
      videoDecoder.prepareVideo(offScreenGlThread.getSurface());
    }
    videoEncoder.start();
    audioEncoder.start();
    videoDecoder.start();
    audioDecoder.start();
    streaming = true;
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    stopStreamRtp();
    if (context != null) {
      offScreenGlThread.removeMediaCodecSurface();
      offScreenGlThread.stop();
    }
    videoDecoder.stop();
    audioDecoder.stop();
    videoEncoder.stop();
    audioEncoder.stop();
    streaming = false;
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

  public void setFilter(BaseFilterRender baseFilterRender) {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setFilter(baseFilterRender);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a gif");
      }
    }
  }

  /**
   * Set a gif to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param gifStreamObject gif object that will be streamed.
   * @throws RuntimeException If you don't use context
   */
  public void setGifStreamObject(GifStreamObject gifStreamObject) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setGif(gifStreamObject);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a gif");
      }
    }
  }

  /**
   * Set an image to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param imageStreamObject image object that will be streamed.
   * @throws RuntimeException If you don't use context
   */
  public void setImageStreamObject(ImageStreamObject imageStreamObject) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setImage(imageStreamObject);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set an image");
      }
    }
  }

  /**
   * Set a text to the stream.
   * By default with same resolution in px that the original file and in bottom-right position.
   *
   * @param textStreamObject text object that will be streamed.
   * @throws RuntimeException If you don't use context
   */
  public void setTextStreamObject(TextStreamObject textStreamObject) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setText(textStreamObject);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a text");
      }
    }
  }

  /**
   * Clear stream object of the stream.
   *
   * @throws RuntimeException If you don't use context
   */
  public void clearStreamObject() throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.clear();
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to clear");
      }
    }
  }

  /**
   * Set alpha to the stream object.
   *
   * @param alpha of the stream object on fly, 1.0f totally opaque and 0.0f totally transparent
   * @throws RuntimeException If you don't use context
   */
  public void setAlphaStreamObject(float alpha) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setStreamObjectAlpha(alpha);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set an alpha");
      }
    }
  }

  /**
   * Set resolution to the stream object in percent.
   *
   * @param sizeX of the stream object in percent: 100 full screen to 1
   * @param sizeY of the stream object in percent: 100 full screen to 1
   * @throws RuntimeException If you don't use context
   */
  public void setSizeStreamObject(float sizeX, float sizeY) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setStreamObjectSize(sizeX, sizeY);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a size");
      }
    }
  }

  /**
   * Set position to the stream object in percent.
   *
   * @param x of the stream object in percent: 100 full screen left to 0 full right
   * @param y of the stream object in percent: 100 full screen top to 0 full bottom
   * @throws RuntimeException If you don't use context
   */
  public void setPositionStreamObject(float x, float y) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setStreamObjectPosition(x, y);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a position");
      }
    }
  }

  /**
   * Set position to the stream object with commons values developed.
   *
   * @param translateTo pre determinate positions
   * @throws RuntimeException If you don't use context
   */
  public void setPositionStreamObject(TranslateTo translateTo) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.setStreamObjectPosition(translateTo);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a position");
      }
    }
  }

  /**
   * Enable FXAA. Disabled by default.
   *
   * @param AAEnabled true to enable false to disable
   * @throws RuntimeException
   */
  public void enableAA(boolean AAEnabled) throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        if (isStreaming()) {
          offScreenGlThread.enableAA(AAEnabled);
        } else {
          Log.e(TAG, "You are not streaming, ignored");
        }
      } else {
        throw new RuntimeException("You must use context in the constructor to set a position");
      }
    }
  }

  public boolean isAAEnabled() throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        return offScreenGlThread.isAAEnabled();
      } else {
        throw new RuntimeException("You must use context in the constructor to set a position");
      }
    }
  }

  /**
   * Get scale of the stream object in percent.
   *
   * @return scale in percent, 0 is stream not started
   * @throws RuntimeException If you don't use context
   */
  public PointF getSizeStreamObject() throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        return offScreenGlThread.getScale();
      } else {
        throw new RuntimeException("You must use context in the constructor to get position");
      }
    }
  }

  /**
   * Get position of the stream object in percent.
   *
   * @return position in percent, 0 is stream not started
   * @throws RuntimeException If you don't use context
   */
  public PointF getPositionStreamObject() throws RuntimeException {
    synchronized (sync) {
      if (offScreenGlThread != null) {
        return offScreenGlThread.getPosition();
      } else {
        throw new RuntimeException("You must use context in the constructor to get scale");
      }
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

  /**
   * Get video camera state
   *
   * @return true if disabled, false if enabled
   */
  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  /**
   * Se video bitrate of H264 in kb while stream.
   *
   * @param bitrate H264 in kb.
   */
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
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
            offScreenGlThread.removeMediaCodecSurface();
            offScreenGlThread.stop();
          }
          videoDecoder.stop();
          videoDecoder = new VideoDecoder(videoDecoderInterface, this);
          if (!videoDecoder.initExtractor(videoPath)) {
            throw new IOException("fail to reset video file");
          }
          if (context != null) {
            offScreenGlThread.start();
            offScreenGlThread.addMediaCodecSurface(videoEncoder.getInputSurface());
            videoDecoder.prepareVideo(offScreenGlThread.getSurface());
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
    onSPSandPPSRtp(sps, pps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    if (recording) {
      if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) canRecord = true;
      if (canRecord) {
        mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
      }
    }
    getH264DataRtp(h264Buffer, info);
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
    getAacDataRtp(aacBuffer, info);
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
