package com.pedro.rtplibrary.util;

/**
 * Created by pedro on 11/07/19.
 */
public class BitrateAdapter {

  public interface Listener {
    void onBitrateAdapted(int bitrate);
  }

  private int maxBitrate;
  private int oldBitrate;
  private int averageBitrate;
  private int cont;
  private Listener listener;

  public BitrateAdapter(Listener listener) {
    this.listener = listener;
    reset();
  }

  public void setMaxBitrate(int bitrate) {
    this.maxBitrate = bitrate;
    this.oldBitrate = bitrate;
    reset();
  }

  public void adaptBitrate(long actualBitrate) {
    averageBitrate += actualBitrate;
    averageBitrate /= 2;
    cont++;
    if (cont >= 5) {
      if (listener != null && maxBitrate != 0) {
        listener.onBitrateAdapted(getBitrateAdapted(averageBitrate));
        reset();
      }
    }
  }

  private int getBitrateAdapted(int bitrate) {
    //reduce bitrate 10%
    if (bitrate < oldBitrate * 0.65) {
      oldBitrate -= maxBitrate * 0.1;
      if (oldBitrate < maxBitrate * 0.1) oldBitrate = (int) (maxBitrate * 0.1);
      return oldBitrate;
      //increase bitrate 10%
    } else {
      oldBitrate += maxBitrate * 0.1;
      if (oldBitrate > maxBitrate) oldBitrate = maxBitrate;
      return oldBitrate;
    }
  }

  public void reset() {
    averageBitrate = 0;
    cont = 0;
  }
}
