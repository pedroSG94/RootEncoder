package com.pedro.builder;

/**
 * Created by pedro on 26/06/17.
 */

import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

/**
 * Created by pedro on 26/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 * Only video is working, audio will be added when it work
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtmpBuilderFromFile implements GetCameraData, GetH264Data {

  private VideoDecoder videoDecoder;
  private VideoEncoder videoEncoder;
  private boolean streaming;
  private SrsFlvMuxer srsFlvMuxer;
  private boolean videoEnabled = true;

  public RtmpBuilderFromFile(ConnectCheckerRtmp connectCheckerRtmp,
      VideoDecoderInterface videoDecoderInterface) {
    srsFlvMuxer = new SrsFlvMuxer(connectCheckerRtmp);
    videoEncoder = new VideoEncoder(this);
    videoDecoder = new VideoDecoder(videoDecoderInterface);
    streaming = false;
  }

  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  public boolean prepareVideo(String filePath, int bitRate) throws IOException {
    if (!videoDecoder.initExtractor(filePath)) return false;
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
            bitRate, 0, true, FormatVideoEncoder.SURFACE);
    videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    return result;
  }

  public void startStream(String url) {
    srsFlvMuxer.start(url);
    srsFlvMuxer.setVideoResolution(videoDecoder.getWidth(), videoDecoder.getHeight());
    videoEncoder.start();
    videoDecoder.start();
    streaming = true;
  }

  public void stopStream() {
    srsFlvMuxer.stop();
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

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    srsFlvMuxer.setSpsPPs(sps, pps);
  }

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendVideo(h264Buffer, info);
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


