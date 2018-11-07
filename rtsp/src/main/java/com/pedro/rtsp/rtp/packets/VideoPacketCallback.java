package com.pedro.rtsp.rtp.packets;

import com.pedro.rtsp.rtsp.RtpFrame;

public interface VideoPacketCallback {
  void onVideoFrameCreated(RtpFrame rtpFrame);
}
