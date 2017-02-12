package com.pedro.rtsp.rtp;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/02/17.
 */

public class RtpAccPacket extends RtpPacket{

    private PacketCreated packetCreated;

    public RtpAccPacket(int sampleRate, PacketCreated packetCreated){
        super(97, sampleRate);
        this.packetCreated = packetCreated;
    }

    public void createPacket(ByteBuffer buf, int size, long timeUs) throws IOException {
        addAU(buf, size, timeUs);
    }

    public void createPacket(byte[] data, int offset, int size, long timeUs) throws IOException{
        addAU(data, offset, size, timeUs);
    }

    private void addAU(ByteBuffer buf, int size, long timeUs) throws IOException {
        byte[] data = new byte[size];
        buf.get(data);
        addAU(data, 0, size, timeUs);
    }

    private void addAU(byte[] data, int offset, int size, long timeUs) throws IOException{
        int auHeadersLength = 16;
        int auHeader = size << 3;
        ByteBuffer payload = ByteBuffer.allocate(2 + 2 + size);
        payload.putShort((short)auHeadersLength);
        payload.putShort((short)auHeader);
        payload.put(data, offset, size);
        packetCreated.onAccPacketCreated(addHeader(payload.array(), 0, payload.position(), timeUs));
    }

}
