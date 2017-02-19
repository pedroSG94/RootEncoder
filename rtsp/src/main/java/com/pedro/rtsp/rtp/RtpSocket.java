/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.pedro.rtsp.rtp;

import android.os.SystemClock;
import android.util.Log;
import com.pedro.rtsp.constants.RtpConstants;
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

  public static final String TAG = "RtpSocket";

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

  private AverageBitrate mAverageBitrate;

  /**
   * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
   */
  public RtpSocket() {
    mCacheSize = 0;
    mBufferCount = 300; // TODO: readjust that when the FIFO is full
    mBuffers = new byte[mBufferCount][];
    mPackets = new DatagramPacket[mBufferCount];
    mReport = new SenderReport();
    mAverageBitrate = new AverageBitrate();
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

			/* Payload Type */
      mBuffers[i][1] = (byte) 96;

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
    mAverageBitrate.reset();
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

  /** Puts the buffer back into the FIFO without sending the packet. */
  public void commitBuffer() throws IOException {

    if (mThread == null) {
      mThread = new Thread(this);
      mThread.start();
    }

    if (++mBufferIn >= mBufferCount) mBufferIn = 0;
    mBufferCommitted.release();
  }

  /** Sends the RTP packet over the network. */
  public void commitBuffer(int length) throws IOException {
    updateSequence();
    mPackets[mBufferIn].setLength(length);

    mAverageBitrate.push(length);

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
      Log.e("Pedro", "hi sender " + mCacheSize);
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
            //Log.d(TAG,"permits: "+mBufferCommitted.availablePermits());
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

  /**
   * Computes an average bit rate.
   **/
  protected static class AverageBitrate {

    private final static long RESOLUTION = 200;

    private long mOldNow, mNow, mDelta;
    private long[] mElapsed, mSum;
    private int mCount, mIndex, mTotal;
    private int mSize;

    public AverageBitrate() {
      mSize = 5000 / ((int) RESOLUTION);
      reset();
    }

    public void reset() {
      mSum = new long[mSize];
      mElapsed = new long[mSize];
      mNow = SystemClock.elapsedRealtime();
      mOldNow = mNow;
      mCount = 0;
      mDelta = 0;
      mTotal = 0;
      mIndex = 0;
    }

    public void push(int length) {
      mNow = SystemClock.elapsedRealtime();
      if (mCount > 0) {
        mDelta += mNow - mOldNow;
        mTotal += length;
        if (mDelta > RESOLUTION) {
          mSum[mIndex] = mTotal;
          mTotal = 0;
          mElapsed[mIndex] = mDelta;
          mDelta = 0;
          mIndex++;
          if (mIndex >= mSize) mIndex = 0;
        }
      }
      mOldNow = mNow;
      mCount++;
    }
  }
}
