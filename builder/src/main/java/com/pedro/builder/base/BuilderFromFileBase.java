package com.pedro.builder.base;

import android.media.MediaCodec;
import android.os.Build;
import com.pedro.encoder.input.decoder.VideoDecoder;
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

public abstract class BuilderFromFileBase implements GetCameraData, GetH264Data {

  private VideoDecoder videoDecoder;
  protected VideoEncoder videoEncoder;
  private boolean streaming;
  private boolean videoEnabled = true;

  public BuilderFromFileBase(VideoDecoderInterface videoDecoderInterface) {
    videoEncoder = new VideoEncoder(this);
    videoDecoder = new VideoDecoder(videoDecoderInterface);
    streaming = false;
  }

  public abstract void setAuthorization(String user, String password);

  public boolean prepareVideo(String filePath, int bitRate) throws IOException {
    if (!videoDecoder.initExtractor(filePath)) return false;
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
            bitRate, 0, true, FormatVideoEncoder.SURFACE);
    videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    return result;
  }

  protected abstract void startStreamRtp(String url);

  public void startStream(String url) {
    startStreamRtp(url);
    videoEncoder.start();
    videoDecoder.start();
    streaming = true;
  }

  protected abstract void stopStreamRtp();

  public void stopStream() {
    stopStreamRtp();
    videoDecoder.stop();
    videoEncoder.stop();
    streaming = false;
  }

  public void setLoopMode(boolean loopMode) {
    videoDecoder.setLoopMode(loopMode);
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
}
