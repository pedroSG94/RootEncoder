package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtsp.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 27/11/18.
 *
 * RFC 3640.
 */
public class AacPacket extends BasePacket {

  private AudioPacketCallback audioPacketCallback;

  public AacPacket(int sampleRate, AudioPacketCallback audioPacketCallback) {
    super(sampleRate);
    this.audioPacketCallback = audioPacketCallback;
    channelIdentifier = (byte) 0;
  }

  @Override
  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    int length = bufferInfo.size - byteBuffer.position();
    if (length > 0) {
      byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 4);

      byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 4, length);
      long ts = bufferInfo.presentationTimeUs * 1000;
      markPacket(buffer);
      updateTimeStamp(buffer, ts);

      // AU-headers-length field: contains the size in bits of a AU-header
      // 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta
      // 13 bits will be enough because ADTS uses 13 bits for frame length
      buffer[RtpConstants.RTP_HEADER_LENGTH] = (byte) 0;
      buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = (byte) 0x10;

      // AU-size
      buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = (byte) (length >> 5);
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] = (byte) (length << 3);

      // AU-Index
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] &= 0xF8;
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] |= 0x00;

      updateSeq(buffer);
      RtpFrame rtpFrame =
          new RtpFrame(buffer, ts, RtpConstants.RTP_HEADER_LENGTH + length + 4, rtpPort, rtcpPort,
              channelIdentifier);
      audioPacketCallback.onAudioFrameCreated(rtpFrame);
    }
  }
}
