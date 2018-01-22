package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * Window Acknowledgement Size
 * 
 * Also known as ServerBW ("Server bandwidth") in some RTMP implementations.
 * 
 * @author francois, yuhsuan.lin
 */
public class WindowAckSize extends RtmpPacket {

    private int acknowledgementWindowSize;

    public WindowAckSize(RtmpHeader header) {
        super(header);
    }
    
    public WindowAckSize(int acknowledgementWindowSize, ChunkStreamInfo channelInfo) {
        super(new RtmpHeader(channelInfo.canReusePrevHeaderTx(RtmpHeader.MESSAGE_WINDOW_ACKNOWLEDGEMENT_SIZE) ? RtmpHeader.CHUNK_RELATIVE_TIMESTAMP_ONLY : RtmpHeader.CHUNK_FULL, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MESSAGE_WINDOW_ACKNOWLEDGEMENT_SIZE));
        this.acknowledgementWindowSize = acknowledgementWindowSize;
    }


    public int getAcknowledgementWindowSize() {
        return acknowledgementWindowSize;
    }

    public void setAcknowledgementWindowSize(int acknowledgementWindowSize) {
        this.acknowledgementWindowSize = acknowledgementWindowSize;
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        acknowledgementWindowSize = in.readInt();
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        out.writeInt(acknowledgementWindowSize);
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
    public String toString() {
        return "RTMP Window Acknowledgment Size";
    }
}
