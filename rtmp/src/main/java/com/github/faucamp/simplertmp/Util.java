package com.github.faucamp.simplertmp;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

/**
 * Misc utility method
 * @author francois, pedro, yuhsuan.lin
 */
public class Util {
    public static final int UNSIGNED_INT_16_SIZE = 2;

    public static int toUnsignedInt24(byte[] bytes) {
        return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
    }

    public static int toUnsignedInt16(byte[] bytes) {
        return ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
    }

    public static byte[] unsignedInt32ToByteArray(int value) throws IOException {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static double readDouble(BufferedSource in) throws IOException {
        Buffer buffer;
        if (!(in instanceof Buffer)) {
            buffer = new Buffer();
            in.readFully(buffer, 8);
        } else {
            buffer = (Buffer) in;
        }
        return Double.longBitsToDouble(buffer.readLong());
    }

    public static byte[] intToUnsignedInt24(int value) {
        return new byte[] {
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static byte[] intToUnsignedInt16(int value) {
        return new byte[] {
                (byte) (value >>> 8),
                (byte) value};
    }

    public static int readUnsignedInt24(BufferedSource in) throws IOException {
        Buffer buffer;
        if (!(in instanceof Buffer)) {
            buffer = new Buffer();
            in.readFully(buffer, 3);
        } else {
            buffer = (Buffer) in;
        }
        return toUnsignedInt24(buffer.readByteArray(3));
    }

    public static int readUnsignedInt16(BufferedSource in) throws IOException {
        Buffer buffer;
        if (!(in instanceof Buffer)) {
            buffer = new Buffer();
            in.readFully(buffer, UNSIGNED_INT_16_SIZE);
        } else {
            buffer = (Buffer) in;
        }
        return toUnsignedInt16(buffer.readByteArray(UNSIGNED_INT_16_SIZE));
    }

    public static String getSalt(String description) {
        String salt = null;
        String data[] = description.split("&");
        for (String s : data) {
            if (s.contains("salt=")) {
                salt = s.substring(5);
                break;
            }
        }
        return salt;
    }

    public static String getChallenge(String description) {
        String challenge = null;
        String data[] = description.split("&");
        for (String s : data) {
            if (s.contains("challenge=")) {
                challenge = s.substring(10);
                break;
            }
        }
        return challenge;
    }

    public static String getOpaque(String description) {
        String opaque = "";
        String data[] = description.split("&");
        for (String s : data) {
            if (s.contains("opaque=")) {
                opaque = s.substring(7);
                break;
            }
        }
        return opaque;
    }

    public static String stringToMD5BASE64(String s) {
        return ByteString.encodeUtf8(s).md5().base64();
    }
}
