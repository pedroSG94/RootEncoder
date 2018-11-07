package com.pedro.rtsp.rtp.packets;

import com.pedro.rtsp.rtsp.RtpFrame;

public interface AudioPacketCallback {
  void onAudioFrameCreated(RtpFrame rtpFrame);
}
