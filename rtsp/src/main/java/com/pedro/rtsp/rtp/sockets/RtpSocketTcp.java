package com.pedro.rtsp.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtcp.SenderReportTcp;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 24/02/17.
 */
public class RtpSocketTcp extends BaseRtpSocket implements Runnable {

  private SenderReportTcp senderReportTcp;
  private byte mTcpHeader[];
  private int[] lengths;
  private OutputStream mOutputStream = null;

  public RtpSocketTcp() {
    super();
    lengths = new int[mBufferCount];
    senderReportTcp = new SenderReportTcp();
    senderReportTcp.reset();
    mTcpHeader = new byte[] { '$', 0, 0, 0 };
  }

  @Override
  public void setSSRC(int ssrc) {
    for (int i = 0; i < mBufferCount; i++) {
      setLong(mBuffers[i], ssrc, 8, 12);
    }
    senderReportTcp.setSSRC(ssrc);
  }

  public void setOutputStream(OutputStream outputStream, byte channelIdentifier) {
    if (outputStream != null) {
      mOutputStream = outputStream;
      mTcpHeader[1] = channelIdentifier;
      senderReportTcp.setOutputStream(outputStream, (byte) (channelIdentifier + 1));
    }
  }

  /** Sends the RTP packet over the network. */
  @Override
  public void commitBuffer(int length) throws IOException {
    updateSequence();
    lengths[mBufferIn] = length;
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
        senderReportTcp.update(lengths[mBufferOut],
            (mTimestamps[mBufferOut] / 100L) * (mClock / 1000L) / 10000L);
        if (mCount++ > 30) {
          Log.i(TAG, "send packet, " + lengths[mBufferOut] + " Size");
          sendTCP();
        }
        if (++mBufferOut >= mBufferCount) mBufferOut = 0;
        mBufferRequested.release();
      }
    } catch (Exception e) {
      Log.e(TAG, "tcp send error: ", e);
    }
    mThread = null;
    resetFifo();
    senderReportTcp.reset();
  }

  private void sendTCP() throws Exception {
    synchronized (mOutputStream) {
      int len = lengths[mBufferOut];
      mTcpHeader[2] = (byte) (len >> 8);
      mTcpHeader[3] = (byte) (len & 0xFF);
      mOutputStream.write(mTcpHeader);
      mOutputStream.write(mBuffers[mBufferOut], 0, len);
      mOutputStream.flush();
      Log.d(TAG, "send " + len);
    }
  }
}