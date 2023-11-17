/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.library.network;

/**
 * <p>
 * Class used to calculateViewPort the approximate bandwidth of a user's connection.
 * </p>
 * <p>
 * This class notifies all subscribed {@link ConnectionClassStateChangeListener} with the new
 * ConnectionClass when the network's ConnectionClass changes.
 * </p>
 */

@Deprecated
public class ConnectionClassManager {

  private static final int BYTES_TO_BITS = 8;

  /** Current bandwidth of the user's connection depending upon the response. */
  private ConnectionClassStateChangeListener listener;
  /**
   * The lower bound for measured bandwidth in bits/ms. Readings
   * lower than this are treated as effectively zero (therefore ignored).
   */
  static final long BANDWIDTH_LOWER_BOUND = 10;

  // Singleton.
  private static class ConnectionClassManagerHolder {
    public static final ConnectionClassManager instance = new ConnectionClassManager();
  }

  /**
   * Retrieval method for the DownloadBandwidthManager singleton.
   *
   * @return The singleton instance of DownloadBandwidthManager.
   */
  public static ConnectionClassManager getInstance() {
    return ConnectionClassManagerHolder.instance;
  }

  // Force constructor to be private.
  private ConnectionClassManager() {
  }

  /**
   * Adds bandwidth to the current filtered latency counter. Sends a broadcast to all
   * {@link ConnectionClassStateChangeListener} if the counter moves from one bucket
   * to another (i.e. poor bandwidth -> moderate bandwidth).
   */
  public synchronized void addBandwidth(long bytes, long timeInMs) {

    //Ignore garbage values.
    if (timeInMs == 0 || (bytes) * 1.0 / (timeInMs) * BYTES_TO_BITS < BANDWIDTH_LOWER_BOUND) {
      return;
    }
    double bandwidth = (bytes) * 1.0 / (timeInMs) * BYTES_TO_BITS;
    if (listener != null) listener.onBandwidthStateChange(bandwidth);
  }

  /**
   * Interface for listening to when ConnectionClassManager
   * changes state.
   */
  public interface ConnectionClassStateChangeListener {
    /**
     * The method that will be called when ConnectionClassManager
     * changes ConnectionClass.
     */
    void onBandwidthStateChange(double bandwidth);
  }

  /**
   * Method for adding new listeners to this class.
   *
   * @param listener {@link ConnectionClassStateChangeListener} to add as a listener.
   */
  public void register(ConnectionClassStateChangeListener listener) {
    this.listener = listener;
  }

  /**
   * Method for removing listeners from this class.
   */
  public void remove() {
    listener = null;
  }
}