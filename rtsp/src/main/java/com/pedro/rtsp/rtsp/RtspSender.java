package com.pedro.rtsp.rtsp;

import android.media.MediaCodec;
import android.util.Log;
import com.pedro.rtsp.rtcp.BaseSenderReport;
import com.pedro.rtsp.rtp.packets.AacPacket;
import com.pedro.rtsp.rtp.packets.AudioPacketCallback;
import com.pedro.rtsp.rtp.packets.BasePacket;
import com.pedro.rtsp.rtp.packets.H264Packet;
import com.pedro.rtsp.rtp.packets.H265Packet;
import com.pedro.rtsp.rtp.packets.VideoPacketCallback;
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket;
import com.pedro.rtsp.utils.BitrateManager;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 7/11/18.
 */

public class RtspSender implements VideoPacketCallback, AudioPacketCallback {

  private final static String TAG = "RtspSender";
  private BasePacket videoPacket;
  private AacPacket aacPacket;
  private BaseRtpSocket rtpSocket;
  private BaseSenderReport baseSenderReport;
  private volatile BlockingQueue<RtpFrame> rtpFrameBlockingQueue =
      new LinkedBlockingQueue<>(getDefaultCacheSize());
  private Thread thread;
  private ConnectCheckerRtsp connectCheckerRtsp;
  private long audioFramesSent = 0;
  private long videoFramesSent = 0;
  private long droppedAudioFrames = 0;
  private long droppedVideoFrames = 0;
  private BitrateManager bitrateManager;
  private boolean isEnableLogs = true;

  public RtspSender(ConnectCheckerRtsp connectCheckerRtsp) {
    this.connectCheckerRtsp = connectCheckerRtsp;
    bitrateManager = new BitrateManager(connectCheckerRtsp);
  }

  public void setSocketsInfo(Protocol protocol, int[] videoSourcePorts, int[] audioSourcePorts) {
    rtpSocket = BaseRtpSocket.getInstance(protocol, videoSourcePorts[0], audioSourcePorts[0]);
    baseSenderReport =
        BaseSenderReport.getInstance(protocol, videoSourcePorts[1], audioSourcePorts[1]);
  }

  public void setVideoInfo(byte[] sps, byte[] pps, byte[] vps) {
    videoPacket =
        vps == null ? new H264Packet(sps, pps, this) : new H265Packet(sps, pps, vps, this);
  }

  public void setAudioInfo(int sampleRate) {
    aacPacket = new AacPacket(sampleRate, this);
  }

  /**
   * @return number of packets
   */
  private int getDefaultCacheSize() {
    return 10 * 1024 * 1024 / RtpConstants.MTU;
  }

  public void setDataStream(OutputStream outputStream, String host) {
    rtpSocket.setDataStream(outputStream, host);
    baseSenderReport.setDataStream(outputStream, host);
  }

  public void setVideoPorts(int rtpPort, int rtcpPort) {
    videoPacket.setPorts(rtpPort, rtcpPort);
  }

  public void setAudioPorts(int rtpPort, int rtcpPort) {
    aacPacket.setPorts(rtpPort, rtcpPort);
  }

  public void sendVideoFrame(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    if (videoPacket != null) videoPacket.createAndSendPacket(h264Buffer, info);
  }

  public void sendAudioFrame(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (aacPacket != null) aacPacket.createAndSendPacket(aacBuffer, info);
  }

  @Override
  public void onVideoFrameCreated(RtpFrame rtpFrame) {
    try {
      rtpFrameBlockingQueue.add(rtpFrame);
    } catch (IllegalStateException e) {
      Log.i(TAG, "Video frame discarded");
      droppedVideoFrames++;
    }
  }

  @Override
  public void onAudioFrameCreated(RtpFrame rtpFrame) {
    try {
      rtpFrameBlockingQueue.add(rtpFrame);
    } catch (IllegalStateException e) {
      Log.i(TAG, "Audio frame discarded");
      droppedAudioFrames++;
    }
  }

  public void start() {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!Thread.interrupted()) {
          try {
            RtpFrame rtpFrame = rtpFrameBlockingQueue.poll(1, TimeUnit.SECONDS);
            if (rtpFrame == null) {
              Log.i(TAG, "Skipping iteration, frame null");
              continue;
            }
            rtpSocket.sendFrame(rtpFrame, isEnableLogs);
            //bytes to bits
            bitrateManager.calculateBitrate(rtpFrame.getLength() * 8);
            if (rtpFrame.isVideoFrame()) {
              videoFramesSent++;
            } else {
              audioFramesSent++;
            }
            baseSenderReport.update(rtpFrame, isEnableLogs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (IOException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "send error: ", e);
            connectCheckerRtsp.onConnectionFailedRtsp("Error send packet, " + e.getMessage());
          }
        }
      }
    });
    thread.start();
  }

  public void stop() {
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(100);
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    rtpFrameBlockingQueue.clear();
    baseSenderReport.reset();
    baseSenderReport.close();
    rtpSocket.close();
    aacPacket.reset();
    if (videoPacket != null) videoPacket.reset();

    resetSentAudioFrames();
    resetSentVideoFrames();
    resetDroppedAudioFrames();
    resetDroppedVideoFrames();
  }

  public boolean hasCongestion() {
    float size = rtpFrameBlockingQueue.size();
    float remaining = rtpFrameBlockingQueue.remainingCapacity();
    float capacity = size + remaining;
    return size >= capacity * 0.2;  //more than 20% queue used. You could have congestion
  }

  public void resizeCache(int newSize) {
    if (newSize < rtpFrameBlockingQueue.size() - rtpFrameBlockingQueue.remainingCapacity()) {
      throw new RuntimeException("Can't fit current cache inside new cache size");
    }

    BlockingQueue<RtpFrame> tempQueue = new LinkedBlockingQueue<>(newSize);
    rtpFrameBlockingQueue.drainTo(tempQueue);
    rtpFrameBlockingQueue = tempQueue;
  }

  public int getCacheSize() {
    return rtpFrameBlockingQueue.size();
  }

  public long getSentAudioFrames() {
    return audioFramesSent;
  }

  public long getSentVideoFrames() {
    return videoFramesSent;
  }

  public long getDroppedAudioFrames() {
    return droppedAudioFrames;
  }

  public long getDroppedVideoFrames() {
    return droppedVideoFrames;
  }

  public void resetSentAudioFrames() {
    audioFramesSent = 0;
  }

  public void resetSentVideoFrames() {
    videoFramesSent = 0;
  }

  public void resetDroppedAudioFrames() {
    droppedAudioFrames = 0;
  }

  public void resetDroppedVideoFrames() {
    droppedVideoFrames = 0;
  }

  public void setLogs(boolean enable) {
    this.isEnableLogs = enable;
  }
}
