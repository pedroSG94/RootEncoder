package com.github.faucamp.simplertmp.packets;

import com.github.faucamp.simplertmp.amf.AmfNumber;
import com.github.faucamp.simplertmp.amf.AmfString;
import com.github.faucamp.simplertmp.io.ChunkStreamInfo;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

/**
 * Encapsulates an command/"invoke" RTMP packet
 * 
 * Invoke/command packet structure (AMF encoded):
 * (String) <commmand name>
 * (Number) <Transaction ID>
 * (Mixed) <Argument> ex. Null, String, Object: {key1:value1, key2:value2 ... }
 * 
 * @author francois, yuhsuan.lin
 */
public class Command extends VariableBodyRtmpPacket {

    private static final String TAG = "Command";

    private String commandName;
    private int transactionId;

    public Command(RtmpHeader header) {
        super(header);
    }

    public Command(String commandName, int transactionId, ChunkStreamInfo channelInfo) {
        super(new RtmpHeader((channelInfo.canReusePrevHeaderTx(RtmpHeader.MESSAGE_COMMAND_AMF0) ? RtmpHeader.CHUNK_RELATIVE_LARGE : RtmpHeader.CHUNK_FULL), ChunkStreamInfo.RTMP_CID_OVER_CONNECTION, RtmpHeader.MESSAGE_COMMAND_AMF0));
        this.commandName = commandName;
        this.transactionId = transactionId;
    }
    
    public Command(String commandName, int transactionId) {
        super(new RtmpHeader(RtmpHeader.CHUNK_FULL, ChunkStreamInfo.RTMP_CID_OVER_CONNECTION, RtmpHeader.MESSAGE_COMMAND_AMF0));
        this.commandName = commandName;
        this.transactionId = transactionId;
    }

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }    

    @Override
    public void readBody(BufferedSource in) throws IOException {
        // The command name and transaction ID are always present (AMF string followed by number)
        Buffer buffer = new Buffer();
        in.readFully(buffer, 3 + 9); // string & number
        ByteString command = AmfString.readStringFrom(in, false, buffer);
        commandName = command.string(AmfString.ASCII);
        if (buffer.size() < 9) {
            in.readFully(buffer, 9 - buffer.size());
        }
        transactionId = (int) AmfNumber.readNumberFrom(buffer);
        int bytesRead = AmfString.sizeOf(command, false) + AmfNumber.SIZE;
        readVariableData(in, bytesRead);
    }

    @Override
    protected void writeBody(BufferedSink out) throws IOException {
        Buffer buffer = new Buffer();
        AmfString.writeStringTo(buffer, commandName, false);
        AmfNumber.writeNumberTo(buffer, transactionId);
        // Write body data
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

    @Override
    public String toString() {
        return "RTMP Command (command: " + commandName + ", transaction ID: " + transactionId + ")";
    }
}
