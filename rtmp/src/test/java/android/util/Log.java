package android.util;

/**
 * Created by pedro on 16/6/21.
 *
 * Replace Log class of Android for testing purpose
 */
public class Log {

  public static int i(String tag, String message, Throwable throwable) {
    return println(tag, message, throwable);
  }

  public static int i(String tag, String message) {
    return println(tag, message, null);
  }

  public static int e(String tag, String message, Throwable throwable) {
    return printlnError(tag, message, throwable);
  }

  public static int e(String tag, String message) {
    return printlnError(tag, message, null);
  }

  private static int println(String tag, String message, Throwable throwable) {
    System.out.println("" + tag + ": " + message);
    if (throwable != null) throwable.printStackTrace();
    return 0;
  }

  private static int printlnError(String tag, String message, Throwable throwable) {
    System.err.println("" + tag + ": " + message);
    if (throwable != null) throwable.printStackTrace();
    return 0;
  }
}
