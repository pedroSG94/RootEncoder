package com.pedro.rtplibrary.base;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
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
public abstract class FromFileBase implements GetCameraData, GetH264Data {

  protected VideoEncoder videoEncoder;
  private boolean streaming;
  private boolean videoEnabled = true;
  //record
  private MediaMuxer mediaMuxer;
  private int videoTrack = -1;
  private boolean recording = false;
  private boolean canRecord = false;
  private MediaFormat videoFormat;

  private VideoDecoderInterface videoDecoderInterface;
  private MediaPlayer mediaPlayer;
  //private VideoDecoder videoDecoder;

  public FromFileBase(VideoDecoderInterface videoDecoderInterface) {
    this.videoDecoderInterface = videoDecoderInterface;
    videoEncoder = new VideoEncoder(this);
    //videoDecoder = new VideoDecoder(videoDecoderInterface);
    streaming = false;
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
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mediaPlayer) {
        videoDecoderInterface.onVideoDecoderFinished();
      }
    });
    mediaPlayer.setDataSource(filePath);
    mediaPlayer.prepare();
    mediaPlayer.setVolume(0, 0);
    //if (!videoDecoder.initExtractor(filePath)) return false;
    boolean result =
        videoEncoder.prepareVideoEncoder(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight(),
            30, bitRate, 0, true, FormatVideoEncoder.SURFACE);
    mediaPlayer.setSurface(videoEncoder.getInputSurface());
    //videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    return result;
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
      mediaMuxer.start();
      recording = true;
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
      mediaMuxer.stop();
      mediaMuxer.release();
      mediaMuxer = null;
    }
    videoTrack = -1;
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
    videoEncoder.start();
    //videoDecoder.start();
    mediaPlayer.start();
    streaming = true;
  }

  protected abstract void stopStreamRtp();

  /**
   * Stop stream started with @startStream.
   */
  public void stopStream() {
    stopStreamRtp();
    if (mediaPlayer != null) {
      mediaPlayer.stop();
      mediaPlayer.release();
      mediaPlayer = null;
    }
    //videoDecoder.stop();
    videoEncoder.stop();
    streaming = false;
  }

  /**
   * If you want reproduce video in loop.
   *
   * @param loopMode true in loop, false stop stream when video finish.
   */
  public void setLoopMode(boolean loopMode) {
    //videoDecoder.setLoopMode(loopMode);
    mediaPlayer.setLooping(loopMode);
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
  public void inputYUVData(byte[] buffer) {
    videoEncoder.inputYUVData(buffer);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    videoFormat = mediaFormat;
  }
}
