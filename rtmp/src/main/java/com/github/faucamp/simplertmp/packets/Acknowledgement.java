package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * (Window) Acknowledgement
 * 
 * The client or the server sends the acknowledgment to the peer after
 * receiving bytes equal to the window size. The window size is the
 * maximum number of bytes that the sender sends without receiving
 * acknowledgment from the receiver. The server sends the window size to
 * the client after application connects. This message specifies the
 * sequence number, which is the number of the bytes received so far.
 * 
 * @author francois, yuhsuan.lin
 */
public class Acknowledgement extends RtmpPacket {

    private int sequenceNumber;

    public Acknowledgement(RtmpHeader header) {
        super(header);
    }

    public Acknowledgement(int numBytesReadThusFar) {
        super(new RtmpHeader(RtmpHeader.CHUNK_FULL, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MESSAGE_ACKNOWLEDGEMENT));
        this.sequenceNumber = numBytesReadThusFar;
    }

    public int getAcknowledgementWindowSize() {
        return sequenceNumber;
    }

    /** @return the sequence number, which is the number of the bytes received so far */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /** Sets the sequence number, which is the number of the bytes received so far */
    public void setSequenceNumber(int numBytesRead) {
        this.sequenceNumber = numBytesRead;
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        sequenceNumber = in.readInt();
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        out.writeInt(sequenceNumber);
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
        return "RTMP Acknowledgment (sequence number: " + sequenceNumber + ")";
    }
}
