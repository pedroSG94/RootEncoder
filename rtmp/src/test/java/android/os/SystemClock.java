package android.os;

public class SystemClock {
    public static long elapsedRealtime() {
        return System.currentTimeMillis();
    }

    public static long elapsedRealtimeNano() {
        return System.nanoTime();
    }
}
