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

import android.media.MediaCodec.BufferInfo;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/02/17.
 *
 * RFC 3640.
 * Encapsulates AAC Access Units in RTP packets as specified in the RFC 3640.
 */
public class AccPacket extends BasePacket {

  private final static String TAG = "AccPacket";

  public AccPacket() {
    super();
    socket.setCacheSize(0);
  }

  public void setSampleRate(int sampleRate) {
    socket.setClockFrequency(sampleRate);
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, BufferInfo bufferInfo) {
    try {
      buffer = socket.requestBuffer();
      int length =
          MAXPACKETSIZE - (rtphl + 4) < bufferInfo.size - byteBuffer.position() ? MAXPACKETSIZE - (
              rtphl
                  + 4) : bufferInfo.size - byteBuffer.position();
      byteBuffer.get(buffer, rtphl + 4, length);
      long oldts = ts;
      ts = bufferInfo.presentationTimeUs * 1000;

      // Seems to happen sometimes
      if (oldts > ts) {
        socket.commitBuffer();
        return;
      }

      socket.markNextPacket();
      socket.updateTimestamp(ts);

      // AU-headers-length field: contains the size in bits of a AU-header
      // 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta
      // 13 bits will be enough because ADTS uses 13 bits for frame length
      buffer[rtphl] = 0;
      buffer[rtphl + 1] = 0x10;

      // AU-size
      buffer[rtphl + 2] = (byte) (length >> 5);
      buffer[rtphl + 3] = (byte) (length << 3);

      // AU-Index
      buffer[rtphl + 3] &= 0xF8;
      buffer[rtphl + 3] |= 0x00;

      send(rtphl + length + 4);
    } catch (IOException | InterruptedException | ArrayIndexOutOfBoundsException e) {
      e.printStackTrace();
    }
  }

  public void close(){
    socket.close();
  }
}
