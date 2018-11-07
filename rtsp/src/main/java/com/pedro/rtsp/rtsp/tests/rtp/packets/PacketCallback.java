package com.pedro.rtsp.rtsp.tests.rtp.packets;

import com.pedro.rtsp.rtsp.tests.RtpFrame;
import java.util.List;

public interface PacketCallback {

  void onFrameCreated(RtpFrame rtpFrame);

  void onFrameCreated(List<RtpFrame> rtpFrames);
}
