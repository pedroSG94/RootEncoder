package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec.BufferInfo;
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp;
import com.pedro.rtsp.rtp.sockets.RtpSocketUdp;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.RtpConstants;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/02/17.
 *
 * RFC 3640.
 * Encapsulates AAC Access Units in RTP packets as specified in the RFC 3640.
 */
public class AacPacket extends BasePacket {

  private final String TAG = "AacPacket";

  public AacPacket(RtspClient rtspClient, Protocol protocol) {
    super(rtspClient, protocol);
  }

  public void updateDestinationAudio() {
    if (socket instanceof RtpSocketUdp) {
      ((RtpSocketUdp) socket).setDestination(rtspClient.getHost(), rtspClient.getAudioPorts()[0],
          rtspClient.getAudioPorts()[1]);
    } else {
      ((RtpSocketTcp) socket).setOutputStream(rtspClient.getOutputStream(), (byte) 0);
    }
  }

  public void setSampleRate(int sampleRate) {
    socket.setClockFrequency(sampleRate);
  }

  public void createAndSendPacket(ByteBuffer byteBuffer, BufferInfo bufferInfo) {
    buffer = socket.requestBuffer();
    int length = maxPacketSize - (RtpConstants.RTP_HEADER_LENGTH + 4)
        < bufferInfo.size - byteBuffer.position() ? maxPacketSize - (RtpConstants.RTP_HEADER_LENGTH
        + 4) : bufferInfo.size - byteBuffer.position();
    if (length > 0) {
      byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 4, length);
      ts = bufferInfo.presentationTimeUs * 1000;
      socket.markNextPacket();
      socket.updateTimestamp(ts);

      // AU-headers-length field: contains the size in bits of a AU-header
      // 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta
      // 13 bits will be enough because ADTS uses 13 bits for frame length
      buffer[RtpConstants.RTP_HEADER_LENGTH] = 0;
      buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = 0x10;

      // AU-size
      buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = (byte) (length >> 5);
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] = (byte) (length << 3);

      // AU-Index
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] &= 0xF8;
      buffer[RtpConstants.RTP_HEADER_LENGTH + 3] |= 0x00;

      socket.commitBuffer(RtpConstants.RTP_HEADER_LENGTH + length + 4);
    }
  }
}
