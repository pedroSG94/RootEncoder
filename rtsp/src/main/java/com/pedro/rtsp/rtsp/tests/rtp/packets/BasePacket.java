package com.pedro.rtsp.rtsp.tests.rtp.packets;

import com.pedro.rtsp.utils.RtpConstants;
import com.pedro.rtsp.utils.SrsAllocator;
import java.util.Random;

public class BasePacket {

  protected final static int maxPacketSize = RtpConstants.MTU - 28;
  protected int seq = 0;
  protected long clock;
  protected int ssrc;
  protected byte channelIdentifier;
  protected int rtpPort;
  protected int rtcpPort;

  public BasePacket(long clock) {
    this.clock = clock;
    ssrc = new Random().nextInt();
  }

  public void setPorts(int rtpPort, int rtcpPort) {
    this.rtpPort = rtpPort;
    this.rtcpPort = rtcpPort;
  }

  public void reset() {
    seq = 0;
    ssrc = new Random().nextInt();
  }

  public SrsAllocator.Allocation getAllocation(SrsAllocator srsAllocator, int size) {
    SrsAllocator.Allocation allocation = srsAllocator.allocate(size);
    allocation.put((byte) Integer.parseInt("10000000", 2), 0);
    allocation.put((byte) RtpConstants.payloadType, 1);
    setLongSSRC(allocation.array(), ssrc);
    return allocation;
  }

  protected void setLongSSRC(byte[] buffer, int ssrc) {
    setLong(buffer, ssrc, 8, 12);
  }

  protected void updateTimeStamp(byte[] buffer, long timestamp) {
    long ts = timestamp * clock / 1000000000L;
    setLong(buffer, ts, 4, 8);
  }

  protected void setLong(byte[] buffer, long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      buffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }

  protected void updateSeq(byte[] buffer) {
    setLong(buffer, ++seq, 2, 4);
  }

  protected void markPacket(byte[] buffer) {
    buffer[1] |= 0x80;
  }

  protected void requestBuffer(byte[] buffer) {
    buffer[1] &= 0x7F;
  }
}
