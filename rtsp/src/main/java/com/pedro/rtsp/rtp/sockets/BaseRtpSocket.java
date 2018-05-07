package com.pedro.rtsp.rtp.sockets;

import com.pedro.rtsp.utils.RtpConstants;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 24/02/17.
 */

public abstract class BaseRtpSocket implements Runnable {

  protected final String TAG = "RtpSocket";

  protected byte[][] buffers;
  protected long[] timestamps;
  protected Semaphore bufferRequested, bufferCommitted;
  protected Thread thread;
  protected int bufferOut;
  protected long clock = 0;
  protected int seq = 0;
  protected int bufferCount, bufferIn;
  protected boolean running;

  /**
   * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
   */
  public BaseRtpSocket() {
    running = true;
    bufferCount = 300;
    buffers = new byte[bufferCount][];
    resetFifo();

    for (int i = 0; i < bufferCount; i++) {
      buffers[i] = new byte[RtpConstants.MTU];
      /*							     Version(2)  Padding(0)					 					*/
      /*									 ^		  ^			Extension(0)						*/
      /*									 |		  |				^								*/
      /*									 | --------				|								*/
      /*									 | |---------------------								*/
      /*									 | ||  -----------------------> Source Identifier(0)	*/
      /*									 | ||  |												*/
      buffers[i][0] = (byte) Integer.parseInt("10000000", 2);
      buffers[i][1] = (byte) RtpConstants.payloadType;

			/* Byte 2,3        ->  Sequence Number                   */
      /* Byte 4,5,6,7    ->  Timestamp                         */
      /* Byte 8,9,10,11  ->  Sync Source Identifier            */
    }
  }

  protected void resetFifo() {
    bufferIn = 0;
    bufferOut = 0;
    timestamps = new long[bufferCount];
    bufferRequested = new Semaphore(bufferCount);
    bufferCommitted = new Semaphore(0);
  }

  public void reset(boolean running) {
    this.running = running;
    bufferCommitted.drainPermits();
    bufferRequested.drainPermits();
    resetFifo();
  }

  /** Sets the SSRC of the stream. */
  public abstract void setSSRC(int ssrc);

  protected void setLongSSRC(int ssrc) {
    for (int i = 0; i < bufferCount; i++) {
      setLong(buffers[i], ssrc, 8, 12);
    }
  }

  /** Sets the clock frequency of the stream in Hz. */
  public void setClockFrequency(long clock) {
    this.clock = clock;
  }

  /**
   * Returns an available buffer from the FIFO, it can then be modified.
   *
   **/
  public byte[] requestBuffer() {
    try {
      bufferRequested.acquire();
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      try {
        Thread.currentThread().join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    buffers[bufferIn][1] &= 0x7F;
    return buffers[bufferIn];
  }

  /**
   * Overwrites the timestamp in the packet.
   *
   * @param timestamp The new timestamp in ns.
   **/
  public void updateTimestamp(long timestamp) {
    long ts = timestamp * clock / 1000000000L;
    timestamps[bufferIn] = ts;
    setLong(buffers[bufferIn], ts, 4, 8);
  }

  /** Sends the RTP packet over the network. */
  public void commitBuffer(int length) {
    //Increments the sequence number.
    setLong(buffers[bufferIn], ++seq, 2, 4);
    implementCommitBuffer(length);
    if (++bufferIn >= bufferCount) bufferIn = 0;
    bufferCommitted.release();
    if (thread == null) {
      thread = new Thread(this);
      thread.start();
    }
  }

  protected abstract void implementCommitBuffer(int length);

  /** Sets the marker in the RTP packet. */
  public void markNextPacket() {
    buffers[bufferIn][1] |= 0x80;
  }

  protected void setLong(byte[] buffer, long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      buffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }
}