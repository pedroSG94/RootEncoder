package com.pedro.rtplibrary.rtsp;

import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.rtplibrary.base.FromFileBase;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.FromFileBase}
 *
 * Created by pedro on 4/06/17.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtspFromFile extends FromFileBase {

  private RtspClient rtspClient;

  public RtspFromFile(ConnectCheckerRtsp connectCheckerRtsp,
      VideoDecoderInterface videoDecoderInterface, AudioDecoderInterface audioDecoderInterface) {
    super(videoDecoderInterface, audioDecoderInterface);
    rtspClient = new RtspClient(connectCheckerRtsp);
  }

  /**
   * Internet protocol used.
   *
   * @param protocol Could be Protocol.TCP or Protocol.UDP.
   */
  public void setProtocol(Protocol protocol) {
    rtspClient.setProtocol(protocol);
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
    ByteBuffer newSps = sps.duplicate();
    ByteBuffer newPps = pps.duplicate();
    rtspClient.setSPSandPPS(newSps, newPps);
    rtspClient.connect();
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    rtspClient.sendAudio(aacBuffer, info);
  }
}

