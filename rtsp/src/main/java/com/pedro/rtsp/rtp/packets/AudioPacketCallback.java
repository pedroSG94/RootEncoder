package com.pedro.rtsp.rtp.packets;

import com.pedro.rtsp.rtsp.RtpFrame;

/**
 * Created by pedro on 7/11/18.
 */

public interface AudioPacketCallback {
  void onAudioFrameCreated(RtpFrame rtpFrame);
}
