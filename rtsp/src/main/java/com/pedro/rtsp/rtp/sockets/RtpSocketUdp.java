package com.pedro.rtsp.rtp.sockets;

import android.util.Log;
import com.pedro.rtsp.rtsp.RtpFrame;
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

  private MulticastSocket multicastSocketVideo;
  private MulticastSocket multicastSocketAudio;
  private DatagramPacket datagramPacket = new DatagramPacket(new byte[] { 0 }, 1);

  public RtpSocketUdp(int videoSourcePort, int audioSourcePort) {
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
  public void sendFrame(RtpFrame rtpFrame, boolean isEnableLogs) throws IOException {
    sendFrameUDP(rtpFrame, isEnableLogs);
  }

  @Override
  public void close() {
    multicastSocketVideo.close();
    multicastSocketAudio.close();
  }

  private void sendFrameUDP(RtpFrame rtpFrame, boolean isEnableLogs) throws IOException {
    datagramPacket.setData(rtpFrame.getBuffer());
    datagramPacket.setPort(rtpFrame.getRtpPort());
    datagramPacket.setLength(rtpFrame.getLength());
    if (rtpFrame.getChannelIdentifier() == (byte) 2) {
      multicastSocketVideo.send(datagramPacket);
    } else {
      multicastSocketAudio.send(datagramPacket);
    }
    if (isEnableLogs) {
      Log.i(TAG, "wrote packet: "
          + (rtpFrame.getChannelIdentifier() == (byte) 2 ? "Video" : "Audio")
          + ", size: "
          + rtpFrame.getLength()
          + ", port: "
          + rtpFrame.getRtpPort());
    }
  }
}
