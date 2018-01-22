package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * A "Set chunk size" RTMP message, received on chunk stream ID 2 (control channel)
 * 
 * @author francois, yuhsuan.lin
 */
public class SetChunkSize extends RtmpPacket {

    private int chunkSize;

    public SetChunkSize(RtmpHeader header) {
        super(header);
    }

    public SetChunkSize(int chunkSize) {
        super(new RtmpHeader(RtmpHeader.CHUNK_RELATIVE_LARGE, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MESSAGE_SET_CHUNK_SIZE));
        this.chunkSize = chunkSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        // Value is received in the 4 bytes of the body
        chunkSize = in.readInt();
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        // Value is received in the 4 bytes of the body
        out.writeInt(chunkSize);
    }

    @Override
    protected Buffer array() {
        return null;
    }

    @Override
    protected int size() {
        return 0;
    }
}
