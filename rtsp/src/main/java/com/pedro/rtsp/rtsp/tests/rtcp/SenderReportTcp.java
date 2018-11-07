package com.pedro.rtsp.rtsp.tests.rtcp;

import android.util.Log;
import com.pedro.rtsp.rtsp.tests.RtpFrame;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by pedro on 24/02/17.
 */

public class SenderReportTcp extends BaseSenderReport {

  private final byte[] tcpHeader;
  private OutputStream outputStream = null;
  private ConnectCheckerRtsp connectCheckerRtsp;

  public SenderReportTcp(boolean isVideo, ConnectCheckerRtsp connectCheckerRtsp) {
    super(isVideo);
    this.connectCheckerRtsp = connectCheckerRtsp;
    tcpHeader = new byte[] { '$', 0, 0, PACKET_LENGTH };
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   **/
  @Override
  public void update(RtpFrame rtpFrame) {
    updateSend(rtpFrame.getLength());
    if (shouldSend) {
      if (isVideo && rtpFrame.getChannelIdentifier() == (byte) 2
          || !isVideo && rtpFrame.getChannelIdentifier() == (byte) 0) {
        send(System.nanoTime(), rtpFrame.getTimeStamp(), rtpFrame.getChannelIdentifier());
        shouldSend = false;
      }
    }
  }

  /**
   * Sends the RTCP packet over the network.
   *
   * @param ntpts the NTP timestamp.
   * @param rtpts the RTP timestamp.
   */
  private void send(final long ntpts, final long rtpts, byte channelIdentifier) {
    synchronized (outputStream) {
      try {
        setData(ntpts, rtpts);
        tcpHeader[1] = (byte) (channelIdentifier + 1);
        outputStream.write(tcpHeader);
        outputStream.write(buffer, 0, PACKET_LENGTH);
        outputStream.flush();
        Log.i(TAG, "wrote " + (channelIdentifier == (byte) 2 ? "Video" : "Audio") + " report");
      } catch (IOException e) {
        Log.e(TAG, "send TCP report error", e);
        connectCheckerRtsp.onConnectionFailedRtsp("Error send report, " + e.getMessage());
      }
    }
  }

  public void setOutputStream(OutputStream os) {
    outputStream = os;
  }
}
