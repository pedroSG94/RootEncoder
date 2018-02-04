package com.github.faucamp.simplertmp.amf;

import com.github.faucamp.simplertmp.Util;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

import static com.github.faucamp.simplertmp.amf.AmfDecoder.AMF_NUMBER;

/**
 * AMF0 Number data type
 * 
 * @author francois, yuhsuan.lin
 */
public class AmfNumber implements AmfData {

    private double value;
    /** Size of an AMF number, in bytes (including type bit) */
    public static final int SIZE = 9;

    public AmfNumber(double value) {
        this.value = value;
    }

    public AmfNumber() {
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }    
    
    @Override
    public void writeTo(BufferedSink out) throws IOException {
        writeNumberTo(out, value);
    }

    @Override
    public void readFrom(BufferedSource in) throws IOException {
        // Skip data type byte (we assume it's already read)
        value = Util.readDouble(in);
    }

    public static double readNumberFrom(BufferedSource in) throws IOException {
        Buffer buffer;
        if (!(in instanceof Buffer)) {
            buffer = new Buffer();
            in.readFully(buffer, 9);
        } else {
            buffer = (Buffer) in;
        }
        // Skip data type byte
        buffer.skip(1);
        return Util.readDouble(buffer);
    }

    public static void writeNumberTo(BufferedSink out, double number) throws IOException {
        out.writeAll(new Buffer()
                .writeByte(AMF_NUMBER)
                .writeLong(Double.doubleToRawLongBits(number)));
    }
    
    @Override
    public int getSize() {
        return SIZE;
    }
    
}
