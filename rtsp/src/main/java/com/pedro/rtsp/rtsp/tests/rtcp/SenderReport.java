package com.pedro.rtsp.rtsp.tests.rtcp;

import android.util.Log;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.tests.RtpFrame;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Random;

public class SenderReport {

  private static final String TAG = "SenderReport";
  private final Protocol protocol;
  private static final int MTU = 1500;
  private static final int PACKET_LENGTH = 28;
  private final long interval = 3000;

  private final byte[] videoBuffer = new byte[MTU];
  private final byte[] audioBuffer = new byte[MTU];

  private long videoTime;
  private long audioTime;
  private int videoPacketCount;
  private int videoOctetCount;
  private int audioPacketCount;
  private int audioOctetCount;
  //TCP
  private OutputStream outputStream;
  private byte tcpHeader[];
  //UDP
  private MulticastSocket multicastSocket;
  private DatagramPacket datagramPacket = new DatagramPacket(new byte[] { 0 }, 1);

  public SenderReport(Protocol protocol) {
    this.protocol = protocol;
    if (protocol == Protocol.UDP) {
      try {
        multicastSocket = new MulticastSocket();
        multicastSocket.setTimeToLive(64);
      } catch (IOException e) {
        Log.e(TAG, "Error", e);
      }
    } else {
      tcpHeader = new byte[] { '$', 0, 0, PACKET_LENGTH };
    }
    /*							     Version(2)  Padding(0)					 					*/
    /*									 ^		  ^			PT = 0	    						*/
    /*									 |		  |				^								*/
    /*									 | --------			 	|								*/
    /*									 | |---------------------								*/
    /*									 | ||													*/
    /*									 | ||													*/
    videoBuffer[0] = (byte) Integer.parseInt("10000000", 2);
    audioBuffer[0] = (byte) Integer.parseInt("10000000", 2);

    /* Packet Type PT */
    videoBuffer[1] = (byte) 200;
    audioBuffer[1] = (byte) 200;

    /* Byte 2,3          ->  Length		                     */
    setLong(videoBuffer, PACKET_LENGTH / 4 - 1, 2, 4);
    setLong(audioBuffer, PACKET_LENGTH / 4 - 1, 2, 4);
    /* Byte 4,5,6,7      ->  SSRC                            */
    setLong(videoBuffer, new Random().nextInt(), 4, 8);
    setLong(audioBuffer, new Random().nextInt(), 4, 8);
    /* Byte 8,9,10,11    ->  NTP timestamp hb				 */
    /* Byte 12,13,14,15  ->  NTP timestamp lb				 */
    /* Byte 16,17,18,19  ->  RTP timestamp		             */
    /* Byte 20,21,22,23  ->  packet count				 	 */
    /* Byte 24,25,26,27  ->  octet count			         */
  }

  public void setDataStream(OutputStream outputStream, String host) {
    this.outputStream = outputStream;
    try {
      datagramPacket.setAddress(InetAddress.getByName(host));
    } catch (UnknownHostException e) {
      Log.e(TAG, "Error", e);
    }
  }

  public void update(RtpFrame rtpFrame) {
    if (rtpFrame.getChannelIdentifier() == (byte) 2) {
      updateVideo(rtpFrame);
    } else {
      updateAudio(rtpFrame);
    }
  }

  private void updateVideo(RtpFrame rtpFrame) {
    videoPacketCount++;
    videoOctetCount += rtpFrame.getLength();
    setLong(videoBuffer, videoPacketCount, 20, 24);
    setLong(videoBuffer, videoOctetCount, 24, 28);
    if (System.currentTimeMillis() - videoTime >= interval) {
      videoTime = System.currentTimeMillis();
      setData(videoBuffer, System.nanoTime(), rtpFrame.getTimeStamp());
      try {
        if (protocol == Protocol.TCP) {
          sendReportTCP(videoBuffer, rtpFrame.getChannelIdentifier(), "Video", videoPacketCount,
              videoOctetCount);
        } else {
          sendReportUDP(videoBuffer, rtpFrame.getRtcpPort(), "Video", videoPacketCount,
              videoOctetCount);
        }
      } catch (IOException e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  private void updateAudio(RtpFrame rtpFrame) {
    audioPacketCount++;
    audioOctetCount += rtpFrame.getLength();
    setLong(audioBuffer, audioPacketCount, 20, 24);
    setLong(audioBuffer, audioOctetCount, 24, 28);
    if (System.currentTimeMillis() - audioTime >= interval) {
      audioTime = System.currentTimeMillis();
      setData(audioBuffer, System.nanoTime(), rtpFrame.getTimeStamp());
      try {
        if (protocol == Protocol.TCP) {
          sendReportTCP(audioBuffer, rtpFrame.getChannelIdentifier(), "Audio", audioPacketCount,
              audioOctetCount);
        } else {
          sendReportUDP(audioBuffer, rtpFrame.getRtcpPort(), "Audio", audioPacketCount,
              audioOctetCount);
        }
      } catch (IOException e) {
        Log.e(TAG, "Error", e);
      }
    }
  }

  private void sendReportTCP(byte[] buffer, byte channelIdentifier, String type, int packet,
      int octet) throws IOException {
    synchronized (outputStream) {
      tcpHeader[1] = (byte) (channelIdentifier + 1);
      outputStream.write(tcpHeader);
      outputStream.write(buffer, 0, PACKET_LENGTH);
      outputStream.flush();
      Log.i(TAG, "wrote report" + type + ", packets: " + packet + ", octet: " + octet);
    }
  }

  private void sendReportUDP(byte[] buffer, int port, String type, int packet, int octet)
      throws IOException {
    datagramPacket.setData(buffer);
    datagramPacket.setPort(port);
    datagramPacket.setLength(PACKET_LENGTH);
    multicastSocket.send(datagramPacket);
    Log.i(TAG, "wrote report" + type + ", packets: " + packet + ", octet: " + octet);
  }

  public void reset() {
    videoPacketCount = videoOctetCount = 0;
    audioPacketCount = audioOctetCount = 0;
    videoTime = audioTime = 0;
    setLong(videoBuffer, videoPacketCount, 20, 24);
    setLong(videoBuffer, videoOctetCount, 24, 28);
    setLong(audioBuffer, audioPacketCount, 20, 24);
    setLong(audioBuffer, audioOctetCount, 24, 28);
  }

  public void close() {
    if (protocol == Protocol.UDP) {
      multicastSocket.close();
    }
  }

  private void setLong(byte[] buffer, long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      buffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }

  private void setData(byte[] buffer, long ntpts, long rtpts) {
    long hb = ntpts / 1000000000;
    long lb = ((ntpts - hb * 1000000000) * 4294967296L) / 1000000000;
    setLong(buffer, hb, 8, 12);
    setLong(buffer, lb, 12, 16);
    setLong(buffer, rtpts, 16, 20);
  }
}
