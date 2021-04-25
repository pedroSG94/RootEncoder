package com.pedro.rtplibrary.rtsp;

import android.media.MediaCodec;

import androidx.annotation.Nullable;

import com.pedro.rtplibrary.base.OnlyAudioBase;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.OnlyAudioBase}
 *
 * Created by pedro on 10/07/18.
 */
public class RtspOnlyAudio extends OnlyAudioBase {

  private RtspClient rtspClient;

  public RtspOnlyAudio(ConnectCheckerRtsp connectCheckerRtsp) {
    super();
    rtspClient = new RtspClient(connectCheckerRtsp);
    rtspClient.setOnlyAudio(true);
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
  public void resizeCache(int newSize) throws RuntimeException {
    rtspClient.resizeCache(newSize);
  }

  @Override
  public int getCacheSize() {
    return rtspClient.getCacheSize();
  }

  @Override
  public long getSentAudioFrames() {
    return rtspClient.getSentAudioFrames();
  }

  @Override
  public long getSentVideoFrames() {
    return rtspClient.getSentVideoFrames();
  }

  @Override
  public long getDroppedAudioFrames() {
    return rtspClient.getDroppedAudioFrames();
  }

  @Override
  public long getDroppedVideoFrames() {
    return rtspClient.getDroppedVideoFrames();
  }

  @Override
  public void resetSentAudioFrames() {
    rtspClient.resetSentAudioFrames();
  }

  @Override
  public void resetSentVideoFrames() {
    rtspClient.resetSentVideoFrames();
  }

  @Override
  public void resetDroppedAudioFrames() {
    rtspClient.resetDroppedAudioFrames();
  }

  @Override
  public void resetDroppedVideoFrames() {
    rtspClient.resetDroppedVideoFrames();
  }

  @Override
  public void setAuthorization(String user, String password) {
    rtspClient.setAuthorization(user, password);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    rtspClient.setIsStereo(isStereo);
    rtspClient.setSampleRate(sampleRate);
  }

  @Override
  protected void startStreamRtp(String url) {
    rtspClient.connect(url);
  }

  @Override
  protected void stopStreamRtp() {
    rtspClient.disconnect();
  }

  @Override
  public void setReTries(int reTries) {
    rtspClient.setReTries(reTries);
  }

  @Override
  protected boolean shouldRetry(String reason) {
    return rtspClient.shouldRetry(reason);
  }

  @Override
  public void reConnect(long delay, @Nullable String backupUrl) {
    rtspClient.reConnect(delay, backupUrl);
  }

  @Override
  public boolean hasCongestion() {
    return rtspClient.hasCongestion();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    rtspClient.sendAudio(aacBuffer, info);
  }

  @Override
  public void setLogs(boolean enable) {
    rtspClient.setLogs(enable);
  }
}