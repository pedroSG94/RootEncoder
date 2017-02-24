package com.pedro.rtsp.rtcp;

import android.os.SystemClock;
import java.io.OutputStream;

/**
 * Created by pedro on 24/02/17.
 */

public class SenderReportTcp extends BaseSenderReport {

  private final byte[] mTcpHeader;
  private OutputStream mOutputStream = null;

  public SenderReportTcp() {
    super();
    mTcpHeader = new byte[] { '$', 0, 0, PACKET_LENGTH };
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   *
   * @param length The length of the packet
   * @param rtpts The RTP timestamp.
   **/
  public void update(int length, long rtpts) {
    mPacketCount += 1;
    mOctetCount += length;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);

    now = SystemClock.elapsedRealtime();
    delta += old != 0 ? now - old : 0;
    old = now;
    if (interval > 0) {
      if (delta >= interval) {
        // We send a Sender Report
        send(System.nanoTime(), rtpts);
        delta = 0;
      }
    }
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
        long hb = ntpts / 1000000000;
        long lb = ((ntpts - hb * 1000000000) * 4294967296L) / 1000000000;
        setLong(hb, 8, 12);
        setLong(lb, 12, 16);
        setLong(rtpts, 16, 20);
        synchronized (mOutputStream) {
          try {
            mOutputStream.write(mTcpHeader);
            mOutputStream.write(mBuffer, 0, PACKET_LENGTH);
          } catch (Exception e) {
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
