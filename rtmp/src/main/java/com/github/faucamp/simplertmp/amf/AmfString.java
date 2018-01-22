package com.github.faucamp.simplertmp.amf;

import com.github.faucamp.simplertmp.Util;

import java.io.IOException;
import java.nio.charset.Charset;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

import static com.github.faucamp.simplertmp.Util.intToUnsignedInt16;
import static com.github.faucamp.simplertmp.amf.AmfDecoder.AMF_STRING;


/**
 *
 * @author francois, yuhsuan.lin
 */
public class AmfString implements AmfData {

    private static final String TAG = "AmfString";
    public static final Charset ASCII = Charset.forName("ASCII");

    private ByteString value;
    private boolean key;
    private int size = -1;

    public AmfString() {
    }

    public AmfString(String value, boolean isKey) {
        setValue(value);
        setKey(isKey);
    }

    public AmfString(String value) {
        this(value, false);
    }

    public AmfString(boolean isKey) {
        setKey(isKey);
    }

    public String getValue() {
        return value.string(ASCII);
    }

    public void setValue(String value) {
        // Strings are ASCII encoded
        this.value = ByteString.encodeString(value, ASCII);
    }

    public boolean isKey() {
        return key;
    }

    public void setKey(boolean key) {
        this.key = key;
    }

    @Override
    public void writeTo(BufferedSink out) throws IOException {
        writeStringTo(out, value, key);
    }

    @Override
    public void readFrom(BufferedSource in) throws IOException {
        // Skip data type byte (we assume it's already read)
        value = readStringFrom(in, true);
        size = 3 + value.size(); // 1 + 2 + length
    }

    public static ByteString readStringFrom(BufferedSource in, boolean isKey) throws IOException {
        return readStringFrom(in, isKey, null);
    }

    public static ByteString readStringFrom(BufferedSource in, boolean isKey, Buffer buffer) throws IOException {
        if (buffer == null) {
            buffer = new Buffer();
        }
        if (!isKey) {
            if (buffer.size() == 0) {
                in.skip(1);
            } else {
                buffer.skip(1);
            }
        }

        if (buffer.size() < Util.UNSIGNED_INT_16_SIZE) { // Util.readUnsignedInt16 needs 2 bytes
            in.readFully(buffer, Util.UNSIGNED_INT_16_SIZE - buffer.size());
        }
        int length = Util.readUnsignedInt16(buffer);
        if (buffer.size() < length) {
            in.readFully(buffer, length - buffer.size());
        }
        return buffer.readByteString(length);
    }

    public static void writeStringTo(BufferedSink out, String string, boolean isKey) throws IOException {
        // Strings are ASCII encoded
        writeStringTo(out, ByteString.encodeString(string, ASCII), isKey);
    }

    public static void writeStringTo(BufferedSink out, ByteString string, boolean isKey) throws IOException {
        Buffer buffer = new Buffer();
        // Write the STRING data type definition (except if this String is used as a key)
        if (!isKey) {
            buffer.writeByte(AMF_STRING);
        }
        out.writeAll(
                // Write 2 bytes indicating string length
                buffer.write(intToUnsignedInt16(string.size()))
                        // Write string
                        .write(string));
    }

    @Override
    public int getSize() {
        if (size == -1) {
            size = (isKey() ? 0 : 1) + 2 + value.size();
        }
        return size;
    }

    /** @return the byte size of the resulting AMF string of the specified value */
    public static int sizeOf(String string, boolean isKey) {
        return sizeOf(ByteString.encodeString(string, ASCII), isKey);
    }

    public static int sizeOf(ByteString string, boolean isKey) {
        return (isKey ? 0 : 1) + 2 + string.size();
    }
}
