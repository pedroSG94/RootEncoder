package com.pedro.rtsp.rtp;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/02/17.
 */

public class RtpPacket {

    private int payloadType;
    private int sampleRate;
    private short sequenceNumber;

    public RtpPacket(int pt, int sampleRate) {
        this.payloadType = pt;
        this.sampleRate = sampleRate;
    }

    public ByteBuffer addHeader(byte[] data, int offset, int size, long timeUs) throws IOException {
        return addHeader(null, data, offset, size, timeUs);
    }

    public ByteBuffer addHeader(byte[] prefixData, byte[] data, int offset, int size, long timeUs) throws IOException {

		/*
        RTP packet header
		Bit offset[b]	0-1	2	3	4-7	8	9-15	16-31
		0			Version	P	X	CC	M	PT	Sequence Number
		32			Timestamp
		64			SSRC identifier
		*/

        ByteBuffer buffer = ByteBuffer.allocate(1500);
        buffer.put((byte) (2 << 6));
        buffer.put((byte) (payloadType));
        buffer.putShort(sequenceNumber++);
        buffer.putInt((int) (timeUs * sampleRate / 1000000));
        buffer.putInt(12345678);

        if (prefixData != null) {
            buffer.put(prefixData);
        }
        return buffer.put(data, offset, size);
    }
}
