package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtsp.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3984
 */
public class H264Packet extends BasePacket {

  private byte[] header = new byte[5];
  private byte[] stapA;
  private VideoPacketCallback videoPacketCallback;

  public H264Packet(byte[] sps, byte[] pps, VideoPacketCallback videoPacketCallback) {
    super(RtpConstants.clockVideoFrequency);
    this.videoPacketCallback = videoPacketCallback;
    channelIdentifier = (byte) 2;
    setSpsPps(sps, pps);
  }

  @Override
  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    byteBuffer.rewind();
    byteBuffer.get(header, 0, 5);
    long ts = bufferInfo.presentationTimeUs * 1000L;
    int naluLength = bufferInfo.size - byteBuffer.position() + 1;
    int type = header[4] & 0x1F;
    if (type == 5) {
      byte[] buffer = getBuffer(stapA.length + RtpConstants.RTP_HEADER_LENGTH);
      updateTimeStamp(buffer, ts);

      markPacket(buffer); //mark end frame
      System.arraycopy(stapA, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, stapA.length);

      updateSeq(buffer);
      RtpFrame rtpFrame =
          new RtpFrame(buffer, ts, stapA.length + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort,
              channelIdentifier);
      videoPacketCallback.onVideoFrameCreated(rtpFrame);
    }
    // Small NAL unit => Single NAL unit
    if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
      int cont = naluLength - 1;
      int length = cont < bufferInfo.size - byteBuffer.position() ? cont
          : bufferInfo.size - byteBuffer.position();
      byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 1);

      buffer[RtpConstants.RTP_HEADER_LENGTH] = header[4];
      byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 1, length);

      updateTimeStamp(buffer, ts);
      markPacket(buffer); //mark end frame

      updateSeq(buffer);
      RtpFrame rtpFrame =
          new RtpFrame(buffer, ts, naluLength + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort,
              channelIdentifier);
      videoPacketCallback.onVideoFrameCreated(rtpFrame);
    }
    // Large NAL unit => Split nal unit
    else {
      // Set FU-A header
      header[1] = (byte) (header[4] & 0x1F);  // FU header type
      header[1] += 0x80; // set start bit to 1
      // Set FU-A indicator
      header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
      header[0] += 28;

      int sum = 1;
      while (sum < naluLength) {
        int cont = naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2 ?
            maxPacketSize
                - RtpConstants.RTP_HEADER_LENGTH
                - 2 : naluLength - sum;
        int length = cont < bufferInfo.size - byteBuffer.position() ? cont
            : bufferInfo.size - byteBuffer.position();
        byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2);

        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0];
        buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1];
        updateTimeStamp(buffer, ts);
        byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length);
        sum += length;
        // Last packet before next NAL
        if (sum >= naluLength) {
          // End bit on
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] += 0x40;
          markPacket(buffer); //mark end frame
        }
        updateSeq(buffer);
        RtpFrame rtpFrame =
            new RtpFrame(buffer, ts, length + RtpConstants.RTP_HEADER_LENGTH + 2, rtpPort, rtcpPort,
                channelIdentifier);
        videoPacketCallback.onVideoFrameCreated(rtpFrame);
        // Switch start bit
        header[1] = (byte) (header[1] & 0x7F);
      }
    }
  }

  private void setSpsPps(byte[] sps, byte[] pps) {
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
