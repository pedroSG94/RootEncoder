package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp;
import com.pedro.rtsp.rtp.sockets.RtpSocketUdp;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.RtpConstants;
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
  private byte[] stapA;

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
    // NAL units are preceded with 0x00000001
    byteBuffer.get(header, 0, 5);
    ts = bufferInfo.presentationTimeUs * 1000L;
    int naluLength = bufferInfo.size - byteBuffer.position() + 1;
    int type = header[4] & 0x1F;

    if (type == 5) {
      buffer = socket.requestBuffer();
      socket.markNextPacket();
      socket.updateTimestamp(ts);
      System.arraycopy(stapA, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, stapA.length);
      socket.commitBuffer(stapA.length + RtpConstants.RTP_HEADER_LENGTH);
    }

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
  }

  public void setSPSandPPS(byte[] sps, byte[] pps) {
    stapA = new byte[sps.length + pps.length + 5];

    // STAP-A NAL header is 24
    stapA[0] = 24;

    // Write NALU 1 size into the array (NALU 1 is the SPS).
    stapA[1] = (byte) (sps.length >> 8);
    stapA[2] = (byte) (sps.length & 0xFF);

    // Write NALU 2 size into the array (NALU 2 is the PPS).
    stapA[sps.length + 3] = (byte) (pps.length >> 8);
    stapA[sps.length + 4] = (byte) (pps.length & 0xFF);

    // Write NALU 1 into the array, then write NALU 2 into the array.
    System.arraycopy(sps, 0, stapA, 3, sps.length);
    System.arraycopy(pps, 0, stapA, 5 + sps.length, pps.length);
  }
}