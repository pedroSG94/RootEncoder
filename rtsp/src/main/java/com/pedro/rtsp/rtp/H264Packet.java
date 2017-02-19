package com.pedro.rtsp.rtp;

import android.media.MediaCodec;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/02/17.
 *
 * RFC 3984.
 * H264 streaming over RTP.
 */
public class H264Packet extends BasePacket {

  public final static String TAG = "H264Packet";

  byte[] header = new byte[5];

  public H264Packet() {
    super();
    socket.setCacheSize(0);
    socket.setClockFrequency(90000);
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    // We read a NAL units from the input stream and we send them
    try {
      // NAL units are preceeded with 0x00000001
      byteBuffer.get(header, 0, 5);
      ts = bufferInfo.presentationTimeUs * 1000L;
      int naluLength = bufferInfo.size - byteBuffer.position() + 1;

      // Small NAL unit => Single NAL unit
      if (naluLength <= MAXPACKETSIZE - rtphl - 2) {
        buffer = socket.requestBuffer();
        buffer[rtphl] = header[4];
        int cont = naluLength - 1;
        int length = cont < bufferInfo.size - byteBuffer.position() ? cont
            : bufferInfo.size - byteBuffer.position();
        byteBuffer.get(buffer, rtphl + 1, length);
        socket.updateTimestamp(ts);
        socket.markNextPacket();
        send(naluLength + rtphl);
      }
      // Large NAL unit => Split nal unit
      else {
        // Set FU-A header
        header[1] = (byte) (header[4] & 0x1F);  // FU header type
        header[1] += 0x80; // Start bit
        // Set FU-A indicator
        header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
        header[0] += 28;

        int sum = 1;
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
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}