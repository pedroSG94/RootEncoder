package com.pedro.rtsp.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtcp.SenderReportUdp;
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
  private DatagramPacket[] mPackets;
  private int mPort = -1;

  /**
   * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
   */
  public RtpSocketUdp() {
    super();
    senderReportUdp = new SenderReportUdp();
    senderReportUdp.reset();
    mPackets = new DatagramPacket[mBufferCount];
    for (int i = 0; i < mBufferCount; i++) {
      mPackets[i] = new DatagramPacket(mBuffers[i], 1);
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
    for (int i = 0; i < mBufferCount; i++) {
      setLong(mBuffers[i], ssrc, 8, 12);
    }
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
        mPort = dport;
        for (int i = 0; i < mBufferCount; i++) {
          mPackets[i].setPort(dport);
          mPackets[i].setAddress(InetAddress.getByName(dest));
        }
        senderReportUdp.setDestination(InetAddress.getByName(dest), rtcpPort);
      }
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  /** Sends the RTP packet over the network. */
  @Override
  public void commitBuffer(int length) throws IOException {
    updateSequence();
    mPackets[mBufferIn].setLength(length);
    if (++mBufferIn >= mBufferCount) mBufferIn = 0;
    mBufferCommitted.release();
    if (mThread == null) {
      mThread = new Thread(this);
      mThread.start();
    }
  }

  /** The Thread sends the packets in the FIFO one by one at a constant rate. */
  @Override
  public void run() {
    try {
      while (mBufferCommitted.tryAcquire(4, TimeUnit.SECONDS)) {
        senderReportUdp.update(mPackets[mBufferOut].getLength(),
            (mTimestamps[mBufferOut] / 100L) * (mClock / 1000L) / 10000L, mPort);
        if (mCount++ > 30) {
          Log.i(TAG, "send packet, "
              + mPackets[mBufferOut].getLength()
              + " Size, "
              + mPackets[mBufferOut].getPort()
              + " Port");
          mSocket.send(mPackets[mBufferOut]);
        }
        if (++mBufferOut >= mBufferCount) mBufferOut = 0;
        mBufferRequested.release();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    mThread = null;
    resetFifo();
    senderReportUdp.reset();
  }
}