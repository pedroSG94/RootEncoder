package com.pedro.rtplibrary.rtmp;

/**
 * Created by pedro on 26/06/17.
 */

import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.rtplibrary.base.FromFileBase;

import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 26/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 * Only video is working, audio will be added when it work
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtmpFromFile extends FromFileBase {

  private SrsFlvMuxer srsFlvMuxer;

  public RtmpFromFile(ConnectCheckerRtmp connectChecker,
      VideoDecoderInterface videoDecoderInterface) {
    super(videoDecoderInterface);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  @Override
  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  @Override
  protected void startStreamRtp(String url) {
    if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
      srsFlvMuxer.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
    } else {
      srsFlvMuxer.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
    }
    srsFlvMuxer.start(url);
  }

  @Override
  protected void stopStreamRtp() {
    srsFlvMuxer.stop();
  }

  @Override
  protected void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps) {
    srsFlvMuxer.setSpsPPs(sps, pps);
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendVideo(h264Buffer, info);
  }
}


