package com.pedro.rtsp.rtcp;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import android.os.SystemClock;

/**
 * Created by pedro on 19/02/17.
 *
 * Implementation of Sender Report RTCP packets.
 */
public class SenderReport {

  private final String TAG = "SenderReport";
  private static final int MTU = 1500;
  private static final int PACKET_LENGTH = 28;

  private MulticastSocket socket;
  private DatagramPacket datagramPacket;

  private byte[] mBuffer = new byte[MTU];
  private int mOctetCount = 0, mPacketCount = 0;
  private long interval, delta, now, old;

  public SenderReport() {
    /*							     Version(2)  Padding(0)					 					*/
    /*									 ^		  ^			PT = 0	    						*/
		/*									 |		  |				^								*/
		/*									 | --------			 	|								*/
		/*									 | |---------------------								*/
		/*									 | ||													*/
		/*									 | ||													*/
    mBuffer[0] = (byte) Integer.parseInt("10000000", 2);

		/* Packet Type PT */
    mBuffer[1] = (byte) 200;

		/* Byte 2,3          ->  Length		                     */
    setLong(PACKET_LENGTH / 4 - 1, 2, 4);

		/* Byte 4,5,6,7      ->  SSRC                            */
		/* Byte 8,9,10,11    ->  NTP timestamp hb				 */
		/* Byte 12,13,14,15  ->  NTP timestamp lb				 */
		/* Byte 16,17,18,19  ->  RTP timestamp		             */
		/* Byte 20,21,22,23  ->  packet count				 	 */
		/* Byte 24,25,26,27  ->  octet count			         */

    try {
      socket = new MulticastSocket();
    } catch (IOException e) {
      // Very unlikely to happen. Means that all UDP ports are already being used
      throw new RuntimeException(e.getMessage());
    }
    datagramPacket = new DatagramPacket(mBuffer, 1);

    // By default we sent one report every 3 second
    interval = 3000;
  }

  public void close() {
    socket.close();
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   *
   * @param length The length of the packet
   * @param rtpts The RTP timestamp.
   **/
  public void update(int length, long rtpts, int port){
    mPacketCount += 1;
    mOctetCount += length;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);

    now = SystemClock.elapsedRealtime();
    delta += old != 0 ? now - old : 0;
    old = now;
    if (interval > 0) {
      if (delta >= interval) {
        // We send a Sender Report
        send(System.nanoTime(), rtpts, port);
        delta = 0;
      }
    }
  }

  public void setSSRC(int ssrc) {
    setLong(ssrc, 4, 8);
    mPacketCount = 0;
    mOctetCount = 0;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);
  }

  public void setDestination(InetAddress dest, int dport) {
    datagramPacket.setPort(dport);
    datagramPacket.setAddress(dest);
  }

  /**
   * Resets the reports (total number of bytes sent, number of packets sent, etc.)
   */
  public void reset() {
    mPacketCount = 0;
    mOctetCount = 0;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);
    delta = now = old = 0;
  }

  private void setLong(long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      mBuffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }

  /**
   * Sends the RTCP packet over the network.
   *
   * @param ntpts the NTP timestamp.
   * @param rtpts the RTP timestamp.
   */
  private void send(final long ntpts, final long rtpts, final int port){
    new Thread(new Runnable() {
      @Override
      public void run() {
        long hb = ntpts / 1000000000;
        long lb = ((ntpts - hb * 1000000000) * 4294967296L) / 1000000000;
        setLong(hb, 8, 12);
        setLong(lb, 12, 16);
        setLong(rtpts, 16, 20);
        datagramPacket.setLength(PACKET_LENGTH);
        datagramPacket.setPort(port);
        Log.i(TAG, "send report, " + datagramPacket.getPort() + " Port");
        try {
          socket.send(datagramPacket);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }).start();
  }
}
