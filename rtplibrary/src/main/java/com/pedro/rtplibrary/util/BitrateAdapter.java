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
    if (bitrate >= maxBitrate) { //You have high speed and max bitrate. Keep max speed
      oldBitrate = maxBitrate;
      return oldBitrate;
    } else if (bitrate <= oldBitrate * 0.9f) { //You have low speed and bitrate too high. Reduce bitrate by 10%.
      oldBitrate = (int) (bitrate * 0.9);
      return oldBitrate;
    } else { //You have high speed and bitrate too low. Increase bitrate by 10%.
      oldBitrate = (int) (bitrate * 1.1);
      if (oldBitrate > maxBitrate) oldBitrate = maxBitrate;
      return oldBitrate;
    }
  }

  public void reset() {
    averageBitrate = 0;
    cont = 0;
  }
}
