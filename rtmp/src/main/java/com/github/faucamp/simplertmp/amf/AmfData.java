package com.github.faucamp.simplertmp.amf;

import java.io.IOException;

import okio.BufferedSink;
import okio.BufferedSource;

/**
 * Base AMF data object. All other AMF data type instances derive from this
 * (including AmfObject)
 * 
 * @author francois, yuhsuan.lin
 */
public interface AmfData {
       
    /** 
     * Write/Serialize this AMF data intance (Object/string/integer etc) to
     * the specified OutputStream
     */
    void writeTo(BufferedSink out) throws IOException;

    /**
     * Read and parse bytes from the specified input stream to populate this
     * AMFData instance (deserialize)
     * 
     * @return the amount of bytes read
     */
    void readFrom(BufferedSource in) throws IOException;
    
    /** @return the amount of bytes required for this object */
    int getSize();
}
