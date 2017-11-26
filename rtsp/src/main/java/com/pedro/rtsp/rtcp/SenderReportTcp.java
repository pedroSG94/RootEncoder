package com.pedro.rtsp.rtcp;

import android.util.Log;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by pedro on 24/02/17.
 */

public class SenderReportTcp extends BaseSenderReport {

  private final byte[] mTcpHeader;
  private OutputStream mOutputStream = null;
  private ConnectCheckerRtsp connectCheckerRtsp;

  public SenderReportTcp(ConnectCheckerRtsp connectCheckerRtsp) {
    super();
    this.connectCheckerRtsp = connectCheckerRtsp;
    mTcpHeader = new byte[] { '$', 0, 0, PACKET_LENGTH };
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   *
   * @param length The length of the packet
   * @param rtpts The RTP timestamp.
   **/
  public void update(int length, long rtpts) {
    if (updateSend(length)) send(System.nanoTime(), rtpts);
  }

  /**
   * Sends the RTCP packet over the network.
   *
   * @param ntpts the NTP timestamp.
   * @param rtpts the RTP timestamp.
   */
  private void send(final long ntpts, final long rtpts) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        setData(ntpts, rtpts);
        synchronized (mOutputStream) {
          try {
            mOutputStream.write(mTcpHeader);
            mOutputStream.write(mBuffer, 0, PACKET_LENGTH);
            Log.i(TAG, "send report");
          } catch (IOException e) {
            Log.e(TAG, "send TCP report error", e);
            connectCheckerRtsp.onConnectionFailedRtsp("Error send report, " + e.getMessage());
          }
        }
      }
    }).start();
  }

  public void setOutputStream(OutputStream os, byte channelIdentifier) {
    mOutputStream = os;
    mTcpHeader[1] = channelIdentifier;
  }
}
