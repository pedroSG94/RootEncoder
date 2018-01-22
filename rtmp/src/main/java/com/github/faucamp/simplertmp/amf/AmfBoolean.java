package com.github.faucamp.simplertmp.amf;

import java.io.IOException;

import okio.BufferedSink;
import okio.BufferedSource;

import static com.github.faucamp.simplertmp.amf.AmfDecoder.AMF_BOOLEAN;

/**
 *
 * @author francois, yuhsuan.lin
 */
public class AmfBoolean implements AmfData {
    
    private boolean value;
    
    public boolean isValue() {
        return value;
    }
    
    public void setValue(boolean value) {
        this.value = value;
    }
    
    public AmfBoolean(boolean value) {
        this.value = value;
    }
    
    public AmfBoolean() {
    }
    
    @Override
    public void writeTo(BufferedSink out) throws IOException {
        out.write(new byte[]{AMF_BOOLEAN, (byte) (value ? 0x01 : 0x00)});
    }

    @Override
    public void readFrom(BufferedSource in) throws IOException {
        // Skip data type byte (we assume it's already read)
        value = in.readByte() == 0x01;
    }

    @Override
    public int getSize() {
        return 2;
    }
}
