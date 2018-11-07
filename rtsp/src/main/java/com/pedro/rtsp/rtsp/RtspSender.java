package com.pedro.rtsp.rtsp;

import android.media.MediaCodec;
import android.util.Log;
import com.pedro.rtsp.rtcp.BaseSenderReport;
import com.pedro.rtsp.rtp.packets.AacPacket;
import com.pedro.rtsp.rtp.packets.AudioPacketCallback;
import com.pedro.rtsp.rtp.packets.H264Packet;
import com.pedro.rtsp.rtp.packets.VideoPacketCallback;
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.pedro.rtsp.utils.RtpConstants;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class RtspSender implements VideoPacketCallback, AudioPacketCallback {

  private final static String TAG = "RtspSender";
  private H264Packet h264Packet;
  private AacPacket aacPacket;
  private BaseRtpSocket rtpSocket;
  private BaseSenderReport baseSenderReport;
  private BlockingQueue<RtpFrame> rtpFrameBlockingQueue =
      new LinkedBlockingQueue<>(getCacheSize(10));
  private Thread thread;

  public RtspSender(ConnectCheckerRtsp connectCheckerRtsp, Protocol protocol, byte[] sps,
      byte[] pps, int sampleRate) {
    h264Packet = new H264Packet(sps, pps, this);
    aacPacket = new AacPacket(sampleRate, this);
    rtpSocket = BaseRtpSocket.getInstance(connectCheckerRtsp, protocol);
    baseSenderReport = BaseSenderReport.getInstance(protocol);
  }

  /**
   * @param size in mb
   * @return number of packets
   */
  private int getCacheSize(int size) {
    return size * 1024 * 1024 / RtpConstants.MTU;
  }

  public void setDataStream(OutputStream outputStream, String host) {
    rtpSocket.setDataStream(outputStream, host);
    baseSenderReport.setDataStream(outputStream, host);
  }

  public void setVideoPorts(int rtpPort, int rtcpPort) {
    h264Packet.setPorts(rtpPort, rtcpPort);
  }

  public void setAudioPorts(int rtpPort, int rtcpPort) {
    aacPacket.setPorts(rtpPort, rtcpPort);
  }

  public void sendVideoFrame(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    h264Packet.createAndSendPacket(h264Buffer, info);
  }

  public void sendAudioFrame(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    aacPacket.createAndSendPacket(aacBuffer, info);
  }

  @Override
  public void onVideoFrameCreated(RtpFrame rtpFrame) {
    try {
      rtpFrameBlockingQueue.add(rtpFrame);
    } catch (IllegalStateException e) {
      Log.i(TAG, "video frame discarded");
    }
  }

  @Override
  public void onAudioFrameCreated(RtpFrame rtpFrame) {
    try {
      rtpFrameBlockingQueue.add(rtpFrame);
    } catch (IllegalStateException e) {
      Log.i(TAG, "audio frame discarded");
    }
  }

  public void start() {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!Thread.interrupted()) {
          try {
            RtpFrame rtpFrame = rtpFrameBlockingQueue.take();
            rtpSocket.sendFrame(rtpFrame);
            baseSenderReport.update(rtpFrame);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        thread.join(1000);
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
    h264Packet.reset();
  }
}
