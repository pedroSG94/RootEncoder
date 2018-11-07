package com.pedro.rtsp.rtsp.tests.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtsp.tests.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;
import com.pedro.rtsp.utils.SrsAllocator;
import java.nio.ByteBuffer;

public class AacPacket extends BasePacket {

  private SrsAllocator srsAllocator = new SrsAllocator(RtpConstants.MTU);

  public AacPacket(int sampleRate, PacketCallback packetCallback) {
    super(sampleRate, packetCallback);
    channelIdentifier = (byte) 0;
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    int length = maxPacketSize - (RtpConstants.RTP_HEADER_LENGTH + 4)
        < bufferInfo.size - byteBuffer.position() ? maxPacketSize - (RtpConstants.RTP_HEADER_LENGTH
        + 4) : bufferInfo.size - byteBuffer.position();
    if (length > 0) {
      SrsAllocator.Allocation allocation = getAllocation(srsAllocator, bufferInfo.size + 2);
      requestBuffer(allocation.array());
      byteBuffer.get(allocation.array(), RtpConstants.RTP_HEADER_LENGTH + 4, length);
      long ts = bufferInfo.presentationTimeUs * 1000;
      markPacket(allocation.array());
      updateTimeStamp(allocation.array(), ts);

      // AU-headers-length field: contains the size in bits of a AU-header
      // 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta
      // 13 bits will be enough because ADTS uses 13 bits for frame length
      allocation.put((byte) 0, RtpConstants.RTP_HEADER_LENGTH);
      allocation.put((byte) 0x10, RtpConstants.RTP_HEADER_LENGTH + 1);

      // AU-size
      allocation.put((byte) (length >> 5), RtpConstants.RTP_HEADER_LENGTH + 2);
      allocation.put((byte) (length << 3), RtpConstants.RTP_HEADER_LENGTH + 3);

      // AU-Index
      allocation.array()[RtpConstants.RTP_HEADER_LENGTH + 3] &= 0xF8;
      allocation.array()[RtpConstants.RTP_HEADER_LENGTH + 3] |= 0x00;

      updateSeq(allocation.array());
      RtpFrame rtpFrame =
          new RtpFrame(allocation.array(), ts, RtpConstants.RTP_HEADER_LENGTH + length + 4, rtpPort,
              rtcpPort, channelIdentifier);
      packetCallback.onFrameCreated(rtpFrame);
      srsAllocator.release(allocation);
    }
  }
}
