/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.encoder.utils.yuv;

/**
 * Created by pedro on 31/10/18.
 */

public class NV21Utils {

  private static byte[] preAllocatedBufferRotate;
  private static byte[] preAllocatedBufferColor;

  public static void preAllocateBuffers(int length) {
    preAllocatedBufferRotate = new byte[length];
    preAllocatedBufferColor = new byte[length];
  }

  public static int[] toARGB(byte[] yuv, int width, int height) {
    int[] argb = new int[width * height];
    final int frameSize = width * height;
    final int ii = 0;
    final int ij = 0;
    final int di = +1;
    final int dj = +1;
    int a = 0;
    for (int i = 0, ci = ii; i < height; ++i, ci += di) {
      for (int j = 0, cj = ij; j < width; ++j, cj += dj) {
        int y = (0xff & ((int) yuv[ci * width + cj]));
        int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 0]));
        int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 1]));
        y = y < 16 ? 16 : y;
        int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
        int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
        int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        argb[a++] = 0xff000000 | (r << 16) | (g << 8) | b;
      }
    }
    return argb;
  }

  public static byte[] toYV12(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i + qFrameSize] = input[frameSize + i * 2 + 1]; // Cb (U)
      preAllocatedBufferColor[frameSize + i] = input[frameSize + i * 2]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
  public static byte[] toNV12(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i * 2] = input[frameSize + i * 2 + 1]; // Cb (U)
      preAllocatedBufferColor[frameSize + i * 2 + 1] = input[frameSize + i * 2]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] toI420(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i] = input[frameSize + i * 2 + 1]; // Cb (U)
      preAllocatedBufferColor[frameSize + i + qFrameSize] = input[frameSize + i * 2]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] rotate90(byte[] data, int imageWidth, int imageHeight) {
    // Rotate the Y luma
    int i = 0;
    for (int x = 0; x < imageWidth; x++) {
      for (int y = imageHeight - 1; y >= 0; y--) {
        preAllocatedBufferRotate[i++] = data[y * imageWidth + x];
      }
    }
    // Rotate the U and V color components
    int size = imageWidth * imageHeight;
    i = size * 3 / 2 - 1;
    for (int x = imageWidth - 1; x > 0; x = x - 2) {
      for (int y = 0; y < imageHeight / 2; y++) {
        preAllocatedBufferRotate[i--] = data[size + (y * imageWidth) + x];
        preAllocatedBufferRotate[i--] = data[size + (y * imageWidth) + (x - 1)];
      }
    }
    return preAllocatedBufferRotate;
  }

  public static byte[] rotate180(byte[] data, int imageWidth, int imageHeight) {
    int count = 0;
    for (int i = imageWidth * imageHeight - 1; i >= 0; i--) {
      preAllocatedBufferRotate[count] = data[i];
      count++;
    }
    for (int i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth * imageHeight; i -= 2) {
      preAllocatedBufferRotate[count++] = data[i - 1];
      preAllocatedBufferRotate[count++] = data[i];
    }
    return preAllocatedBufferRotate;
  }

  public static byte[] rotate270(byte[] data, int imageWidth, int imageHeight) {
    // Rotate the Y luma
    int i = 0;
    for (int x = imageWidth - 1; x >= 0; x--) {
      for (int y = 0; y < imageHeight; y++) {
        preAllocatedBufferRotate[i++] = data[y * imageWidth + x];
      }
    }

    // Rotate the U and V color components
    i = imageWidth * imageHeight;
    int uvHeight = imageHeight / 2;
    for (int x = imageWidth - 1; x >= 0; x -= 2) {
      for (int y = imageHeight; y < uvHeight + imageHeight; y++) {
        preAllocatedBufferRotate[i++] = data[y * imageWidth + x - 1];
        preAllocatedBufferRotate[i++] = data[y * imageWidth + x];
      }
    }
    return preAllocatedBufferRotate;
  }

  public static byte[] rotatePixels(byte[] input, int width, int height, int rotation) {
    byte[] output = new byte[input.length];

    boolean swap = (rotation == 90 || rotation == 270);
    boolean yflip = (rotation == 90 || rotation == 180);
    boolean xflip = (rotation == 270 || rotation == 180);
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        int xo = x, yo = y;
        int w = width, h = height;
        int xi = xo, yi = yo;
        if (swap) {
          xi = w * yo / h;
          yi = h * xo / w;
        }
        if (yflip) {
          yi = h - yi - 1;
        }
        if (xflip) {
          xi = w - xi - 1;
        }
        output[w * yo + xo] = input[w * yi + xi];
        int fs = w * h;
        int qs = (fs >> 2);
        xi = (xi >> 1);
        yi = (yi >> 1);
        xo = (xo >> 1);
        yo = (yo >> 1);
        w = (w >> 1);
        h = (h >> 1);
        // adjust for interleave here
        int ui = fs + (w * yi + xi) * 2;
        int uo = fs + (w * yo + xo) * 2;
        // and here
        int vi = ui + 1;
        int vo = uo + 1;
        output[uo] = input[ui];
        output[vo] = input[vi];
      }
    }
    return output;
  }

  public static byte[] mirror(byte[] input, int width, int height) {
    byte[] output = new byte[input.length];

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        int xo = x, yo = y;
        int w = width, h = height;
        int xi = xo, yi = yo;
        yi = h - yi - 1;
        output[w * yo + xo] = input[w * yi + xi];
        int fs = w * h;
        int qs = (fs >> 2);
        xi = (xi >> 1);
        yi = (yi >> 1);
        xo = (xo >> 1);
        yo = (yo >> 1);
        w = (w >> 1);
        h = (h >> 1);
        // adjust for interleave here
        int ui = fs + (w * yi + xi) * 2;
        int uo = fs + (w * yo + xo) * 2;
        // and here
        int vi = ui + 1;
        int vo = uo + 1;
        output[uo] = input[ui];
        output[vo] = input[vi];
      }
    }
    return output;
  }
}
