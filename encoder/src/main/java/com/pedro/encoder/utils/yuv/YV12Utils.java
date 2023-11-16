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

public class YV12Utils {

  private static byte[] preAllocatedBufferRotate;
  private static byte[] preAllocatedBufferColor;

  public static void preAllocateBuffers(int length) {
    preAllocatedBufferRotate = new byte[length];
    preAllocatedBufferColor = new byte[length];
  }

  // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
  public static byte[] toNV12(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
      preAllocatedBufferColor[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] toI420(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    System.arraycopy(input, frameSize + qFrameSize, preAllocatedBufferColor, frameSize,
        qFrameSize); // Cb (U)
    System.arraycopy(input, frameSize, preAllocatedBufferColor, frameSize + qFrameSize,
        qFrameSize); // Cr (V)
    return preAllocatedBufferColor;
  }

  public static byte[] toNV21(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i * 2 + 1] = input[frameSize + i + qFrameSize]; // Cb (U)
      preAllocatedBufferColor[frameSize + i * 2] = input[frameSize + i]; // Cr (V)
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
    final int size = imageWidth * imageHeight;
    final int colorSize = size / 4;
    final int colorHeight = colorSize / imageWidth;
    // Rotate the U and V color components
    for (int x = 0; x < imageWidth / 2; x++) {
      for (int y = colorHeight - 1; y >= 0; y--) {
        //V
        preAllocatedBufferRotate[i + colorSize] =
            data[colorSize + size + (imageWidth * y) + x + (imageWidth / 2)];
        preAllocatedBufferRotate[i + colorSize + 1] = data[colorSize + size + (imageWidth * y) + x];
        //U
        preAllocatedBufferRotate[i++] = data[size + (imageWidth * y) + x + (imageWidth / 2)];
        preAllocatedBufferRotate[i++] = data[size + (imageWidth * y) + x];
      }
    }
    return preAllocatedBufferRotate;
  }

  public static byte[] rotate180(byte[] data, int imageWidth, int imageHeight) {
    int count = 0;
    final int size = imageWidth * imageHeight;
    for (int i = size - 1; i >= 0; i--) {
      preAllocatedBufferRotate[count++] = data[i];
    }
    final int midColorSize = size / 4;
    //U
    for (int i = size + midColorSize - 1; i >= size; i--) {
      preAllocatedBufferRotate[count++] = data[i];
    }
    //V
    for (int i = data.length - 1; i >= imageWidth * imageHeight + midColorSize; i--) {
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
    final int size = imageWidth * imageHeight;
    final int colorSize = size / 4;
    final int colorHeight = colorSize / imageWidth;

    for (int x = 0; x < imageWidth / 2; x++) {
      for (int y = 0; y < colorHeight; y++) {
        //V
        preAllocatedBufferRotate[i + colorSize] =
            data[colorSize + size + (imageWidth * y) - x + (imageWidth / 2) - 1];
        preAllocatedBufferRotate[i + colorSize + 1] =
            data[colorSize + size + (imageWidth * y) - x + imageWidth - 1];
        //U
        preAllocatedBufferRotate[i++] = data[size + (imageWidth * y) - x + (imageWidth / 2) - 1];
        preAllocatedBufferRotate[i++] = data[size + (imageWidth * y) - x + imageWidth - 1];
      }
    }
    return preAllocatedBufferRotate;
  }
}
