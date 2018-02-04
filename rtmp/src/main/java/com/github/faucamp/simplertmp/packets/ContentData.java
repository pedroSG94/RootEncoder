package com.github.faucamp.simplertmp.packets;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * Content (audio/video) data packet base
 *  
 * @author francois, yuhsuan.lin
 */
public abstract class ContentData extends RtmpPacket {

    protected Buffer data;
    protected int size;

    public ContentData(RtmpHeader header) {
        super(header);
    }

    public Buffer getData() {
        return data;
    }

    public void setData(byte[] data, int size) {
        this.data = new Buffer().write(data);
        this.size = size;
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        data = new Buffer();
        in.readFully(data, header.getPacketLength());
    }

    /**
     * Method is public for content (audio/video)
     * Write this packet body without chunking;
     * useful for dumping audio/video streams
     */
    @Override
    public void writeBody(BufferedSink out) throws IOException {
    }

    @Override
    public Buffer array() {
        return data;
    }

    @Override
    public int size() {
        return size;
    }
}
