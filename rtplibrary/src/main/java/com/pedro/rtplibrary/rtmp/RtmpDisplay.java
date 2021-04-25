package com.pedro.rtplibrary.rtmp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pedro.rtplibrary.base.DisplayBase;

import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.DisplayBase}
 *
 * Created by pedro on 9/08/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtmpDisplay extends DisplayBase {

  private SrsFlvMuxer srsFlvMuxer;

  public RtmpDisplay(Context context, boolean useOpengl, ConnectCheckerRtmp connectChecker) {
    super(context, useOpengl);
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
  protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
    srsFlvMuxer.setSpsPPs(sps, pps);
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendVideo(h264Buffer, info);
  }

  @Override
  public void setLogs(boolean enable) {
    srsFlvMuxer.setLogs(enable);
  }
}
