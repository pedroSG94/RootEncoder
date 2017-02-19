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

      ts = bufferInfo.presentationTimeUs * 1000;
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
