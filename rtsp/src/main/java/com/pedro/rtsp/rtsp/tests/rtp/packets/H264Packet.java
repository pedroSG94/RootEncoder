package com.pedro.rtsp.rtsp.tests.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtsp.tests.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;
import com.pedro.rtsp.utils.SrsAllocator;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class H264Packet extends BasePacket {

  private SrsAllocator srsAllocator = new SrsAllocator(RtpConstants.MTU);
  private byte[] header = new byte[5];
  private byte[] stapA;
  private List<RtpFrame> videoFrames = new ArrayList<>();

  public H264Packet(byte[] sps, byte[] pps, PacketCallback packetCallback) {
    super(RtpConstants.clockVideoFrequency, packetCallback);
    channelIdentifier = (byte) 2;
    setSPSandPPS(sps, pps);
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    videoFrames.clear();
    byteBuffer.rewind();
    byteBuffer.get(header, 0, 5);
    long ts = bufferInfo.presentationTimeUs * 1000L;
    int naluLength = bufferInfo.size - byteBuffer.position() + 1;
    int type = header[4] & 0x1F;
    if (type == 5) {
      SrsAllocator.Allocation allocation = getAllocation(srsAllocator, RtpConstants.MTU);
      requestBuffer(allocation.array());
      updateTimeStamp(allocation.array(), ts);
      markPacket(allocation.array());
      System.arraycopy(stapA, 0, allocation.array(), RtpConstants.RTP_HEADER_LENGTH, stapA.length);

      updateSeq(allocation.array());
      RtpFrame rtpFrame =
          new RtpFrame(allocation.array(), ts, stapA.length + RtpConstants.RTP_HEADER_LENGTH,
              rtpPort, rtcpPort, channelIdentifier);
      videoFrames.add(rtpFrame);
      srsAllocator.release(allocation);
    }
    // Small NAL unit => Single NAL unit
    if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2) {
      SrsAllocator.Allocation allocation = getAllocation(srsAllocator, RtpConstants.MTU);
      requestBuffer(allocation.array());

      allocation.put(header[4], RtpConstants.RTP_HEADER_LENGTH);
      int cont = naluLength - 1;
      int length = cont < bufferInfo.size - byteBuffer.position() ? cont
          : bufferInfo.size - byteBuffer.position();
      byteBuffer.get(allocation.array(), RtpConstants.RTP_HEADER_LENGTH + 1, length);

      updateTimeStamp(allocation.array(), ts);
      markPacket(allocation.array());

      updateSeq(allocation.array());
      RtpFrame rtpFrame =
          new RtpFrame(allocation.array(), ts, naluLength + RtpConstants.RTP_HEADER_LENGTH, rtpPort,
              rtcpPort, channelIdentifier);
      videoFrames.add(rtpFrame);
      srsAllocator.release(allocation);
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
        SrsAllocator.Allocation allocation = getAllocation(srsAllocator, RtpConstants.MTU);

        requestBuffer(allocation.array());
        allocation.put(header[0], RtpConstants.RTP_HEADER_LENGTH);
        allocation.put(header[1], RtpConstants.RTP_HEADER_LENGTH + 1);
        updateTimeStamp(allocation.array(), ts);
        int cont = naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 2 ?
            maxPacketSize
                - RtpConstants.RTP_HEADER_LENGTH
                - 2 : naluLength - sum;
        int length = cont < bufferInfo.size - byteBuffer.position() ? cont
            : bufferInfo.size - byteBuffer.position();
        byteBuffer.get(allocation.array(), RtpConstants.RTP_HEADER_LENGTH + 2, length);
        sum += length;
        // Last packet before next NAL
        if (sum >= naluLength) {
          // End bit on
          allocation.array()[RtpConstants.RTP_HEADER_LENGTH + 1] += 0x40;
          markPacket(allocation.array());
        }
        updateSeq(allocation.array());
        RtpFrame rtpFrame =
            new RtpFrame(allocation.array(), ts, length + RtpConstants.RTP_HEADER_LENGTH + 2,
                rtpPort, rtcpPort, channelIdentifier);
        videoFrames.add(rtpFrame);
        srsAllocator.release(allocation);
        // Switch start bit
        header[1] = (byte) (header[1] & 0x7F);
      }
    }
    packetCallback.onFrameCreated(videoFrames);
  }

  private void setSPSandPPS(byte[] sps, byte[] pps) {
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
