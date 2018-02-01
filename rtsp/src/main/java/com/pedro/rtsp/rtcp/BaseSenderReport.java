package com.pedro.rtsp.rtcp;

/**
 * Created by pedro on 24/02/17.
 */

public abstract class BaseSenderReport {

  protected final String TAG = "SenderReport";

  protected static final int MTU = 1500;
  protected static final int PACKET_LENGTH = 28;

  protected byte[] mBuffer = new byte[MTU];
  protected int mOctetCount = 0, mPacketCount = 0;
  protected long interval, delta, now, old;

  public BaseSenderReport() {
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

    // By default we sent one report every 3 second
    interval = 3000;
    delta = interval;
  }

  public void setSSRC(int ssrc) {
    setLong(ssrc, 4, 8);
    mPacketCount = 0;
    mOctetCount = 0;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   *
   * @param length The length of the packet
   */
  protected boolean updateSend(int length) {
    mPacketCount += 1;
    mOctetCount += length;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);

    now = System.currentTimeMillis();
    delta += old != 0 ? now - old : 0;
    old = now;
    if (interval > 0) {
      if (delta >= interval) {
        // We send a Sender Report
        delta = 0;
        return true;
      }
    }
    return false;
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

  protected void setLong(long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      mBuffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }

  protected void setData(long ntpts, long rtpts) {
    long hb = ntpts / 1000000000;
    long lb = ((ntpts - hb * 1000000000) * 4294967296L) / 1000000000;
    setLong(hb, 8, 12);
    setLong(lb, 12, 16);
    setLong(rtpts, 16, 20);
  }
}
