package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.amf.AmfString;
import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

/**
 * AMF Data packet
 *
 * Also known as NOTIFY in some RTMP implementations.
 *
 * The client or the server sends this message to send Metadata or any user data
 * to the peer. Metadata includes details about the data (audio, video etc.)
 * like creation time, duration, theme and so on.
 *
 * @author francois, yuhsuan.lin
 */
public class Data extends VariableBodyRtmpPacket {

    private ByteString type;

    public Data(RtmpHeader header) {
        super(header);
    }

    public Data(String type) {
        super(new RtmpHeader(RtmpHeader.CHUNK_FULL, ChunkStreamInfo.RTMP_CID_OVER_CONNECTION, RtmpHeader.MESSAGE_DATA_AMF0));
        setType(type);
    }

    public String getType() {
        return type.string(AmfString.ASCII);
    }

    public void setType(String type) {
        this.type = ByteString.encodeString(type, AmfString.ASCII);
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        // Read notification type
        type = AmfString.readStringFrom(in, false);
        int bytesRead = AmfString.sizeOf(type, false);
        // Read data body
        readVariableData(in, bytesRead);
    }

    /**
     * This method is public for Data to make it easy to dump its contents to
     * another output stream
     */
    @Override
    public void writeBody(BufferedSink out) throws IOException {
        Buffer buffer = new Buffer();
        AmfString.writeStringTo(buffer, type, false);
        writeVariableData(buffer);
        out.writeAll(buffer);
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
