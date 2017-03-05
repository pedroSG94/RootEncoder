package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtp.packets.BasePacket;
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp;
import com.pedro.rtsp.rtp.sockets.RtpSocketUdp;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/02/17.
 *
 * RFC 3984.
 * H264 streaming over RTP.
 */
public class H264Packet extends BasePacket {

  private final String TAG = "H264Packet";

  //contain header from ByteBuffer (first 5 bytes)
  private byte[] header = new byte[5];

  public H264Packet(RtspClient rtspClient, Protocol protocol) {
    super(rtspClient, protocol);
    socket.setClockFrequency(RtpConstants.clockVideoFrequency);
  }

  public void updateDestinationVideo() {
    if (socket instanceof RtpSocketUdp) {
      ((RtpSocketUdp) socket).setDestination(rtspClient.getHost(), rtspClient.getVideoPorts()[0],
          rtspClient.getVideoPorts()[1]);
    } else {
      ((RtpSocketTcp) socket).setOutputStream(rtspClient.getOutputStream(), (byte) 2);
    }
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    // We read a NAL units from ByteBuffer and we send them
    try {
      // NAL units are preceded with 0x00000001
      byteBuffer.get(header, 0, 5);
      ts = bufferInfo.presentationTimeUs * 1000L;
      int naluLength = bufferInfo.size - byteBuffer.position() + 1;

      // Small NAL unit => Single NAL unit
      if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
        buffer = socket.requestBuffer();
        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[4];
        int cont = naluLength - 1;
        int length = cont < bufferInfo.size - byteBuffer.position() ? cont
            : bufferInfo.size - byteBuffer.position();
        byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, length);
        socket.updateTimestamp(ts);
        socket.markNextPacket();
        socket.commitBuffer(naluLength + RtpConstants.RTP_HEADER_LENGTH);
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
          buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0];
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1];
          socket.updateTimestamp(ts);
          int cont = naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2 ?
              maxPacketSize
                  - RtpConstants.RTP_HEADER_LENGTH
                  - 2 : naluLength - sum;
          int length = cont < bufferInfo.size - byteBuffer.position() ? cont
              : bufferInfo.size - byteBuffer.position();
          byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length);
          if (length < 0) {
            return;
          }
          sum += length;
          // Last packet before next NAL
          if (sum >= naluLength) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 1] += 0x40;
            socket.markNextPacket();
          }
          socket.commitBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2);
          // Switch start bit
          header[1] = (byte) (header[1] & 0x7F);
        }
      }
    } catch (IOException | InterruptedException | IndexOutOfBoundsException e) {
      e.printStackTrace();
    }
  }
}