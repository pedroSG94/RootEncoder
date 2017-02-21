package com.pedro.rtsp.rtp;

import android.util.Log;
import com.pedro.rtsp.utils.RtpConstants;
import com.pedro.rtsp.rtcp.SenderReport;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 19/02/17.
 *
 * A basic implementation of an RTP socket.
 * It implements a buffering mechanism, relying on a FIFO of buffers and a Thread.
 * That way, if a packetizer tries to send many packets too quickly, the FIFO will
 * grow and packets will be sent one by one smoothly.
 */
public class RtpSocket implements Runnable {

  private final String TAG = "RtpSocket";

  private MulticastSocket mSocket;
  private DatagramPacket[] mPackets;
  private byte[][] mBuffers;
  private long[] mTimestamps;

  private SenderReport mReport;
  private Semaphore mBufferRequested, mBufferCommitted;
  private Thread mThread;

  private long mCacheSize;
  private long mClock = 0;
  private long mOldTimestamp = 0;
  private int mSeq = 0, mPort = -1;
  private int mBufferCount, mBufferIn, mBufferOut;
  private int mCount = 0;

  /**
   * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
   */
  public RtpSocket() {
    mCacheSize = 0;
    mBufferCount = 300;
    mBuffers = new byte[mBufferCount][];
    mPackets = new DatagramPacket[mBufferCount];
    mReport = new SenderReport();
    resetFifo();

    for (int i = 0; i < mBufferCount; i++) {
      mBuffers[i] = new byte[RtpConstants.MTU];
      mPackets[i] = new DatagramPacket(mBuffers[i], 1);

			/*							     Version(2)  Padding(0)					 					*/
      /*									 ^		  ^			Extension(0)						*/
      /*									 |		  |				^								*/
      /*									 | --------				|								*/
			/*									 | |---------------------								*/
			/*									 | ||  -----------------------> Source Identifier(0)	*/
			/*									 | ||  |												*/
      mBuffers[i][0] = (byte) Integer.parseInt("10000000", 2);
      mBuffers[i][1] = (byte) RtpConstants.playLoadType;

			/* Byte 2,3        ->  Sequence Number                   */
			/* Byte 4,5,6,7    ->  Timestamp                         */
			/* Byte 8,9,10,11  ->  Sync Source Identifier            */
    }
    try {
      mSocket = new MulticastSocket();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void resetFifo() {
    mCount = 0;
    mBufferIn = 0;
    mBufferOut = 0;
    mTimestamps = new long[mBufferCount];
    mBufferRequested = new Semaphore(mBufferCount);
    mBufferCommitted = new Semaphore(0);
    mReport.reset();
  }

  /** Closes the underlying socket. */
  public void close() {
    mSocket.close();
    mReport.close();
  }

  /** Sets the SSRC of the stream. */
  public void setSSRC(int ssrc) {
    for (int i = 0; i < mBufferCount; i++) {
      setLong(mBuffers[i], ssrc, 8, 12);
    }
    mReport.setSSRC(ssrc);
  }

  /** Sets the clock frequency of the stream in Hz. */
  public void setClockFrequency(long clock) {
    mClock = clock;
  }

  /** Sets the size of the FIFO in ms. */
  public void setCacheSize(long cacheSize) {
    mCacheSize = cacheSize;
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
        mReport.setDestination(InetAddress.getByName(dest), rtcpPort);
      }
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns an available buffer from the FIFO, it can then be modified.
   * Call {@link #commitBuffer(int)} to send it over the network.
   *
   * @throws InterruptedException
   **/
  public byte[] requestBuffer() throws InterruptedException {
    mBufferRequested.acquire();
    mBuffers[mBufferIn][1] &= 0x7F;
    return mBuffers[mBufferIn];
  }

  /** Sends the RTP packet over the network. */
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

  /** Increments the sequence number. */
  private void updateSequence() {
    setLong(mBuffers[mBufferIn], ++mSeq, 2, 4);
  }

  /**
   * Overwrites the timestamp in the packet.
   *
   * @param timestamp The new timestamp in ns.
   **/
  public void updateTimestamp(long timestamp) {
    mTimestamps[mBufferIn] = timestamp;
    setLong(mBuffers[mBufferIn], (timestamp / 100L) * (mClock / 1000L) / 10000L, 4, 8);
  }

  /** Sets the marker in the RTP packet. */
  public void markNextPacket() {
    mBuffers[mBufferIn][1] |= 0x80;
  }

  /** The Thread sends the packets in the FIFO one by one at a constant rate. */
  @Override
  public void run() {
    Statistics stats = new Statistics(50, 3000);
    try {
      // Caches mCacheSize milliseconds of the stream in the FIFO.
      Thread.sleep(mCacheSize);
      long delta = 0;
      while (mBufferCommitted.tryAcquire(4, TimeUnit.SECONDS)) {
        if (mOldTimestamp != 0) {
          // We use our knowledge of the clock rate of the stream and the difference between two timestamps to
          // compute the time lapse that the packet represents.
          if ((mTimestamps[mBufferOut] - mOldTimestamp) > 0) {
            stats.push(mTimestamps[mBufferOut] - mOldTimestamp);
            long d = stats.average() / 1000000;
            // We ensure that packets are sent at a constant and suitable rate no matter how the RtpSocket is used.
            if (mCacheSize > 0) Thread.sleep(d);
          } else if ((mTimestamps[mBufferOut] - mOldTimestamp) < 0) {
            Log.e(TAG, "TS: " + mTimestamps[mBufferOut] + " OLD: " + mOldTimestamp);
          }
          delta += mTimestamps[mBufferOut] - mOldTimestamp;
          if (delta > 500000000 || delta < 0) {
            delta = 0;
          }
        }
        mReport.update(mPackets[mBufferOut].getLength(),
            (mTimestamps[mBufferOut] / 100L) * (mClock / 1000L) / 10000L, mPort);
        mOldTimestamp = mTimestamps[mBufferOut];
        if (mCount++ > 30) {
          Log.i(TAG, "send packet, " + mPackets[mBufferOut].getLength() + " Size, " + mPackets[mBufferOut].getPort() + " Port");
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
  }

  private void setLong(byte[] buffer, long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      buffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }
}
