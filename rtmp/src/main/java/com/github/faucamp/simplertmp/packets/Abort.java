package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * A "Abort" RTMP control message, received on chunk stream ID 2 (control channel)
 * 
 * @author francois, yuhsuan.lin
 */
public class Abort extends RtmpPacket {

    private int chunkStreamId;
    
    public Abort(RtmpHeader header) {
        super(header);
    }

    public Abort(int chunkStreamId) {
        super(new RtmpHeader(RtmpHeader.CHUNK_RELATIVE_LARGE, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MESSAGE_SET_CHUNK_SIZE));
        this.chunkStreamId = chunkStreamId;
    }

    /** @return the ID of the chunk stream to be aborted */
    public int getChunkStreamId() {
        return chunkStreamId;
    }

    /** Sets the ID of the chunk stream to be aborted */
    public void setChunkStreamId(int chunkStreamId) {
        this.chunkStreamId = chunkStreamId;
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        // Value is received in the 4 bytes of the body
        chunkStreamId = in.readInt();
    }

    @Override
    protected Buffer array() {
        return null;
    }

    @Override
    protected int size() {
        return 0;
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        out.writeInt(chunkStreamId);
    }
}
