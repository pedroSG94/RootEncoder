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

import android.media.MediaCodec;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * RFC 3984.
 *
 * H.264 streaming over RTP.
 *
 * Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 * The stream must start with mpeg4 or 3gpp header, it will be skipped.
 */
public class H264Packet extends BasePacket {

  public final static String TAG = "H264Packet";

  private long delay = 0;
  private Statistics stats = new Statistics();
  private byte[] sps = null, pps = null, stapa = null;
  byte[] header = new byte[5];
  private int count = 0;

  public H264Packet() {
    super();
    socket.setCacheSize(0);
    socket.setClockFrequency(90000);
  }

  public void setStreamParameters(byte[] pps, byte[] sps) {
    this.pps = pps;
    this.sps = sps;

    // A STAP-A NAL (NAL type 24) containing the sps and pps of the stream
    if (pps != null && sps != null) {
      // STAP-A NAL header + NALU 1 (SPS) size + NALU 2 (PPS) size = 5 bytes
      stapa = new byte[sps.length + pps.length + 5];

      // STAP-A NAL header is 24
      stapa[0] = 24;

      // Write NALU 1 size into the array (NALU 1 is the SPS).
      stapa[1] = (byte) (sps.length >> 8);
      stapa[2] = (byte) (sps.length & 0xFF);

      // Write NALU 2 size into the array (NALU 2 is the PPS).
      stapa[sps.length + 3] = (byte) (pps.length >> 8);
      stapa[sps.length + 4] = (byte) (pps.length & 0xFF);

      // Write NALU 1 into the array, then write NALU 2 into the array.
      System.arraycopy(sps, 0, stapa, 3, sps.length);
      System.arraycopy(pps, 0, stapa, 5 + sps.length, pps.length);
    }
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    long duration;
    stats.reset();
    count = 0;
    long oldtime = System.nanoTime();
    // We read a NAL units from the input stream and we send them
    try {
      send(byteBuffer, bufferInfo);
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
    // We measure how long it took to receive NAL units from the phone
    duration = System.nanoTime() - oldtime;

    stats.push(duration);
    // Computes the average duration of a NAL unit
    delay = stats.average();
  }

  /**
   * Reads a NAL unit in the FIFO and sends it.
   * If it is too big, we split it in FU-A units (RFC 3984).
   */
  private void send(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
      throws IOException, InterruptedException {
    int sum = 1, type;

    // NAL units are preceeded with 0x00000001
    byteBuffer.get(header, 0, 5);
    ts = bufferInfo.presentationTimeUs * 1000L;
    ts += delay;
    int naluLength = bufferInfo.size - byteBuffer.position() + 1;

    // Parses the NAL unit type
    type = header[4] & 0x1F;

    // The stream already contains NAL unit type 7 or 8, we don't need
    // to add them to the stream ourselves
    if (type == 7 || type == 8) {
      count++;
      if (count > 4) {
        sps = null;
        pps = null;
      }
    }

    // We send two packets containing NALU type 7 (SPS) and 8 (PPS)
    // Those should allow the H264 stream to be decoded even if no SDP was sent to the decoder.
    if (type == 5 && sps != null && pps != null) {
      buffer = socket.requestBuffer();
      socket.markNextPacket();
      socket.updateTimestamp(ts);
      System.arraycopy(stapa, 0, buffer, rtphl, stapa.length);
      send(rtphl + stapa.length);
    }

    // Small NAL unit => Single NAL unit
    if (naluLength <= MAXPACKETSIZE - rtphl - 2) {
      buffer = socket.requestBuffer();
      buffer[rtphl] = header[4];
      int length = naluLength - 1 < bufferInfo.size - byteBuffer.position() ? naluLength - 1
          : bufferInfo.size - byteBuffer.position();
      byteBuffer.get(buffer, rtphl + 1, length);
      socket.markNextPacket();
      send(naluLength + rtphl);
      //Log.d(TAG,"----- Single NAL unit - len:"+len+" delay: "+delay);
    }
    // Large NAL unit => Split nal unit
    else {
      // Set FU-A header
      header[1] = (byte) (header[4] & 0x1F);  // FU header type
      header[1] += 0x80; // Start bit
      // Set FU-A indicator
      header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
      header[0] += 28;

      while (sum < naluLength) {
        buffer = socket.requestBuffer();
        buffer[rtphl] = header[0];
        buffer[rtphl + 1] = header[1];
        socket.updateTimestamp(ts);
        int cont = naluLength - sum > MAXPACKETSIZE - rtphl - 2 ? MAXPACKETSIZE - rtphl - 2
            : naluLength - sum;
        int length = cont < bufferInfo.size - byteBuffer.position() ? cont
            : bufferInfo.size - byteBuffer.position();
        byteBuffer.get(buffer, rtphl + 2, length);
        if (length < 0) {
          return;
        }
        sum += length;
        // Last packet before next NAL
        if (sum >= naluLength) {
          // End bit on
          buffer[rtphl + 1] += 0x40;
          socket.markNextPacket();
        }
        send(length + rtphl + 2);
        // Switch start bit
        header[1] = (byte) (header[1] & 0x7F);
      }
    }
  }

  /** Used in packetizers to estimate timestamps in RTP packets. */
  private static class Statistics {

    public final static String TAG = "Statistics";

    private int count = 700, c = 0;
    private float m = 0, q = 0;
    private long elapsed = 0;
    private long start = 0;
    private long duration = 0;
    private long period = 10000000000L;
    private boolean initOffset = false;

    public Statistics() {
    }

    public void reset() {
      initOffset = false;
      q = 0;
      m = 0;
      c = 0;
      elapsed = 0;
      start = 0;
      duration = 0;
    }

    public void push(long value) {
      elapsed += value;
      if (elapsed > period) {
        elapsed = 0;
        long now = System.nanoTime();
        if (!initOffset || (now - start < 0)) {
          start = now;
          duration = 0;
          initOffset = true;
        }
        // Prevents drifting issues by comparing the real duration of the
        // stream with the sum of all temporal lengths of RTP packets.
        value += (now - start) - duration;
        //Log.d(TAG, "sum1: "+duration/1000000+" sum2: "+(now-start)/1000000+" drift: "+((now-start)-duration)/1000000+" v: "+value/1000000);
      }
      if (c < 5) {
        // We ignore the first 20 measured values because they may not be accurate
        c++;
        m = value;
      } else {
        m = (m * q + value) / (q + 1);
        if (q < count) q++;
      }
    }

    public long average() {
      long l = (long) m;
      duration += l;
      return l;
    }
  }
}