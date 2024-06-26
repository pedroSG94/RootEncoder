/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    if (!bitmap.isRecycled()) bitmap.recycle();
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
