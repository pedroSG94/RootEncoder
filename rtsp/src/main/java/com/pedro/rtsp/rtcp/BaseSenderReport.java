package com.pedro.rtsp.rtcp;

import android.util.Log;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtpFrame;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Created by pedro on 7/11/18.
 */

public abstract class BaseSenderReport {

  protected static final String TAG = "BaseSenderReport";
  protected static final int PACKET_LENGTH = 28;
  private static final int MTU = 1500;
  private final long interval = 3000;

  private final byte[] videoBuffer = new byte[MTU];
  private final byte[] audioBuffer = new byte[MTU];

  private long videoTime;
  private long audioTime;
  private int videoPacketCount;
  private int videoOctetCount;
  private int audioPacketCount;
  private int audioOctetCount;

  BaseSenderReport() {
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

  public static BaseSenderReport getInstance(Protocol protocol, int videoSourcePort,
      int audioSourcePort) {
    return protocol == Protocol.TCP ? new SenderReportTcp()
        : new SenderReportUdp(videoSourcePort, audioSourcePort);
  }

  public abstract void setDataStream(OutputStream outputStream, String host);

  public void update(RtpFrame rtpFrame) {
    if (rtpFrame.getChannelIdentifier() == (byte) 2) {
      updateVideo(rtpFrame);
    } else {
      updateAudio(rtpFrame);
    }
  }

  public abstract void sendReport(byte[] buffer, RtpFrame rtpFrame, String type, int packetCount,
      int octetCount) throws IOException;

  private void updateVideo(RtpFrame rtpFrame) {
    videoPacketCount++;
    videoOctetCount += rtpFrame.getLength();
    setLong(videoBuffer, videoPacketCount, 20, 24);
    setLong(videoBuffer, videoOctetCount, 24, 28);
    if (System.currentTimeMillis() - videoTime >= interval) {
      videoTime = System.currentTimeMillis();
      setData(videoBuffer, System.nanoTime(), rtpFrame.getTimeStamp());
      try {
        sendReport(videoBuffer, rtpFrame, "Video", videoPacketCount, videoOctetCount);
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
        sendReport(audioBuffer, rtpFrame, "Audio", audioPacketCount, audioOctetCount);
      } catch (IOException e) {
        Log.e(TAG, "Error", e);
      }
    }
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

  public abstract void close();

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
