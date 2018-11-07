package com.pedro.rtsp.rtsp.tests.rtp.packets;

import com.pedro.rtsp.rtsp.tests.RtpFrame;

public interface VideoPacketCallback {
  void onVideoFrameCreated(RtpFrame rtpFrame);
}
