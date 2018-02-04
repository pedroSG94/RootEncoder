package com.github.faucamp.simplertmp.amf;

import android.support.v4.util.SimpleArrayMap;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

import static com.github.faucamp.simplertmp.amf.AmfDecoder.AMF_OBJECT;
import static com.github.faucamp.simplertmp.amf.AmfString.ASCII;
import static com.github.faucamp.simplertmp.amf.AmfString.readStringFrom;


/**
 * AMF object
 * 
 * @author francois, yuhsuan.lin
 */
public class AmfObject implements AmfData {

    protected SimpleArrayMap<String, AmfData> properties = new SimpleArrayMap<>();
    protected int size = -1;
    /** Byte sequence that marks the end of an AMF object */
    protected static final ByteString OBJECT_END_MARKER = new Buffer()
            .write(new byte[]{0x00, 0x00, 0x09}).readByteString();

    public AmfObject() {
    }

    public AmfData getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, AmfData value) {
        properties.put(key, value);
    }

    public void setProperty(String key, boolean value) {
        properties.put(key, new AmfBoolean(value));
    }

    public void setProperty(String key, String value) {
        properties.put(key, new AmfString(value, false));
    }

    public void setProperty(String key, int value) {
        properties.put(key, new AmfNumber(value));
    }

    public void setProperty(String key, double value) {
        properties.put(key, new AmfNumber(value));
    }

    @Override
    public void writeTo(BufferedSink out) throws IOException {
        Buffer buffer = new Buffer();
        // Begin the object
        buffer.writeByte(AMF_OBJECT);

        // Write key/value pairs in this object
        for (int i = 0; i < properties.size(); i++) {
            // The key must be a STRING type, and thus the "type-definition" byte is implied (not included in message)
            AmfString.writeStringTo(buffer, properties.keyAt(i), true);
            properties.valueAt(i).writeTo(buffer);
        }

        // End the object
        buffer.write(OBJECT_END_MARKER);
        out.writeAll(buffer);
    }

    @Override
    public void readFrom(BufferedSource in) throws IOException {
        // Skip data type byte (we assume it's already read)
        size = 1;

        while (true) {
            Buffer buffer = new Buffer();
            in.readFully(buffer, 3);

            if (buffer.getByte(0) == OBJECT_END_MARKER.getByte(0) &&
                    buffer.getByte(1) == OBJECT_END_MARKER.getByte(1) &&
                    buffer.getByte(2) == OBJECT_END_MARKER.getByte(2)) {
                // End marker found
                size += 3;
                return;
            } else {
                // Read the property key...
                ByteString key = readStringFrom(in, true, buffer);
                size += AmfString.sizeOf(key, true);
                // ...and the property value
                AmfData value = AmfDecoder.readFrom(in);
                size += value.getSize();
                properties.put(key.string(ASCII), value);
            }
        }
    }

    @Override
    public int getSize() {
        if (size == -1) {
            size = 1; // object marker
            for (int i = 0; i < properties.size(); i++) {
                size += AmfString.sizeOf(properties.keyAt(i), true) + properties.valueAt(i).getSize();
            }
            size += 3; // end of object marker

        }
        return size;
    }

}
