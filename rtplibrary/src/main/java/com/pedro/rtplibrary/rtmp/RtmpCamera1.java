package com.pedro.rtplibrary.rtmp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.SurfaceView;
import android.view.TextureView;
import com.pedro.rtplibrary.base.Camera1Base;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;
import java.nio.ByteBuffer;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 *
 * Created by pedro on 25/01/17.
 */

public class RtmpCamera1 extends Camera1Base {

  private SrsFlvMuxer srsFlvMuxer;

  public RtmpCamera1(SurfaceView surfaceView, ConnectCheckerRtmp connectChecker) {
    super(surfaceView);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  public RtmpCamera1(TextureView textureView, ConnectCheckerRtmp connectChecker) {
    super(textureView);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public RtmpCamera1(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
    super(openGlView);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public RtmpCamera1(LightOpenGlView lightOpenGlView, ConnectCheckerRtmp connectChecker) {
    super(lightOpenGlView);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public RtmpCamera1(Context context, ConnectCheckerRtmp connectChecker) {
    super(context);
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  /**
   * H264 profile.
   *
   * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
   */
  public void setProfileIop(byte profileIop) {
    srsFlvMuxer.setProfileIop(profileIop);
  }

  @Override
  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    srsFlvMuxer.setIsStereo(isStereo);
    srsFlvMuxer.setSampleRate(sampleRate);
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
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendAudio(aacBuffer, info);
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
