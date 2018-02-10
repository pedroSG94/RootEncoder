package com.pedro.rtsp.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtcp.SenderReportUdp;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 24/02/17.
 */

public class RtpSocketUdp extends BaseRtpSocket implements Runnable {

  private SenderReportUdp senderReportUdp;
  private MulticastSocket mSocket;
  private DatagramPacket[] packets;
  private int port = -1;
  private ConnectCheckerRtsp connectCheckerRtsp;

  /**
   * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
   */
  public RtpSocketUdp(ConnectCheckerRtsp connectCheckerRtsp) {
    super();
    this.connectCheckerRtsp = connectCheckerRtsp;
    senderReportUdp = new SenderReportUdp(connectCheckerRtsp);
    senderReportUdp.reset();
    packets = new DatagramPacket[bufferCount];
    for (int i = 0; i < bufferCount; i++) {
      packets[i] = new DatagramPacket(buffers[i], 1);
    }
    try {
      mSocket = new MulticastSocket();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** Closes the underlying socket. */
  public void close() {
    mSocket.close();
    senderReportUdp.close();
  }

  @Override
  public void setSSRC(int ssrc) {
    setLongSSRC(ssrc);
    senderReportUdp.setSSRC(ssrc);
  }

  /** Sets the Time To Live of the UDP packets. */
  public void setTimeToLive(int ttl) throws IOException {
    mSocket.setTimeToLive(ttl);
  }

  /** Sets the destination address and to which the packets will be sent. */
  public void setDestination(String dest, int dport, int rtcpPort) {
    try {
      if (dport != 0 && rtcpPort != 0) {
        port = dport;
        for (int i = 0; i < bufferCount; i++) {
          packets[i].setPort(dport);
          packets[i].setAddress(InetAddress.getByName(dest));
        }
        senderReportUdp.setDestination(InetAddress.getByName(dest), rtcpPort);
      }
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void implementCommitBuffer(int length) {
    packets[bufferIn].setLength(length);
  }

  /** The Thread sends the packets in the FIFO one by one at a constant rate. */
  @Override
  public void run() {
    try {
      while (bufferCommitted.tryAcquire(4, TimeUnit.SECONDS)) {
        if (running) {
          senderReportUdp.update(packets[bufferOut].getLength(), timestamps[bufferOut], port);
          mSocket.send(packets[bufferOut]);
          Log.i(TAG, "send packet, "
              + packets[bufferOut].getLength()
              + " Size, "
              + packets[bufferOut].getPort()
              + " Port");
          if (++bufferOut >= bufferCount) bufferOut = 0;
          bufferRequested.release();
        }
      }
    } catch (IOException | InterruptedException e) {
      Log.e(TAG, "UDP send error: ", e);
      connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.getMessage());
    }
    thread = null;
    resetFifo();
    senderReportUdp.reset();
  }
}