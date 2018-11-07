package com.pedro.rtsp.rtsp.tests.rtp.packets;

import com.pedro.rtsp.rtsp.tests.RtpFrame;

public interface AudioPacketCallback {
  void onAudioFrameCreated(RtpFrame rtpFrame);
}
