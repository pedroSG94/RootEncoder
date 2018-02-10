package com.pedro.rtsp.rtp.packets;

import com.pedro.rtsp.rtp.sockets.BaseRtpSocket;
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp;
import com.pedro.rtsp.rtp.sockets.RtpSocketUdp;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.util.Random;

/**
 * Created by pedro on 19/02/17.
 *
 * All packets inherits from this one and therefore uses UDP.
 */
public abstract class BasePacket {

  //used on all packets
  protected final static int maxPacketSize = RtpConstants.MTU - 28;
  protected BaseRtpSocket socket = null;
  protected byte[] buffer;
  protected long ts;
  protected RtspClient rtspClient;

  public BasePacket(RtspClient rtspClient, Protocol protocol) {
    this.rtspClient = rtspClient;
    ts = new Random().nextInt();
    if (protocol == Protocol.UDP) {
      socket = new RtpSocketUdp(rtspClient.getConnectCheckerRtsp());
    } else {
      socket = new RtpSocketTcp(rtspClient.getConnectCheckerRtsp());
    }
    socket.setSSRC(new Random().nextInt());
    if (socket instanceof RtpSocketUdp) {
      try {
        ((RtpSocketUdp) socket).setTimeToLive(64);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void close() {
    socket.reset(false);
    if (socket instanceof RtpSocketUdp) {
      ((RtpSocketUdp) socket).close();
    }
  }
}
