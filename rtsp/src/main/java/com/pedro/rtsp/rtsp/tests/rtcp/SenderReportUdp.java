package com.pedro.rtsp.rtsp.tests.rtcp;

import android.util.Log;
import com.pedro.rtsp.rtsp.tests.RtpFrame;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * Created by pedro on 24/02/17.
 */

public class SenderReportUdp extends BaseSenderReport {

  private MulticastSocket multicastSocket;
  private DatagramPacket datagramPacket;
  private ConnectCheckerRtsp connectCheckerRtsp;

  public SenderReportUdp(boolean isVideo, ConnectCheckerRtsp connectCheckerRtsp) {
    super(isVideo);
    this.connectCheckerRtsp = connectCheckerRtsp;
    try {
      multicastSocket = new MulticastSocket();
    } catch (IOException e) {
      // Very unlikely to happen. Means that all UDP ports are already being used
      throw new RuntimeException(e.getMessage());
    }
    datagramPacket = new DatagramPacket(buffer, 1);
  }

  public void close() {
    multicastSocket.close();
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   **/
  @Override
  public void update(RtpFrame rtpFrame) {
    if (updateSend(rtpFrame.getLength())) {
      send(System.nanoTime(), rtpFrame.getTimeStamp(), rtpFrame.getRtcpPort());
    }
  }

  public void setHost(String host) {
    try {
      datagramPacket.setAddress(InetAddress.getByName(host));
    } catch (UnknownHostException e) {
      Log.e(TAG, "Error", e);
    }
  }

  /**
   * Sends the RTCP packet over the network.
   *
   * @param ntpts the NTP timestamp.
   * @param rtpts the RTP timestamp.
   */
  private void send(final long ntpts, final long rtpts, final int port) {
    setData(ntpts, rtpts);
    datagramPacket.setLength(PACKET_LENGTH);
    datagramPacket.setPort(port);
    try {
      multicastSocket.send(datagramPacket);
      Log.i(TAG, "wrote report, " + datagramPacket.getPort() + " Port");
    } catch (IOException e) {
      Log.e(TAG, "send UDP report error", e);
      connectCheckerRtsp.onConnectionFailedRtsp("Error send report, " + e.getMessage());
    }
  }
}
