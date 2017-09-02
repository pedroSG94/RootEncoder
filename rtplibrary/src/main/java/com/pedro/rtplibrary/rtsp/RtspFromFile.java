package com.pedro.rtplibrary.rtsp;

import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.rtplibrary.base.FromFileBase;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 4/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 * Only video is working, audio will be added when it work
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtspFromFile extends FromFileBase {

  private RtspClient rtspClient;

  public RtspFromFile(Protocol protocol, ConnectCheckerRtsp connectCheckerRtsp,
      VideoDecoderInterface videoDecoderInterface) {
    super(videoDecoderInterface);
    rtspClient = new RtspClient(connectCheckerRtsp, protocol);
  }

  @Override
  public void setAuthorization(String user, String password) {
    rtspClient.setAuthorization(user, password);
  }

  @Override
  protected void startStreamRtp(String url) {
    rtspClient.setUrl(url);
  }

  @Override
  protected void stopStreamRtp() {
    rtspClient.disconnect();
  }

  @Override
  protected void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps) {
    rtspClient.setSPSandPPS(sps, pps);
    rtspClient.connect();
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }
}

