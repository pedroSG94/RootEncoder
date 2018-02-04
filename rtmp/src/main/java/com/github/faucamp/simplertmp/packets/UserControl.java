package com.github.faucamp.simplertmp.packets;

import android.support.annotation.IntDef;

import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * User Control message, such as ping
 * 
 * @author francois, yuhsuan.lin
 */
public class UserControl extends RtmpPacket {

    /**
     * Control message type
     * Docstring adapted from the official Adobe RTMP spec, section 3.7
     */
    @IntDef({CONTROL_STREAM_BEGIN,
            CONTROL_STREAM_EOF,
            CONTROL_STREAM_DRY,
            CONTROL_SET_BUFFER_LENGTH,
            CONTROL_STREAM_IS_RECORDED,
            CONTROL_PING_REQUEST,
            CONTROL_PONG_REPLY,
            CONTROL_BUFFER_EMPTY,
            CONTROL_BUFFER_READY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ControlType {}

    /**
     * Type: 0
     * The server sends this event to notify the client that a stream has become
     * functional and can be used for communication. By default, this event
     * is sent on ID 0 after the application connect command is successfully
     * received from the client.
     *
     * Event Data:
     * eventData[0] (int) the stream ID of the stream that became functional
     */
    public static final int CONTROL_STREAM_BEGIN = 0;
    /**
     * Type: 1
     * The server sends this event to notify the client that the playback of
     * data is over as requested on this stream. No more data is sent without
     * issuing additional commands. The client discards the messages received
     * for the stream.
     *
     * Event Data:
     * eventData[0]: the ID of thestream on which playback has ended.
     */
    public static final int CONTROL_STREAM_EOF = 1;
    /**
     * Type: 2
     * The server sends this event to notify the client that there is no
     * more data on the stream. If the server does not detect any message for
     * a time period, it can notify the subscribed clients that the stream is
     * dry.
     *
     * Event Data:
     * eventData[0]: the stream ID of the dry stream.
     */
    public static final int CONTROL_STREAM_DRY = 2;
    /**
     * Type: 3
     * The client sends this event to inform the server of the buffer size
     * (in milliseconds) that is used to buffer any data coming over a stream.
     * This event is sent before the server starts  processing the stream.
     *
     * Event Data:
     * eventData[0]: the stream ID and
     * eventData[1]: the buffer length, in milliseconds.
     */
    public static final int CONTROL_SET_BUFFER_LENGTH = 3;
    /**
     * Type: 4
     * The server sends this event to notify the client that the stream is a
     * recorded stream.
     *
     * Event Data:
     * eventData[0]: the stream ID of the recorded stream.
     */
    public static final int CONTROL_STREAM_IS_RECORDED = 4;
    /**
     * Type: 6
     * The server sends this event to test whether the client is reachable.
     *
     * Event Data:
     * eventData[0]: a timestamp representing the local server time when the server dispatched the command.
     *
     * The client responds with PING_RESPONSE on receiving PING_REQUEST.
     */
    public static final int CONTROL_PING_REQUEST = 6;
    /**
     * Type: 7
     * The client sends this event to the server in response to the ping request.
     *
     * Event Data:
     * eventData[0]: the 4-byte timestamp which was received with the PING_REQUEST.
     */
    public static final int CONTROL_PONG_REPLY = 7;
    /**
     * Type: 31 (0x1F)
     *
     * This user control type is not specified in any official documentation, but
     * is sent by Flash Media Server 3.5. Thanks to the rtmpdump devs for their
     * explanation:
     *
     * Buffer Empty (unofficial name): After the server has sent a complete buffer, and
     * sends this Buffer Empty message, it will wait until the play
     * duration of that buffer has passed before sending a new buffer.
     * The Buffer Ready message will be sent when the new buffer starts.
     *
     * (see also: http://repo.or.cz/w/rtmpdump.git/blob/8880d1456b282ee79979adbe7b6a6eb8ad371081:/librtmp/rtmp.c#l2787)
     */
    public static final int CONTROL_BUFFER_EMPTY = 31;
    /**
     * Type: 32 (0x20)
     *
     * This user control type is not specified in any official documentation, but
     * is sent by Flash Media Server 3.5. Thanks to the rtmpdump devs for their
     * explanation:
     *
     * Buffer Ready (unofficial name): After the server has sent a complete buffer, and
     * sends a Buffer Empty message, it will wait until the play
     * duration of that buffer has passed before sending a new buffer.
     * The Buffer Ready message will be sent when the new buffer starts.
     * (There is no BufferReady message for the very first buffer;
     * presumably the Stream Begin message is sufficient for that
     * purpose.)
     *
     * (see also: http://repo.or.cz/w/rtmpdump.git/blob/8880d1456b282ee79979adbe7b6a6eb8ad371081:/librtmp/rtmp.c#l2787)
     */
    public static final int CONTROL_BUFFER_READY = 32;

    @ControlType
    private static int valueOfControlType(int controlByte) {
        switch (controlByte) {
            case CONTROL_STREAM_BEGIN:
            case CONTROL_STREAM_EOF:
            case CONTROL_STREAM_DRY:
            case CONTROL_SET_BUFFER_LENGTH:
            case CONTROL_STREAM_IS_RECORDED:
            case CONTROL_PING_REQUEST:
            case CONTROL_PONG_REPLY:
            case CONTROL_BUFFER_EMPTY:
            case CONTROL_BUFFER_READY:
                return controlByte;
            default:
                throw new IllegalArgumentException("Unknown control type: " + Integer.toHexString(controlByte));
        }
    }


    @ControlType private int type;
    private int[] eventData;

    public UserControl(RtmpHeader header) {
        super(header);
    }

    public UserControl(ChunkStreamInfo channelInfo) {
        super(new RtmpHeader(channelInfo.canReusePrevHeaderTx(RtmpHeader.MESSAGE_USER_CONTROL) ? RtmpHeader.CHUNK_RELATIVE_TIMESTAMP_ONLY : RtmpHeader.CHUNK_FULL, ChunkStreamInfo.RTMP_CID_PROTOCOL_CONTROL, RtmpHeader.MESSAGE_USER_CONTROL));
    }

    /** Convenience construtor that creates a "pong" message for the specified ping */
    public UserControl(UserControl replyToPing, ChunkStreamInfo channelInfo) {
        this(CONTROL_PONG_REPLY, channelInfo);
        this.eventData = replyToPing.eventData;
    }

    public UserControl(@ControlType int type, ChunkStreamInfo channelInfo) {
        this(channelInfo);
        this.type = type;
    }

    @ControlType
    public int getType() {
        return type;
    }

    public void setType(@ControlType int type) {
        this.type = type;
    }

    /** 
     * Convenience method for getting the first event data item, as most user control
     * message types only have one event data item anyway
     * This is equivalent to calling <code>getEventData()[0]</code>
     */
    public int getFirstEventData() {
        return eventData[0];
    }

    public int[] getEventData() {
        return eventData;
    }

    /** Used to set (a single) event data for most user control message types */
    public void setEventData(int eventData) {
        if (type == CONTROL_SET_BUFFER_LENGTH) {
            throw new IllegalStateException("SET_BUFFER_LENGTH requires two event data values; use setEventData(int, int) instead");
        }
        this.eventData = new int[]{eventData};
    }

    /** Used to set event data for the SET_BUFFER_LENGTH user control message types */
    public void setEventData(int streamId, int bufferLength) {
        if (type != CONTROL_SET_BUFFER_LENGTH) {
            throw new IllegalStateException("User control type " + type + " requires only one event data value; use setEventData(int) instead");
        }
        this.eventData = new int[]{streamId, bufferLength};
    }

    @Override
    public void readBody(BufferedSource in) throws IOException {
        // Bytes 0-1: first parameter: ping type (mandatory)
        type = valueOfControlType(Util.readUnsignedInt16(in));
        int bytesRead = 2;
        // Event data (1 for most types, 2 for SET_BUFFER_LENGTH)
        if (type == CONTROL_SET_BUFFER_LENGTH) {
            setEventData(in.readInt(), in.readInt());
            bytesRead += 8;
        } else {
            setEventData(in.readInt());
            bytesRead += 4;
        }
        // To ensure some strange non-specified UserControl/ping message does not slip through
        assert header.getPacketLength() == bytesRead;
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        Buffer buffer = new Buffer();
        // Write the user control message type
        buffer.write(Util.intToUnsignedInt16(type))
                // Now write the event data
                .writeInt(eventData[0]);
        if (type == CONTROL_SET_BUFFER_LENGTH) {
            buffer.writeInt(eventData[1]);
        }
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
        return "RTMP User Control (type: " + type + ", event data: " + Arrays.toString(eventData) + ")";
    }
}
