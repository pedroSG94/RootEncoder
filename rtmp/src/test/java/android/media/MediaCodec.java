package android.media;

/**
 * Created by pedro on 16/6/21.
 *
 * Replace MediaCodec.BufferInfo class of Android for testing purpose
 */
public class MediaCodec {
  public final static class BufferInfo {

    public void set(int newOffset, int newSize, long newTimeUs, int newFlags) {
      offset = newOffset;
      size = newSize;
      presentationTimeUs = newTimeUs;
      flags = newFlags;
    }

    public int offset;
    public int size;
    public long presentationTimeUs;
    public int flags;
  }
}
