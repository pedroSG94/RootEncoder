package com.pedro.rtsp.rtp.sockets;

import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 24/02/17.
 */

public abstract class BaseRtpSocket implements Runnable {

  protected final String TAG = "RtpSocket";

  protected byte[][] mBuffers;
  protected long[] mTimestamps;
  protected Semaphore mBufferRequested, mBufferCommitted;
  protected Thread mThread;
  protected int mBufferOut;
  protected long mClock = 0;
  protected int mSeq = 0;
  protected int mBufferCount, mBufferIn;
  protected int mCount = 0;

  /**
   * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
   */
  public BaseRtpSocket() {
    mBufferCount = 300;
    mBuffers = new byte[mBufferCount][];
    resetFifo();

    for (int i = 0; i < mBufferCount; i++) {
      mBuffers[i] = new byte[RtpConstants.MTU];
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
  }

  protected void resetFifo() {
    mCount = 0;
    mBufferIn = 0;
    mBufferOut = 0;
    mTimestamps = new long[mBufferCount];
    mBufferRequested = new Semaphore(mBufferCount);
    mBufferCommitted = new Semaphore(0);
  }

  /** Sets the SSRC of the stream. */
  public abstract void setSSRC(int ssrc);

  /** Sets the clock frequency of the stream in Hz. */
  public void setClockFrequency(long clock) {
    mClock = clock;
  }

  /**
   * Returns an available buffer from the FIFO, it can then be modified.
   *
   * @throws InterruptedException
   **/
  public byte[] requestBuffer() throws InterruptedException {
    mBufferRequested.acquire();
    mBuffers[mBufferIn][1] &= 0x7F;
    return mBuffers[mBufferIn];
  }

  /** Increments the sequence number. */
  protected void updateSequence() {
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

  public void commitBuffer() throws IOException {
    if (mThread == null) {
      mThread = new Thread(this);
      mThread.start();
    }
    if (++mBufferIn >= mBufferCount) mBufferIn = 0;
    mBufferCommitted.release();
  }

  public abstract void commitBuffer(int length) throws IOException;

  /** Sets the marker in the RTP packet. */
  public void markNextPacket() {
    mBuffers[mBufferIn][1] |= 0x80;
  }

  protected void setLong(byte[] buffer, long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      buffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }
}