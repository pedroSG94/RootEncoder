package android.util;

/**
 * Created by pedro on 16/6/21.
 *
 * Replace Base64 class of Android for testing purpose
 */
public class Base64 {
  public static String encodeToString(byte[] input, int flags) {
    return java.util.Base64.getEncoder().encodeToString(input);
  }
}
