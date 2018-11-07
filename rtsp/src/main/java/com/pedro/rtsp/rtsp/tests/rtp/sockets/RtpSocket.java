package com.pedro.rtsp.rtsp.tests.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.tests.RtpFrame;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class RtpSocket {

  private final static String TAG = "RtpSocket";
  private Protocol protocol;
  private ConnectCheckerRtsp connectCheckerRtsp;
  //TCP
  private OutputStream outputStream;
  private byte tcpHeader[];
  //UDP
  private MulticastSocket multicastSocket;
  private DatagramPacket datagramPacket = new DatagramPacket(new byte[] { 0 }, 1);

  public RtpSocket(ConnectCheckerRtsp connectCheckerRtsp, Protocol protocol) {
    this.connectCheckerRtsp = connectCheckerRtsp;
    this.protocol = protocol;
    if (protocol == Protocol.UDP) {
      try {
        multicastSocket = new MulticastSocket();
        multicastSocket.setTimeToLive(64);
      } catch (IOException e) {
        Log.e(TAG, "Error", e);
      }
    } else {
      tcpHeader = new byte[] { '$', 0, 0, 0 };
    }
  }

  public void setDataStream(OutputStream outputStream, String host) {
    this.outputStream = outputStream;
    try {
      datagramPacket.setAddress(InetAddress.getByName(host));
    } catch (UnknownHostException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void sendFrame(RtpFrame rtpFrame) {
    try {
      if (protocol == Protocol.TCP) {
        sendFrameTCP(rtpFrame);
      } else {
        sendFrameUDP(rtpFrame);
      }
      Log.i(TAG, "wrote packet: "
          + (rtpFrame.getChannelIdentifier() == (byte) 2 ? "Video" : "Audio")
          + ", size: "
          + rtpFrame.getLength() +  ", port: " + rtpFrame.getRtpPort());
    } catch (IOException e) {
      Log.e(TAG, "TCP send error: ", e);
      connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.getMessage());
    }
  }

  private void sendFrameTCP(RtpFrame rtpFrame) throws IOException {
    synchronized (outputStream) {
      try {
        int len = rtpFrame.getLength();
        tcpHeader[1] = rtpFrame.getChannelIdentifier();
        tcpHeader[2] = (byte) (len >> 8);
        tcpHeader[3] = (byte) (len & 0xFF);
        outputStream.write(tcpHeader);
        outputStream.write(rtpFrame.getBuffer(), 0, len);
        outputStream.flush();
      } catch (ArrayIndexOutOfBoundsException e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  private void sendFrameUDP(RtpFrame rtpFrame) throws IOException {
    datagramPacket.setData(rtpFrame.getBuffer(), 0, rtpFrame.getLength());
    datagramPacket.setLength(rtpFrame.getLength());
    multicastSocket.send(datagramPacket);
  }

  public void close() {
    if (protocol == Protocol.UDP) {
      multicastSocket.close();
    }
  }
}
