/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.faucamp.simplertmp.amf;

import java.io.IOException;

import okio.BufferedSink;
import okio.BufferedSource;

import static com.github.faucamp.simplertmp.amf.AmfDecoder.AMF_NULL;

/**
 *
 * @author francois, yuhsuan.lin
 */
public class AmfNull implements AmfData {

    @Override
    public void writeTo(BufferedSink out) throws IOException {
        out.writeByte(AMF_NULL);
    }

    @Override
    public void readFrom(BufferedSource in) throws IOException {
        // Skip data type byte (we assume it's already read)
    }

    public static void writeNullTo(BufferedSink out) throws IOException {
        out.writeByte(AMF_NULL);
    }

    @Override
    public int getSize() {
        return 1;
    }    
}
