/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.library.util;

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
  private float decreaseRange = 0.8f; //20%
  private float increaseRange = 1.2f; //20%

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

  /**
   * Adapt bitrate on fly based on queue state.
   */
  public void adaptBitrate(long actualBitrate, boolean hasCongestion) {
    averageBitrate += actualBitrate;
    averageBitrate /= 2;
    cont++;
    if (cont >= 5) {
      if (listener != null && maxBitrate != 0) {
        listener.onBitrateAdapted(getBitrateAdapted(averageBitrate, hasCongestion));
        reset();
      }
    }
  }

  private int getBitrateAdapted(int bitrate) {
    if (bitrate >= maxBitrate) { //You have high speed and max bitrate. Keep max speed
      oldBitrate = maxBitrate;
    } else if (bitrate <= oldBitrate * 0.9f) { //You have low speed and bitrate too high. Reduce bitrate by 10%.
      oldBitrate = (int) (bitrate * decreaseRange);
    } else { //You have high speed and bitrate too low. Increase bitrate by 10%.
      oldBitrate = (int) (bitrate * increaseRange);
      if (oldBitrate > maxBitrate) oldBitrate = maxBitrate;
    }
    return oldBitrate;
  }

  private int getBitrateAdapted(int bitrate, boolean hasCongestion) {
    if (bitrate >= maxBitrate) { //You have high speed and max bitrate. Keep max speed
      oldBitrate = maxBitrate;
    } else if (hasCongestion) { //You have low speed and bitrate too high. Reduce bitrate by 10%.
      oldBitrate = (int) (bitrate * decreaseRange);
    } else { //You have high speed and bitrate too low. Increase bitrate by 10%.
      oldBitrate = (int) (bitrate * increaseRange);
      if (oldBitrate > maxBitrate) oldBitrate = maxBitrate;
    }
    return oldBitrate;
  }

  public void reset() {
    averageBitrate = 0;
    cont = 0;
  }

  public float getDecreaseRange() {
    return decreaseRange;
  }

  /**
   * @param decreaseRange in percent. How many bitrate will be reduced based on oldBitrate.
   * valid values:
   * 0 to 100 not included
   */
  public void setDecreaseRange(float decreaseRange) {
    if (decreaseRange > 0f && decreaseRange < 100f) {
      this.decreaseRange = 1f - (decreaseRange / 100f);
    }
  }

  public float getIncreaseRange() {
    return increaseRange;
  }

  /**
   * @param increaseRange in percent. How many bitrate will be increment based on oldBitrate.
   * valid values:
   * 0 to 100
   */
  public void setIncreaseRange(float increaseRange) {
    if (increaseRange > 0f && increaseRange < 100f) {
      this.increaseRange = 1f + (increaseRange / 100f);
    }
  }
}
