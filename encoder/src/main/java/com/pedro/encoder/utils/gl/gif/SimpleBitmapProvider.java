package com.pedro.encoder.utils.gl.gif;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;

final class SimpleBitmapProvider implements GifDecoder.BitmapProvider {
  @NonNull
  @Override
  public Bitmap obtain(int width, int height, Bitmap.Config config) {
    return Bitmap.createBitmap(width, height, config);
  }

  @Override
  public void release(Bitmap bitmap) {
    bitmap.recycle();
  }

  @Override
  public byte[] obtainByteArray(int size) {
    return new byte[size];
  }

  @Override
  public void release(byte[] bytes) {
    // no-op
  }

  @Override
  public int[] obtainIntArray(int size) {
    return new int[size];
  }

  @Override
  public void release(int[] array) {
    // no-op
  }
}
