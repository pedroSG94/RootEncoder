package com.pedro.rtplibrary.rtmp;

import android.media.MediaCodec;
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

  public void resizeFlvTagCache(int newSize){
    srsFlvMuxer.resizeFlvTagCache(newSize);
  }

  public int getFlvTagCacheSize(){
    if(srsFlvMuxer != null) {
      return srsFlvMuxer.getFlvTagCacheSize();
    }
    return -1;
  }

  public long getSentAudioFrames() {
    if(srsFlvMuxer != null) {
      return srsFlvMuxer.getSentAudioFrames();
    }
    return -1;
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
}
