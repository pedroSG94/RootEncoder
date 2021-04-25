package com.pedro.rtplibrary.rtmp;

import android.media.MediaCodec;

import androidx.annotation.Nullable;

import com.pedro.rtplibrary.base.OnlyAudioBase;
import java.nio.ByteBuffer;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.OnlyAudioBase}
 *
 * Created by pedro on 10/07/18.
 */
public class RtmpOnlyAudio extends OnlyAudioBase {

  private SrsFlvMuxer srsFlvMuxer;

  public RtmpOnlyAudio(ConnectCheckerRtmp connectChecker) {
    super();
    srsFlvMuxer = new SrsFlvMuxer(connectChecker);
  }

  @Override
  public void resizeCache(int newSize) throws RuntimeException {
    srsFlvMuxer.resizeFlvTagCache(newSize);
  }

  @Override
  public int getCacheSize() {
    return srsFlvMuxer.getFlvTagCacheSize();
  }

  @Override
  public long getSentAudioFrames() {
    return srsFlvMuxer.getSentAudioFrames();
  }

  @Override
  public long getSentVideoFrames() {
    return srsFlvMuxer.getSentVideoFrames();
  }

  @Override
  public long getDroppedAudioFrames() {
    return srsFlvMuxer.getDroppedAudioFrames();
  }

  @Override
  public long getDroppedVideoFrames() {
    return srsFlvMuxer.getDroppedVideoFrames();
  }

  @Override
  public void resetSentAudioFrames() {
    srsFlvMuxer.resetSentAudioFrames();
  }

  @Override
  public void resetSentVideoFrames() {
    srsFlvMuxer.resetSentVideoFrames();
  }

  @Override
  public void resetDroppedAudioFrames() {
    srsFlvMuxer.resetDroppedAudioFrames();
  }

  @Override
  public void resetDroppedVideoFrames() {
    srsFlvMuxer.resetDroppedVideoFrames();
  }

  @Override
  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  /**
   * Some Livestream hosts use Akamai auth that requires RTMP packets to be sent with increasing
   * timestamp order regardless of packet type.
   * Necessary with Servers like Dacast.
   * More info here:
   * https://learn.akamai.com/en-us/webhelp/media-services-live/media-services-live-encoder-compatibility-testing-and-qualification-guide-v4.0/GUID-F941C88B-9128-4BF4-A81B-C2E5CFD35BBF.html
   */
  public void forceAkamaiTs(boolean enabled) {
    srsFlvMuxer.forceAkamaiTs(enabled);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    srsFlvMuxer.setIsStereo(isStereo);
    srsFlvMuxer.setSampleRate(sampleRate);
  }

  @Override
  protected void startStreamRtp(String url) {
    srsFlvMuxer.start(url);
  }

  @Override
  protected void stopStreamRtp() {
    srsFlvMuxer.stop();
  }

  @Override
  public void setReTries(int reTries) {
    srsFlvMuxer.setReTries(reTries);
  }

  @Override
  protected boolean shouldRetry(String reason) {
    return srsFlvMuxer.shouldRetry(reason);
  }

  @Override
  public void reConnect(long delay, @Nullable String backupUrl) {
    srsFlvMuxer.reConnect(delay, backupUrl);
  }

  @Override
  public boolean hasCongestion() {
    return srsFlvMuxer.hasCongestion();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendAudio(aacBuffer, info);
  }

  @Override
  public void setLogs(boolean enable) {
    srsFlvMuxer.setLogs(enable);
  }
}
