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

  public abstract void setAuthorization(String user, String password);

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

  /*Need be called while stream*/
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

  public void startStream(String url) {
    startStreamRtp(url);
    videoEncoder.start();
    //videoDecoder.start();
    mediaPlayer.start();
    streaming = true;
  }

  protected abstract void stopStreamRtp();

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

  public void setLoopMode(boolean loopMode) {
    //videoDecoder.setLoopMode(loopMode);
    mediaPlayer.setLooping(loopMode);
  }

  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  /** need min API 19 */
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
  }

  public boolean isStreaming() {
    return streaming;
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
      mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
    }
    getH264DataRtp(h264Buffer, info);
  }

  @Override
  public void inputYv12Data(byte[] buffer) {
    videoEncoder.inputYv12Data(buffer);
  }

  @Override
  public void inputNv21Data(byte[] buffer) {
    videoEncoder.inputNv21Data(buffer);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    videoFormat = mediaFormat;
  }
}
