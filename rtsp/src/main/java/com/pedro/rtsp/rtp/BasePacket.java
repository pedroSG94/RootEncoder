package com.pedro.rtsp.rtp;

import com.pedro.rtsp.utils.RtpConstants;
import java.io.IOException;
import java.util.Random;

/**
 * Created by pedro on 19/02/17.
 *
 * Each packetizer inherits from this one and therefore uses RTP and UDP.
 *
 */
public abstract class BasePacket {

	protected static final int rtphl = RtpConstants.RTP_HEADER_LENGTH;
	
	// Maximum size of RTP packets
	protected final static int MAXPACKETSIZE = RtpConstants.MTU - 28;

	protected RtpSocket socket = null;
	protected byte[] buffer;
	
	protected long ts = 0;

	public BasePacket() {
		int ssrc = new Random().nextInt();
		ts = new Random().nextInt();
		socket = new RtpSocket();
		socket.setSSRC(ssrc);
		try {
			socket.setTimeToLive(64);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the destination of the stream.
	 * @param dest The destination address of the stream
	 * @param rtpPort Destination port that will be used for RTP
	 * @param rtcpPort Destination port that will be used for RTCP
	 */
	public void setDestination(String dest, int rtpPort, int rtcpPort) {
		socket.setDestination(dest, rtpPort, rtcpPort);		
	}

	/** Updates data for RTCP SR and sends the packet. */
	protected void send(int length) throws IOException {
		socket.commitBuffer(length);
	}
}
