package com.pedro.rtsp.rtp.packets;

import android.media.MediaCodec;
import com.pedro.rtsp.rtsp.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 28/11/18.
 *
 * RFC 7798.
 */
public class H265Packet extends BasePacket {

  private byte[] header = new byte[6];
  private byte[] stapA;
  private VideoPacketCallback videoPacketCallback;
  private boolean sendKeyFrame = false;

  public H265Packet(byte[] sps, byte[] pps, byte[] vps, VideoPacketCallback videoPacketCallback) {
    super(RtpConstants.clockVideoFrequency);
    this.videoPacketCallback = videoPacketCallback;
    channelIdentifier = (byte) 2;
    setSpsPpsVps(sps, pps, vps);
  }

  @Override
  public void createAndSendPacket(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
    // We read a NAL units from ByteBuffer and we send them
    // NAL units are preceded with 0x00000001
    byteBuffer.rewind();
    byteBuffer.get(header, 0, 6);
    long ts = bufferInfo.presentationTimeUs * 1000L;
    int naluLength = bufferInfo.size - byteBuffer.position() + 1;
    int type = (header[4] >> 1) & 0x3f;
    if (type == RtpConstants.IDR_N_LP || type == RtpConstants.IDR_W_DLP
        || bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
      byte[] buffer = getBuffer(stapA.length + RtpConstants.RTP_HEADER_LENGTH);
      updateTimeStamp(buffer, ts);

      markPacket(buffer); //mark end frame
      System.arraycopy(stapA, 0, buffer, RtpConstants.RTP_HEADER_LENGTH, stapA.length);

      updateSeq(buffer);
      RtpFrame rtpFrame =
          new RtpFrame(buffer, ts, stapA.length + RtpConstants.RTP_HEADER_LENGTH, rtpPort, rtcpPort,
              channelIdentifier);
      videoPacketCallback.onVideoFrameCreated(rtpFrame);
      sendKeyFrame = true;
    }
    if (sendKeyFrame) {
      // Small NAL unit => Single NAL unit
      if (naluLength <= maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3) {
        int cont = naluLength - 1;
        int length = cont < bufferInfo.size - byteBuffer.position() ? cont : bufferInfo.size - byteBuffer.position();
        byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 2);
        //Set PayloadHdr (exact copy of nal unit header)
        buffer[RtpConstants.RTP_HEADER_LENGTH] = header[4];
        buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[5];
        byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 2, length);

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
        //Set PayloadHdr (16bit type=49)
        header[0] = 49 << 1;
        header[1] = 1;
        // Set FU header
        //   +---------------+
        //   |0|1|2|3|4|5|6|7|
        //   +-+-+-+-+-+-+-+-+
        //   |S|E|  FuType   |
        //   +---------------+
        header[2] = (byte) type;  // FU header type
        header[2] += 0x80; // Start bit

        int sum = 1;
        while (sum < naluLength) {
          int cont = naluLength - sum > maxPacketSize - RtpConstants.RTP_HEADER_LENGTH - 3 ?
              maxPacketSize
                  - RtpConstants.RTP_HEADER_LENGTH
                  - 3 : naluLength - sum;
          int length = cont < bufferInfo.size - byteBuffer.position() ? cont : bufferInfo.size - byteBuffer.position();
          byte[] buffer = getBuffer(length + RtpConstants.RTP_HEADER_LENGTH + 3);

          buffer[RtpConstants.RTP_HEADER_LENGTH] = header[0];
          buffer[RtpConstants.RTP_HEADER_LENGTH + 1] = header[1];
          buffer[RtpConstants.RTP_HEADER_LENGTH + 2] = header[2];
          updateTimeStamp(buffer, ts);
          byteBuffer.get(buffer, RtpConstants.RTP_HEADER_LENGTH + 3, length);
          sum += length;
          // Last packet before next NAL
          if (sum >= naluLength) {
            // End bit on
            buffer[RtpConstants.RTP_HEADER_LENGTH + 2] += 0x40;
            markPacket(buffer); //mark end frame
          }
          updateSeq(buffer);
          RtpFrame rtpFrame =
              new RtpFrame(buffer, ts, length + RtpConstants.RTP_HEADER_LENGTH + 3, rtpPort,
                  rtcpPort, channelIdentifier);
          videoPacketCallback.onVideoFrameCreated(rtpFrame);
          // Switch start bit
          header[2] = (byte) (header[2] & 0x7F);
        }
      }
    }
  }

  private void setSpsPpsVps(byte[] sps, byte[] pps, byte[] vps) {
    stapA = new byte[sps.length + pps.length + 6];

    stapA[0] = 48 << 1;
    stapA[1] = 1;

    // Write NALU 1 size into the array (NALU 1 is the SPS).
    stapA[2] = (byte) (sps.length >> 8);
    stapA[3] = (byte) (sps.length & 0xFF);

    // Write NALU 2 size into the array (NALU 2 is the PPS).
    stapA[sps.length + 4] = (byte) (pps.length >> 8);
    stapA[sps.length + 5] = (byte) (pps.length & 0xFF);

    // Write NALU 1 into the array, then write NALU 2 into the array.
    System.arraycopy(sps, 0, stapA, 4, sps.length);
    System.arraycopy(pps, 0, stapA, 6 + sps.length, pps.length);
  }

  @Override
  public void reset() {
    super.reset();
    sendKeyFrame = false;
  }
}
