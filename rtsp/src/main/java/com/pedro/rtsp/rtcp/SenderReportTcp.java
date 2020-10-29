package com.pedro.rtsp.rtcp;

import android.util.Log;
import com.pedro.rtsp.rtsp.RtpFrame;
import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by pedro on 8/11/18.
 */

public class SenderReportTcp extends BaseSenderReport {

  private OutputStream outputStream;
  private byte tcpHeader[];

  public SenderReportTcp() {
    super();
    tcpHeader = new byte[] { '$', 0, 0, PACKET_LENGTH };
  }

  @Override
  public void setDataStream(OutputStream outputStream, String host) {
    this.outputStream = outputStream;
  }

  @Override
  public void sendReport(byte[] buffer, RtpFrame rtpFrame, String type, int packetCount,
      int octetCount, boolean isEnableLogs) throws IOException {
    sendReportTCP(buffer, rtpFrame.getChannelIdentifier(), type, packetCount, octetCount,
        isEnableLogs);
  }

  @Override
  public void close() {

  }

  private void sendReportTCP(byte[] buffer, byte channelIdentifier, String type, int packet,
      int octet, boolean isEnableLogs) throws IOException {
    synchronized (RtpConstants.lock) {
      tcpHeader[1] = (byte) (channelIdentifier + 1);
      outputStream.write(tcpHeader);
      outputStream.write(buffer, 0, PACKET_LENGTH);
      outputStream.flush();
      if (isEnableLogs) {
        Log.i(TAG, "wrote report: " + type + ", packets: " + packet + ", octet: " + octet);
      }
    }
  }
}
