package com.pedro.rtsp.rtsp.tests.rtp.packets;

import com.pedro.rtsp.rtsp.tests.RtpFrame;
import java.util.List;

public interface VideoPacketCallback {
  void onVideoFramesCreated(List<RtpFrame> rtpFrames);
}
