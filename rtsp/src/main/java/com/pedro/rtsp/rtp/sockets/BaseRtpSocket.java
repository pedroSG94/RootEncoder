package com.pedro.rtsp.rtp.sockets;

import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtpFrame;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by pedro on 7/11/18.
 */

public abstract class BaseRtpSocket {

  protected final static String TAG = "BaseRtpSocket";

  public static BaseRtpSocket getInstance(Protocol protocol, int videoSourcePort,
      int audioSourcePort) {
    return protocol == Protocol.TCP ? new RtpSocketTcp()
        : new RtpSocketUdp(videoSourcePort, audioSourcePort);
  }

  public abstract void setDataStream(OutputStream outputStream, String host);

  public abstract void sendFrame(RtpFrame rtpFrame, boolean isEnableLogs) throws IOException;

  public abstract void close();
}
