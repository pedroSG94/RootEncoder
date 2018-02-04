package com.github.faucamp.simplertmp.io;

import com.github.faucamp.simplertmp.packets.RtmpHeader;
import com.github.faucamp.simplertmp.packets.RtmpHeader.MessageType;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSource;

/**
 * Chunk stream channel information
 * 
 * @author francois, leo, yuhsuan.lin
 */
public class ChunkStreamInfo {

    public static final byte RTMP_CID_PROTOCOL_CONTROL = 0x02;
    public static final byte RTMP_CID_OVER_CONNECTION = 0x03;
    public static final byte RTMP_CID_OVER_CONNECTION2 = 0x04;
    public static final byte RTMP_CID_OVER_STREAM = 0x05;
    public static final byte RTMP_CID_VIDEO = 0x06;
    public static final byte RTMP_CID_AUDIO = 0x07;

    private RtmpHeader prevHeaderRx;
    private RtmpHeader prevHeaderTx;
    private static long sessionBeginTimestamp;
    private long realLastTimestamp = System.nanoTime() / 1000000;  // Do not use wall time!
    private Buffer buffer = new Buffer();

    /** @return the previous header that was received on this channel, or <code>null</code> if no previous header was received */
    public RtmpHeader prevHeaderRx() {
        return prevHeaderRx;
    }

    /** Sets the previous header that was received on this channel, or <code>null</code> if no previous header was sent */
    public void setPrevHeaderRx(RtmpHeader previousHeader) {
        this.prevHeaderRx = previousHeader;
    }

    /** @return the previous header that was transmitted on this channel */
    public RtmpHeader getPrevHeaderTx() {
        return prevHeaderTx;
    }

    public boolean canReusePrevHeaderTx(@MessageType int forMessageType) {
        return (prevHeaderTx != null && prevHeaderTx.getMessageType() == forMessageType);
    }

    /** Sets the previous header that was transmitted on this channel */
    public void setPrevHeaderTx(RtmpHeader prevHeaderTx) {
        this.prevHeaderTx = prevHeaderTx;
    }

    /** Sets the session beginning timestamp for all chunks */
    public static void markSessionTimestampTx() {
        sessionBeginTimestamp = System.nanoTime() / 1000000;
    }

    /** Utility method for calculating & synchronizing transmitted timestamps */
    public long markAbsoluteTimestampTx() {
        return System.nanoTime() / 1000000 - sessionBeginTimestamp;
    }

    /** Utility method for calculating & synchronizing transmitted timestamp deltas */
    public long markDeltaTimestampTx() {
        long currentTimestamp = System.nanoTime() / 1000000;
        long diffTimestamp = currentTimestamp - realLastTimestamp;
        realLastTimestamp = currentTimestamp;
        return diffTimestamp;
    }

    public boolean storePacketChunk(BufferedSource in, long chunkSize) throws IOException {
        final long remainingBytes = prevHeaderRx.getPacketLength() - buffer.size();
        byte[] chunk = new byte[(int) Math.min(remainingBytes, chunkSize)];
        in.readFully(chunk);
        return buffer.write(chunk).size() == prevHeaderRx.getPacketLength();
    }

    public BufferedSource getStoredPacketInputStream() {
        Buffer newBuffer = buffer.clone();
        buffer.clear();
        return newBuffer;
    }

    /** Clears all currently-stored packet chunks (used when an ABORT packet is received) */
    public void clearStoredChunks() {
        buffer.clear();
    }
}
