package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 *
 * @author francois, leo, yuhsuan.lin
 */
public abstract class RtmpPacket {
     
    protected RtmpHeader header;

    public RtmpPacket(RtmpHeader header) {
        this.header = header;
    }

    public RtmpHeader getHeader() {
        return header;
    }
    
    public abstract void readBody(BufferedSource in) throws IOException;

    protected abstract void writeBody(BufferedSink out) throws IOException;

    protected abstract Buffer array();

    protected abstract int size();

    public void writeTo(BufferedSink out, final int chunkSize, final ChunkStreamInfo chunkStreamInfo) throws IOException {
        Buffer body = new Buffer(), outBuffer = new Buffer();
        writeBody(body);
        body = this instanceof ContentData ? array() : body;
        int length = this instanceof ContentData ? size() : (int) body.size();
        header.setPacketLength(length);
        // Write header for first chunk
        header.writeTo(outBuffer, RtmpHeader.CHUNK_FULL, chunkStreamInfo);
        while (length > chunkSize) {
            // Write packet for chunk
            outBuffer.write(body, chunkSize);
            length -= chunkSize;
            // Write header for remain chunk
            header.writeTo(outBuffer, RtmpHeader.CHUNK_RELATIVE_SINGLE_BYTE, chunkStreamInfo);
        }
        outBuffer.write(body, length);
        out.writeAll(outBuffer);
    }
}
