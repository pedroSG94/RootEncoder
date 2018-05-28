package com.pedro.rtsp.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by pedro on 22/02/17.
 */

public class AuthUtil {

  private static char[] hexArray =
      { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

  public static String getMd5Hash(String buffer) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
      return bytesToHex(md.digest(buffer.getBytes("UTF-8")));
    } catch (NoSuchAlgorithmException | UnsupportedEncodingException ignore) {
    }
    return "";
  }

  private static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    int v;
    for (int j = 0; j < bytes.length; j++) {
      v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }
}
