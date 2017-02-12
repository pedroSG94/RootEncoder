package com.pedro.rtsp.rtp;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/02/17.
 */

public class RtpH264Packet extends RtpPacket{

    private PacketCreated packetCreated;

    public RtpH264Packet(PacketCreated packetCreated) {
        super(125, 90000);
        this.packetCreated = packetCreated;
    }

    public void createPacket(ByteBuffer buf, int size, long timeUs) throws IOException {
        addNalu(buf, size, timeUs);
    }

    public void createPacket(byte[] data, int offset, int size, long timeUs) throws IOException{
        addNalu(data, offset, size, timeUs);
    }

    private void addNalu(ByteBuffer buf, int size, long timeUs) throws IOException {
        byte[] data = new byte[size];
        buf.get(data);
        addNalu(data, 0, size, timeUs);
    }

    private void addNalu(byte[] data, int offset, int size, long timeUs) throws IOException {
        if (size <= 1400) {
            createSingleUnit(data, offset, size, timeUs);
        } else {
            createFuA(data, offset, size, timeUs);
        }
    }

    private void createSingleUnit(byte[] data, int offset, int size, long timeUs) throws IOException {
        packetCreated.onH264PacketCreated(addHeader(data, offset, size, timeUs));
    }

    private void createFuA(byte[] data, int offset, int size, long timeUs) throws IOException {
        byte originHeader = data[offset++];
        size -= 1;
        int left = size;
        int read = 1400;
        for (; left > 0; left -= read, offset += read) {
            byte indicator = (byte) ((originHeader & 0xe0) | 28);
            byte naluHeader = (byte) (originHeader & 0x1f);

            if (left < read) {
                read = left;
            }

            if (left == size)
                naluHeader = (byte) (naluHeader | (1 << 7));
            else if (left == read)
                naluHeader = (byte) (naluHeader | (1 << 6));
            packetCreated.onH264PacketCreated(addHeader(new byte[]{indicator, naluHeader}, data, offset, read, timeUs));
        }
    }
}
