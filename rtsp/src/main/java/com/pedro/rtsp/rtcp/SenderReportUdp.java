package com.pedro.rtsp.rtcp;

import android.os.SystemClock;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Created by pedro on 24/02/17.
 */

public class SenderReportUdp extends BaseSenderReport {

  private MulticastSocket socket;
  private DatagramPacket datagramPacket;

  public SenderReportUdp() {
    super();
    try {
      socket = new MulticastSocket();
    } catch (IOException e) {
      // Very unlikely to happen. Means that all UDP ports are already being used
      throw new RuntimeException(e.getMessage());
    }
    datagramPacket = new DatagramPacket(mBuffer, 1);
  }

  public void close() {
    socket.close();
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   *
   * @param length The length of the packet
   * @param rtpts The RTP timestamp.
   **/
  public void update(int length, long rtpts, int port) {
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
        send(System.nanoTime(), rtpts, port);
        delta = 0;
      }
    }
  }

  public void setDestination(InetAddress dest, int dport) {
    datagramPacket.setPort(dport);
    datagramPacket.setAddress(dest);
  }

  /**
   * Sends the RTCP packet over the network.
   *
   * @param ntpts the NTP timestamp.
   * @param rtpts the RTP timestamp.
   */
  private void send(final long ntpts, final long rtpts, final int port) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        long hb = ntpts / 1000000000;
        long lb = ((ntpts - hb * 1000000000) * 4294967296L) / 1000000000;
        setLong(hb, 8, 12);
        setLong(lb, 12, 16);
        setLong(rtpts, 16, 20);
        datagramPacket.setLength(PACKET_LENGTH);
        datagramPacket.setPort(port);
        Log.i(TAG, "send report, " + datagramPacket.getPort() + " Port");
        try {
          socket.send(datagramPacket);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }).start();
  }
}
