package com.github.faucamp.simplertmp.packets;

import android.support.annotation.IntDef;

import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

import static com.github.faucamp.simplertmp.packets.RtmpHeader.CHUNK_FULL;
import static com.github.faucamp.simplertmp.packets.RtmpHeader.CHUNK_RELATIVE_TIMESTAMP_ONLY;

/**
 * Set Peer Bandwidth
 * 
 * Also known as ClientrBW ("client bandwidth") in some RTMP implementations.
 * 
 * @author francois, yuhsuan.lin
 */
public class SetPeerBandwidth extends RtmpPacket {

    /**
     * Bandwidth limiting type
     */
    @IntDef({LIMIT_HARD,
            LIMIT_SOFT,
            LIMIT_DYNAMIC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LimitType {}

    /**
     * In a hard (0) request, the peer must send the data in the provided bandwidth.
     */
    public static final int LIMIT_HARD = 0;
    /**
     * In a soft (1) request, the bandwidth is at the discretion of the peer
     * and the sender can limit the bandwidth.
     */
    public static final int LIMIT_SOFT = 1;
    /**
     * In a dynamic (2) request, the bandwidth can be hard or soft.
     */
    public static final int LIMIT_DYNAMIC = 2;

    @LimitType
    private static int valueOfLimitType(byte limitType) {
        switch (limitType) {
            case LIMIT_HARD:
            case LIMIT_SOFT:
            case LIMIT_DYNAMIC:
                return limitType;
            default:
                throw new IllegalArgumentException("Unknown limit type byte: " + ByteString.of(limitType).hex());
        }
    }

    private int acknowledgementWindowSize;
    @LimitType private int limitType;
    
    public SetPeerBandwidth(RtmpHeader header) {
        super(header);
    }
    
    public SetPeerBandwidth(int acknowledgementWindowSize, @LimitType int limitType, ChunkStreamInfo channelInfo) {
        super(new RtmpHeader(channelInfo.canReusePrevHeaderTx(RtmpHeader.MESSAGE_SET_PEER_BANDWIDTH) ? CHUNK_RELATIVE_TIMESTAMP_ONLY : CHUNK_FULL, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MESSAGE_WINDOW_ACKNOWLEDGEMENT_SIZE));
        this.acknowledgementWindowSize = acknowledgementWindowSize;
        this.limitType = limitType;
    }
    
    public int getAcknowledgementWindowSize() {
        return acknowledgementWindowSize;
    }
    
    public void setAcknowledgementWindowSize(int acknowledgementWindowSize) {
        this.acknowledgementWindowSize = acknowledgementWindowSize;
    }

    @LimitType
    public int getLimitType() {
        return limitType;
    }
    
    public void setLimitType(@LimitType int limitType) {
        this.limitType = limitType;
    }
    
    @Override
    public void readBody(BufferedSource in) throws IOException {
        Buffer buffer = new Buffer();
        in.readFully(buffer, 5); // acknowledgementWindowSize 4 bytes + limitType 1 byte
        acknowledgementWindowSize = buffer.readInt();
        limitType = valueOfLimitType(buffer.readByte());
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        Buffer buffer = new Buffer();
        buffer.writeInt(acknowledgementWindowSize);
        buffer.writeByte(limitType);
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

    @Override
    public String toString() {
        return "RTMP Set Peer Bandwidth";
    }
}
