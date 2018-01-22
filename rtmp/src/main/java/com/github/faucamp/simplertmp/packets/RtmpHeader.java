/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.faucamp.simplertmp.packets;

import android.support.annotation.IntDef;

import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.io.ChunkStreamInfo;
import com.github.faucamp.simplertmp.io.RtmpSessionInfo;

import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

import static com.github.faucamp.simplertmp.Util.intToUnsignedInt24;

/**
 *
 * @author francois, leoma, yuhsuan.lin
 */
public class RtmpHeader {

    /**
     * RTMP packet/message type definitions.
     * Note: docstrings are adapted from the official Adobe RTMP spec:
     * http://www.adobe.com/devnet/rtmp/
     */
    @IntDef({MESSAGE_SET_CHUNK_SIZE,
            MESSAGE_ABORT,
            MESSAGE_ACKNOWLEDGEMENT,
            MESSAGE_USER_CONTROL,
            MESSAGE_WINDOW_ACKNOWLEDGEMENT_SIZE,
            MESSAGE_SET_PEER_BANDWIDTH,
            MESSAGE_AUDIO,
            MESSAGE_VIDEO,
            MESSAGE_DATA_AMF3,
            MESSAGE_SHARED_OBJECT_AMF3,
            MESSAGE_COMMAND_AMF3,
            MESSAGE_DATA_AMF0,
            MESSAGE_COMMAND_AMF0,
            MESSAGE_SHARED_OBJECT_AMF0,
            MESSAGE_AGGREGATE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MessageType {}

    /**
     * Protocol control message 1
     * Set Chunk Size, is used to notify the peer a new maximum chunk size to use.
     */
    public static final byte MESSAGE_SET_CHUNK_SIZE = 0x01;

    /**
     * Protocol control message 2
     * Abort Message, is used to notify the peer if it is waiting for chunks
     * to complete a message, then to discard the partially received message
     * over a chunk stream and abort processing of that message.
     */
    public static final byte MESSAGE_ABORT = 0x02;

    /**
     * Protocol control message 3
     * The client or the server sends the acknowledgment to the peer after
     * receiving bytes equal to the window size. The window size is the
     * maximum number of bytes that the sender sends without receiving
     * acknowledgment from the receiver.
     */
    public static final byte MESSAGE_ACKNOWLEDGEMENT = 0x03;

    /**
     * Protocol control message 4
     * The client or the server sends this message to notify the peer about
     * the user control events. This message carries Event type and Event
     * data.
     * Also known as a PING message in some RTMP implementations.
     */
    public static final byte MESSAGE_USER_CONTROL = 0x04;

    /**
     * Protocol control message 5
     * The client or the server sends this message to inform the peer which
     * window size to use when sending acknowledgment.
     * Also known as ServerBW ("server bandwidth") in some RTMP implementations.
     */
    public static final byte MESSAGE_WINDOW_ACKNOWLEDGEMENT_SIZE = 0x05;

    /**
     * Protocol control message 6
     * The client or the server sends this message to update the output
     * bandwidth of the peer. The output bandwidth value is the same as the
     * window size for the peer.
     * Also known as ClientBW ("client bandwidth") in some RTMP implementations.
     */
    public static final byte MESSAGE_SET_PEER_BANDWIDTH = 0x06;

    /**
     * RTMP audio packet (0x08)
     * The client or the server sends this message to send audio data to the peer.
     */
    public static final byte MESSAGE_AUDIO = 0x08;

    /**
     * RTMP video packet (0x09)
     * The client or the server sends this message to send video data to the peer.
     */
    public static final byte MESSAGE_VIDEO = 0x09;

    /**
     * RTMP message type 0x0F
     * The client or the server sends this message to send Metadata or any
     * user data to the peer. Metadata includes details about the data (audio, video etc.)
     * like creation time, duration, theme and so on.
     * This is the AMF3-encoded version.
     */
    public static final byte MESSAGE_DATA_AMF3 = 0x0F;

    /**
     * RTMP message type 0x10
     * A shared object is a Flash object (a collection of name value pairs)
     * that are in synchronization across multiple clients, instances, and
     * so on.
     * This is the AMF3 version: kMsgContainerEx=16 for AMF3.
     */
    public static final byte MESSAGE_SHARED_OBJECT_AMF3 = 0x10;

    /**
     * RTMP message type 0x11
     * Command messages carry the AMF-encoded commands between the client
     * and the server.
     * A command message consists of command name, transaction ID, and command object that
     * contains related parameters.
     * This is the AMF3-encoded version.
     */
    public static final byte MESSAGE_COMMAND_AMF3 = 0x11;
    /**
     * RTMP message type 0x12
     * The client or the server sends this message to send Metadata or any
     * user data to the peer. Metadata includes details about the data (audio, video etc.)
     * like creation time, duration, theme and so on.
     * This is the AMF0-encoded version.
     */
    public static final byte MESSAGE_DATA_AMF0 = 0x12;
    /**
     * RTMP message type 0x14
     * Command messages carry the AMF-encoded commands between the client
     * and the server.
     * A command message consists of command name, transaction ID, and command object that
     * contains related parameters.
     * This is the common AMF0 version, also known as INVOKE in some RTMP implementations.
     */
    public static final byte MESSAGE_COMMAND_AMF0 = 0x14;
    /**
     * RTMP message type 0x13
     * A shared object is a Flash object (a collection of name value pairs)
     * that are in synchronization across multiple clients, instances, and
     * so on.
     * This is the AMF0 version: kMsgContainer=19 for AMF0.
     */
    public static final byte MESSAGE_SHARED_OBJECT_AMF0 = 0x13;
    /**
     * RTMP message type 0x16
     * An aggregate message is a single message that contains a list of sub-messages.
     */
    public static final byte MESSAGE_AGGREGATE = 0x16;

    @MessageType
    private static int valueOfMessageType(byte messageTypeId) {
        switch (messageTypeId) {
            case MESSAGE_SET_CHUNK_SIZE:
            case MESSAGE_ABORT:
            case MESSAGE_ACKNOWLEDGEMENT:
            case MESSAGE_USER_CONTROL:
            case MESSAGE_WINDOW_ACKNOWLEDGEMENT_SIZE:
            case MESSAGE_SET_PEER_BANDWIDTH:
            case MESSAGE_AUDIO:
            case MESSAGE_VIDEO:
            case MESSAGE_DATA_AMF3:
            case MESSAGE_SHARED_OBJECT_AMF3:
            case MESSAGE_COMMAND_AMF3:
            case MESSAGE_DATA_AMF0:
            case MESSAGE_COMMAND_AMF0:
            case MESSAGE_SHARED_OBJECT_AMF0:
            case MESSAGE_AGGREGATE:
                return messageTypeId;
            default:
                throw new IllegalArgumentException("Unknown message type byte: " + ByteString.of(messageTypeId).hex());
        }
    }

    @IntDef({CHUNK_FULL,
        CHUNK_RELATIVE_LARGE,
        CHUNK_RELATIVE_TIMESTAMP_ONLY,
        CHUNK_RELATIVE_SINGLE_BYTE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChunkType {}

    /** Full 12-byte RTMP chunk header */
    public static final byte CHUNK_FULL = 0x00;
    /** Relative 8-byte RTMP chunk header (message stream ID is not included) */
    public static final byte CHUNK_RELATIVE_LARGE = 0x01;
    /** Relative 4-byte RTMP chunk header (only timestamp delta) */
    public static final byte CHUNK_RELATIVE_TIMESTAMP_ONLY = 0x02;
    /** Relative 1-byte RTMP chunk header (no "real" header, just the 1-byte indicating chunk header type & chunk stream ID) */
    public static final byte CHUNK_RELATIVE_SINGLE_BYTE = 0x03;

    @ChunkType
    public static int valueOfChunkType(byte chunkHeaderType) {
        switch (chunkHeaderType) {
            case CHUNK_FULL:
            case CHUNK_RELATIVE_LARGE:
            case CHUNK_RELATIVE_TIMESTAMP_ONLY:
            case CHUNK_RELATIVE_SINGLE_BYTE:
                return chunkHeaderType;
            default:
               throw new IllegalArgumentException("Unknown chunk header type byte: " + ByteString.of(chunkHeaderType).hex());
        }
    }

    @ChunkType private int chunkType;
    private int chunkStreamId;
    private int absoluteTimestamp;
    private int timestampDelta = -1;
    private int packetLength;
    @MessageType private int messageType;
    private int messageStreamId;
    private int extendedTimestamp;

    public RtmpHeader() {
    }

    public RtmpHeader(@ChunkType int chunkType, int chunkStreamId, @MessageType int messageType) {
        this.chunkType = chunkType;
        this.chunkStreamId = chunkStreamId;
        this.messageType = messageType;
    }

    public static RtmpHeader readHeader(BufferedSource in, RtmpSessionInfo rtmpSessionInfo) throws IOException {
        RtmpHeader rtmpHeader = new RtmpHeader();
        rtmpHeader.readHeaderImpl(in, rtmpSessionInfo);
        return rtmpHeader;
    }

    private void readHeaderImpl(BufferedSource in, RtmpSessionInfo rtmpSessionInfo) throws IOException {

        byte basicHeaderByte = in.readByte();
        if (basicHeaderByte == -1) {
            throw new EOFException("Unexpected EOF while reading RTMP packet basic header");
        }
        // Read byte 0: chunk type and chunk stream ID
        parseBasicHeader(basicHeaderByte);

        switch (chunkType) {
            case CHUNK_FULL: { //  b00 = 12 byte header (full header)
                Buffer buffer = new Buffer();
                in.readFully(buffer, 11);
                // Read bytes 1-3: Absolute timestamp
                absoluteTimestamp = Util.readUnsignedInt24(buffer);
                timestampDelta = 0;
                // Read bytes 4-6: Packet length
                packetLength = Util.readUnsignedInt24(buffer);
                // Read byte 7: Message type ID
                messageType = valueOfMessageType(buffer.readByte());
                // Read bytes 8-11: Message stream ID (apparently little-endian order)
                messageStreamId = buffer.readIntLe();
                // Read bytes 1-4: Extended timestamp
                extendedTimestamp = absoluteTimestamp >= 0xffffff ? in.readInt() : 0;
                if (extendedTimestamp != 0) {
                    absoluteTimestamp = extendedTimestamp;
                }
                break;
            }
            case CHUNK_RELATIVE_LARGE: { // b01 = 8 bytes - like type 0. not including message stream ID (4 last bytes)
                Buffer buffer = new Buffer();
                in.readFully(buffer, 7);
                // Read bytes 1-3: Timestamp delta
                timestampDelta = Util.readUnsignedInt24(buffer);
                // Read bytes 4-6: Packet length
                packetLength = Util.readUnsignedInt24(buffer);
                // Read byte 7: Message type ID
                messageType = valueOfMessageType(buffer.readByte());
                // Read bytes 1-4: Extended timestamp delta
                extendedTimestamp = timestampDelta >= 0xffffff ? in.readInt() : 0;
                RtmpHeader prevHeader = rtmpSessionInfo.getChunkStreamInfo(chunkStreamId).prevHeaderRx();
                if (prevHeader != null) {
                    messageStreamId = prevHeader.messageStreamId;
                    absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : prevHeader.absoluteTimestamp + timestampDelta;
                } else {
                    messageStreamId = 0;
                    absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : timestampDelta;
                }
                break;
            }
            case CHUNK_RELATIVE_TIMESTAMP_ONLY: { // b10 = 4 bytes - Basic Header and timestamp (3 bytes) are included
                // Read bytes 1-3: Timestamp delta
                timestampDelta = Util.readUnsignedInt24(in);
                // Read bytes 1-4: Extended timestamp delta
                extendedTimestamp = timestampDelta >= 0xffffff ? in.readInt() : 0;
                RtmpHeader prevHeader = rtmpSessionInfo.getChunkStreamInfo(chunkStreamId).prevHeaderRx();
                packetLength = prevHeader.packetLength;
                messageType = prevHeader.messageType;
                messageStreamId = prevHeader.messageStreamId;
                absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : prevHeader.absoluteTimestamp + timestampDelta;
                break;
            }
            case CHUNK_RELATIVE_SINGLE_BYTE: { // b11 = 1 byte: basic header only
                RtmpHeader prevHeader = rtmpSessionInfo.getChunkStreamInfo(chunkStreamId).prevHeaderRx();
                // Read bytes 1-4: Extended timestamp
                extendedTimestamp = prevHeader.timestampDelta >= 0xffffff ? in.readInt() : 0;
                timestampDelta = extendedTimestamp != 0 ? 0xffffff : prevHeader.timestampDelta;
                packetLength = prevHeader.packetLength;
                messageType = prevHeader.messageType;
                messageStreamId = prevHeader.messageStreamId;
                absoluteTimestamp = extendedTimestamp != 0 ? extendedTimestamp : prevHeader.absoluteTimestamp + timestampDelta;
                break;
            }
            default:
                throw new IOException("Invalid chunk type; basic header byte was: " + ByteString.of(basicHeaderByte).hex());
        }
    }

    public void writeTo(BufferedSink out, @ChunkType int chunkType, final ChunkStreamInfo chunkStreamInfo) throws IOException {
        // Write basic header byte
        Buffer buffer = new Buffer();
        buffer.writeByte(((byte) (chunkType << 6) | chunkStreamId));
        switch (chunkType) {
            case CHUNK_FULL: { //  b00 = 12 byte header (full header)
                chunkStreamInfo.markDeltaTimestampTx();
                buffer.write(intToUnsignedInt24(absoluteTimestamp >= 0xffffff ? 0xffffff : absoluteTimestamp))
                        .write(intToUnsignedInt24(packetLength))
                        .writeByte(messageType)
                        .writeIntLe(messageStreamId);
                if (absoluteTimestamp >= 0xffffff) {
                    extendedTimestamp = absoluteTimestamp;
                    buffer.writeInt(extendedTimestamp);
                }
                break;
            }
            case CHUNK_RELATIVE_LARGE: { // b01 = 8 bytes - like type 0. not including message ID (4 last bytes)
                timestampDelta = (int) chunkStreamInfo.markDeltaTimestampTx();
                absoluteTimestamp = chunkStreamInfo.getPrevHeaderTx().getAbsoluteTimestamp() + timestampDelta;
                buffer.write(intToUnsignedInt24(absoluteTimestamp >= 0xffffff ? 0xffffff : timestampDelta))
                        .write(intToUnsignedInt24(packetLength))
                        .writeByte(messageType);
                if (absoluteTimestamp >= 0xffffff) {
                    extendedTimestamp = absoluteTimestamp;
                    buffer.writeInt(absoluteTimestamp);
                }
                break;
            }
            case CHUNK_RELATIVE_TIMESTAMP_ONLY: { // b10 = 4 bytes - Basic Header and timestamp (3 bytes) are included
                timestampDelta = (int) chunkStreamInfo.markDeltaTimestampTx();
                absoluteTimestamp = chunkStreamInfo.getPrevHeaderTx().getAbsoluteTimestamp() + timestampDelta;
                buffer.write(intToUnsignedInt24((absoluteTimestamp >= 0xffffff) ? 0xffffff : timestampDelta));
                if (absoluteTimestamp >= 0xffffff) {
                    extendedTimestamp = absoluteTimestamp;
                    buffer.writeInt(extendedTimestamp);
                }
                break;
            }
            case CHUNK_RELATIVE_SINGLE_BYTE: { // b11 = 1 byte: basic header only
                timestampDelta = (int) chunkStreamInfo.markDeltaTimestampTx();
                absoluteTimestamp = chunkStreamInfo.getPrevHeaderTx().getAbsoluteTimestamp() + timestampDelta;
                if (absoluteTimestamp >= 0xffffff) {
                    extendedTimestamp = absoluteTimestamp;
                    buffer.writeInt(extendedTimestamp);
                }
                break;
            }
            default:
                throw new IOException("Invalid chunk type: " + chunkType);
        }
        out.writeAll(buffer);
    }

    private void parseBasicHeader(byte basicHeaderByte) {
        chunkType = valueOfChunkType((byte) ((0xff & basicHeaderByte) >>> 6)); // 2 most significant bits define the chunk type
        chunkStreamId = basicHeaderByte & 0x3F; // 6 least significant bits define chunk stream ID
    }

    /** @return the RTMP chunk stream ID (channel ID) for this chunk */
    public int getChunkStreamId() {
        return chunkStreamId;
    }

    @ChunkType
    public int getChunkType() {
        return chunkType;
    }

    public int getPacketLength() {
        return packetLength;
    }

    public int getMessageStreamId() {
        return messageStreamId;
    }

    @MessageType
    public int getMessageType() {
        return messageType;
    }

    public int getAbsoluteTimestamp() {
        return absoluteTimestamp;
    }

    public void setAbsoluteTimestamp(int absoluteTimestamp) {
        this.absoluteTimestamp = absoluteTimestamp;
    }

    public int getTimestampDelta() {
        return timestampDelta;
    }

    public void setTimestampDelta(int timestampDelta) {
        this.timestampDelta = timestampDelta;
    }

    /** Sets the RTMP chunk stream ID (channel ID) for this chunk */
    public void setChunkStreamId(int channelId) {
        this.chunkStreamId = channelId;
    }

    public void setChunkType(@ChunkType int chunkType) {
        this.chunkType = chunkType;
    }

    public void setChunkType(byte chunkType) {
        this.chunkType = valueOfChunkType(chunkType);
    }

    public void setMessageStreamId(int messageStreamId) {
        this.messageStreamId = messageStreamId;
    }

    public void setMessageType(@MessageType int messageType) {
        this.messageType = messageType;
    }

    public void setMessageType(byte messageType) {
        this.messageType = valueOfMessageType(messageType);
    }

    public void setPacketLength(int packetLength) {
        this.packetLength = packetLength;
    }
}
