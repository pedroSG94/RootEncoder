package com.github.faucamp.simplertmp.amf;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

import static com.github.faucamp.simplertmp.amf.AmfDecoder.AMF_MAP;

/**
 * AMF map; that is, an "object"-like structure of key/value pairs, but with 
 * an array-like size indicator at the start (which is seemingly always 0)
 * 
 * @author francois, yuhsuan.lin
 */
public class AmfMap extends AmfObject {
    private final static int SIZE = 4;

    public AmfMap() {
    }

    @Override
    public void writeTo(BufferedSink out) throws IOException {
        // Begin the map/object/array/whatever exactly this is
        Buffer buffer = new Buffer();
        buffer.writeByte(AMF_MAP);

        // Write the "array size"
        buffer.writeInt(properties.size());

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
        //int length = Util.readUnsignedInt32(in); // Seems this is always 0
        in.skip(SIZE);
        super.readFrom(in);
        size += SIZE; // Add the bytes read for parsing the array size (length)
    }

    @Override
    public int getSize() {
        if (size == -1) {
            size = super.getSize() + SIZE; // array length bytes
        }
        return size;
    }
}
