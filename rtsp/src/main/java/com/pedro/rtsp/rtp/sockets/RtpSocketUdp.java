package com.pedro.rtsp.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtsp.RtpFrame;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * Created by pedro on 7/11/18.
 */

public class RtpSocketUdp extends BaseRtpSocket {

  private MulticastSocket multicastSocket;
  private DatagramPacket datagramPacket = new DatagramPacket(new byte[] { 0 }, 1);

  public RtpSocketUdp(ConnectCheckerRtsp connectCheckerRtsp) {
    super(connectCheckerRtsp);
    try {
      multicastSocket = new MulticastSocket();
      multicastSocket.setTimeToLive(64);
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
  public void sendFrame(RtpFrame rtpFrame) {
    try {
      sendFrameUDP(rtpFrame);
    } catch (IOException e) {
      Log.e(TAG, "TCP send error: ", e);
      connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.getMessage());
    }
  }

  @Override
  public void close() {
    multicastSocket.close();
  }

  private void sendFrameUDP(RtpFrame rtpFrame) throws IOException {
    datagramPacket.setData(rtpFrame.getBuffer());
    datagramPacket.setPort(rtpFrame.getRtpPort());
    datagramPacket.setLength(rtpFrame.getLength());
    multicastSocket.send(datagramPacket);
    Log.i(TAG, "wrote packet: "
        + (rtpFrame.getChannelIdentifier() == (byte) 2 ? "Video" : "Audio")
        + ", size: "
        + rtpFrame.getLength()
        + ", port: "
        + rtpFrame.getRtpPort());
  }
}
