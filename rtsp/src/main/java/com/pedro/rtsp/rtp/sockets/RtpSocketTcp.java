package com.pedro.rtsp.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtcp.SenderReportTcp;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 24/02/17.
 */
public class RtpSocketTcp extends BaseRtpSocket implements Runnable {

  private SenderReportTcp senderReportTcp;
  private byte tcpHeader[];
  private int[] lengths;
  private OutputStream outputStream = null;
  private ConnectCheckerRtsp connectCheckerRtsp;

  public RtpSocketTcp(ConnectCheckerRtsp connectCheckerRtsp) {
    super();
    this.connectCheckerRtsp = connectCheckerRtsp;
    lengths = new int[bufferCount];
    senderReportTcp = new SenderReportTcp(connectCheckerRtsp);
    senderReportTcp.reset();
    tcpHeader = new byte[] { '$', 0, 0, 0 };
  }

  @Override
  public void setSSRC(int ssrc) {
    setLongSSRC(ssrc);
    senderReportTcp.setSSRC(ssrc);
  }

  public void setOutputStream(OutputStream outputStream, byte channelIdentifier) {
    if (outputStream != null) {
      this.outputStream = outputStream;
      tcpHeader[1] = channelIdentifier;
      senderReportTcp.setOutputStream(outputStream, (byte) (channelIdentifier + 1));
    }
  }

  /** Sends the RTP packet over the network. */
  @Override
  public void implementCommitBuffer(int length) {
    lengths[bufferIn] = length;
  }

  /** The Thread sends the packets in the FIFO one by one at a constant rate. */
  @Override
  public void run() {
    try {
      while (bufferCommitted.tryAcquire(4, TimeUnit.SECONDS)) {
        senderReportTcp.update(lengths[bufferOut], timestamps[bufferOut]);
        sendTCP();
        if (++bufferOut >= bufferCount) bufferOut = 0;
        bufferRequested.release();
      }
    } catch (IOException | InterruptedException e) {
      Log.e(TAG, "TCP send error: ", e);
      connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.getMessage());
    }
    thread = null;
    resetFifo();
    senderReportTcp.reset();
  }

  private void sendTCP() throws IOException {
    synchronized (outputStream) {
      if (running) {
        int len = lengths[bufferOut];
        tcpHeader[2] = (byte) (len >> 8);
        tcpHeader[3] = (byte) (len & 0xFF);
        outputStream.write(tcpHeader);
        outputStream.write(buffers[bufferOut], 0, len);
        outputStream.flush();
        Log.i(TAG, "send packet, " + len + " Size");
      }
    }
  }
}