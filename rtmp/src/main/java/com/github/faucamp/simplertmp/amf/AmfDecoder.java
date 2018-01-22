package com.github.faucamp.simplertmp.amf;

import android.support.annotation.IntDef;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import okio.BufferedSource;
import okio.ByteString;

/**
 *
 * @author francois, yuhsuan.lin
 */
public class AmfDecoder {

    /** Number (encoded as IEEE 64-bit double precision floating point number) */
    public static final byte AMF_NUMBER = 0x00;
    /** Boolean (Encoded as a single byte of value 0x00 or 0x01) */
    public static final byte AMF_BOOLEAN = 0x01;
    /** String (ASCII encoded) */
    public static final byte AMF_STRING = 0x02;
    /** Object - set of key/value pairs */
    public static final byte AMF_OBJECT = 0x03;
    public static final byte AMF_NULL = 0x05;
    public static final byte AMF_UNDEFINED = 0x06;
    public static final byte AMF_MAP = 0x08;
    public static final byte AMF_ARRAY = 0x0A;

    @IntDef({AMF_NUMBER,
            AMF_BOOLEAN,
            AMF_STRING,
            AMF_OBJECT,
            AMF_NULL,
            AMF_UNDEFINED,
            AMF_MAP,
            AMF_ARRAY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AmfType {}

    public static AmfData readFrom(BufferedSource in) throws IOException {
        byte amfType = in.readByte();

        AmfData amfData;
        switch (amfType) {
            case AMF_NUMBER:
                amfData = new AmfNumber();
                break;
            case AMF_BOOLEAN:
                amfData = new AmfBoolean();
                break;
            case AMF_STRING:
                amfData = new AmfString();
                break;
            case AMF_OBJECT:
                amfData = new AmfObject();
                break;
            case AMF_NULL:
                return new AmfNull();
            case AMF_UNDEFINED:
                return new AmfUndefined();
            case AMF_MAP:
                amfData = new AmfMap();
                break;
            case AMF_ARRAY:
                amfData = new AmfArray();
                break;
            default:
                throw new IOException("Unknown/unimplemented AMF data type: " + ByteString.of(amfType).hex());
        }

        amfData.readFrom(in);
        return amfData;

    }
}
