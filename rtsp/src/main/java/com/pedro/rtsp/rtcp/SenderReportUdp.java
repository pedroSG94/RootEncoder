package com.pedro.rtsp.rtcp;

import android.util.Log;
import com.pedro.rtsp.rtsp.RtpFrame;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * Created by pedro on 8/11/18.
 */

public class SenderReportUdp extends BaseSenderReport {

  private MulticastSocket multicastSocketVideo;
  private MulticastSocket multicastSocketAudio;
  private DatagramPacket datagramPacket = new DatagramPacket(new byte[] { 0 }, 1);

  public SenderReportUdp(int videoSourcePort, int audioSourcePort) {
    super();
    try {
      multicastSocketVideo = new MulticastSocket(videoSourcePort);
      multicastSocketVideo.setTimeToLive(64);
      multicastSocketAudio = new MulticastSocket(audioSourcePort);
      multicastSocketAudio.setTimeToLive(64);
    } catch (IOException e) {
      Log.e(TAG, "Error", e);
    }
  }

  @Override
  public void setDataStream(OutputStream outputStream, String host) {
    try {
      datagramPacket.setAddress(InetAddress.getByName(host));
    } catch (UnknownHostException e) {
      Log.e(TAG, "Error", e);
    }
  }

  @Override
  public void sendReport(byte[] buffer, RtpFrame rtpFrame, String type, int packetCount,
      int octetCount, boolean isEnableLogs) throws IOException {
    sendReportUDP(buffer, rtpFrame.getRtcpPort(), type, packetCount, octetCount, isEnableLogs);
  }

  @Override
  public void close() {
    multicastSocketVideo.close();
    multicastSocketAudio.close();
  }

  private void sendReportUDP(byte[] buffer, int port, String type, int packet, int octet,
      boolean isEnableLogs) throws IOException {
    datagramPacket.setData(buffer);
    datagramPacket.setPort(port);
    datagramPacket.setLength(PACKET_LENGTH);
    if (type.equals("Video")) {
      multicastSocketVideo.send(datagramPacket);
    } else {
      multicastSocketAudio.send(datagramPacket);
    }
    if (isEnableLogs) {
      Log.i(TAG, "wrote report: "
          + type
          + ", port: "
          + port
          + ", packets: "
          + packet
          + ", octet: "
          + octet);
    }
  }
}
