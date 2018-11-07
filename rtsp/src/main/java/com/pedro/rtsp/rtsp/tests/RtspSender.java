package com.pedro.rtsp.rtsp.tests;

import android.media.MediaCodec;
import android.util.Log;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.tests.rtcp.BaseSenderReport;
import com.pedro.rtsp.rtsp.tests.rtcp.SenderReportTcp;
import com.pedro.rtsp.rtsp.tests.rtcp.SenderReportUdp;
import com.pedro.rtsp.rtsp.tests.rtp.packets.AacPacket;
import com.pedro.rtsp.rtsp.tests.rtp.packets.H264Packet;
import com.pedro.rtsp.rtsp.tests.rtp.packets.PacketCallback;
import com.pedro.rtsp.rtsp.tests.rtp.sockets.RtpSocket;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class RtspSender {

  private final static String TAG = "RtspSender";
  private H264Packet h264Packet;
  private AacPacket aacPacket;
  private RtpSocket rtpSocket;
  private BaseSenderReport baseSenderReport;
  private BlockingQueue<RtpFrame> rtpFrameBlockingQueue = new LinkedBlockingQueue<>(300);
  private Thread thread;

  public RtspSender(ConnectCheckerRtsp connectCheckerRtsp, Protocol protocol, byte[] sps,
      byte[] pps, int sampleRate) {
    h264Packet = new H264Packet(sps, pps, videoPacketCallback);
    aacPacket = new AacPacket(sampleRate, audioPacketCallback);
    rtpSocket = new RtpSocket(connectCheckerRtsp, protocol);
    baseSenderReport = protocol == Protocol.TCP ? new SenderReportTcp(connectCheckerRtsp)
        : new SenderReportUdp(connectCheckerRtsp);
    baseSenderReport.setSSRC(new Random().nextInt());
  }

  public void setDataStream(OutputStream outputStream, String host) {
    rtpSocket.setDataStream(outputStream, host);
    if (baseSenderReport instanceof SenderReportTcp) {
      ((SenderReportTcp) baseSenderReport).setOutputStream(outputStream);
    } else {
      ((SenderReportUdp) baseSenderReport).setHost(host);
    }
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

  private PacketCallback videoPacketCallback = new PacketCallback() {
    @Override
    public void onFrameCreated(RtpFrame rtpFrame) {
      try {
        rtpFrameBlockingQueue.add(rtpFrame);
      } catch (IllegalStateException e) {
        Log.i(TAG, "video frame discarded");
      }
    }

    @Override
    public void onFrameCreated(List<RtpFrame> rtpFrames) {
      try {
        rtpFrameBlockingQueue.addAll(rtpFrames);
      } catch (IllegalStateException e) {
        Log.i(TAG, "video frame discarded");
      }
    }
  };

  private PacketCallback audioPacketCallback = new PacketCallback() {
    @Override
    public void onFrameCreated(RtpFrame rtpFrame) {
      try {
        rtpFrameBlockingQueue.add(rtpFrame);
      } catch (IllegalStateException e) {
        Log.i(TAG, "audio frame discarded");
      }
    }

    @Override
    public void onFrameCreated(List<RtpFrame> rtpFrames) {
      try {
        rtpFrameBlockingQueue.addAll(rtpFrames);
      } catch (IllegalStateException e) {
        Log.i(TAG, "video frame discarded");
      }
    }
  };

  public void start() {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!Thread.interrupted()) {
          try {
            RtpFrame rtpFrame = rtpFrameBlockingQueue.take();
            baseSenderReport.update(rtpFrame);
            rtpSocket.sendFrame(rtpFrame);
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
    rtpSocket.close();
    aacPacket.reset();
    h264Packet.reset();
    if (baseSenderReport instanceof SenderReportUdp) ((SenderReportUdp) baseSenderReport).close();
  }
}
