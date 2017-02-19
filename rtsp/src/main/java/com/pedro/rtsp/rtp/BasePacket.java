/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.pedro.rtsp.rtp;

import com.pedro.rtsp.constants.RtpConstants;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Created by pedro on 19/02/17.
 * Each packetizer inherits from this one and therefore uses RTP and UDP.
 *
 */
public abstract class BasePacket {

	protected static final int rtphl = RtpConstants.RTP_HEADER_LENGTH;
	
	// Maximum size of RTP packets
	protected final static int MAXPACKETSIZE = RtpConstants.MTU - 28;

	protected RtpSocket socket = null;
	protected InputStream is = null;
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
