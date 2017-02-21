package com.pedro.rtsp.rtp;

import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by pedro on 19/02/17.
 *
 * All packets inherits from this one and therefore uses UDP.
 */
public abstract class BasePacket {

  //used on all packets
  protected final static int maxPacketSize = RtpConstants.MTU - 28;
  protected RtpSocket socket = null;
  protected byte[] buffer;
  protected long ts;
  protected RtspClient rtspClient;

  public BasePacket(RtspClient rtspClient) {
    this.rtspClient = rtspClient;
    ts = new Random().nextInt();
    socket = new RtpSocket();
    socket.setSSRC(new Random().nextInt());
    try {
      socket.setTimeToLive(64);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void close(){
    socket.close();
  }
}
