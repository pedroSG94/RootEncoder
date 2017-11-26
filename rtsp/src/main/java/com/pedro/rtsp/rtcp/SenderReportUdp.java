package com.pedro.rtsp.rtcp;

import android.util.Log;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
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
  private ConnectCheckerRtsp connectCheckerRtsp;

  public SenderReportUdp(ConnectCheckerRtsp connectCheckerRtsp) {
    super();
    this.connectCheckerRtsp = connectCheckerRtsp;
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
   * @param port to send packet
   **/
  public void update(int length, long rtpts, int port) {
    if (updateSend(length)) send(System.nanoTime(), rtpts, port);
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
        setData(ntpts, rtpts);
        datagramPacket.setLength(PACKET_LENGTH);
        datagramPacket.setPort(port);
        try {
          socket.send(datagramPacket);
          Log.i(TAG, "send report, " + datagramPacket.getPort() + " Port");
        } catch (IOException e) {
          Log.e(TAG, "send UDP report error", e);
          connectCheckerRtsp.onConnectionFailedRtsp("Error send report, " + e.getMessage());
        }
      }
    }).start();
  }
}
