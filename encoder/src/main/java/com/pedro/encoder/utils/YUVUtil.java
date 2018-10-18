package com.pedro.encoder.utils;

import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.os.Environment;
import com.pedro.encoder.input.video.Frame;
import com.pedro.encoder.video.FormatVideoEncoder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by pedro on 25/01/17.
 * https://wiki.videolan.org/YUV/#I420
 */

public class YUVUtil {

  private static byte[] preAllocatedBufferRotate;
  private static byte[] preAllocatedBufferColor;

  public static void preAllocateBuffers(int length) {
    preAllocatedBufferRotate = new byte[length];
    preAllocatedBufferColor = new byte[length];
  }

  public static Bitmap frameToBitmap(Frame frame, int width, int height, int orientation) {
    int w = (orientation == 90 || orientation == 270) ? height : width;
    int h = (orientation == 90 || orientation == 270) ? width : height;
    int[] argb = NV21toARGB(rotateNV21(frame.getBuffer(), width, height, orientation), w, h);
    return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
  }

  public static int[] NV21toARGB(byte[] yuv, int width, int height) {
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

  // for the vbuffer for YV12(android YUV), @see below:
  // https://developer.android.com/reference/android/hardware/Camera.Parameters.html#setPreviewFormat(int)
  // https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
  public static int getYuvBuffer(int width, int height) {
    // stride = ALIGN(width, 16)
    int stride = (int) Math.ceil(width / 16.0) * 16;
    // y_size = stride * height
    int y_size = stride * height;
    // c_stride = ALIGN(stride/2, 16)
    int c_stride = (int) Math.ceil(width / 32.0) * 16;
    // c_size = c_stride * height/2
    int c_size = c_stride * height / 2;
    // size = y_size + c_size * 2
    return y_size + c_size * 2;
  }

  public static byte[] ARGBtoYUV420SemiPlanar(int[] input, int width, int height) {
    /*
     * COLOR_FormatYUV420SemiPlanar is NV12
     */
    final int frameSize = width * height;
    byte[] yuv420sp = new byte[width * height * 3 / 2];
    int yIndex = 0;
    int uvIndex = frameSize;

    int a, R, G, B, Y, U, V;
    int index = 0;
    for (int j = 0; j < height; j++) {
      for (int i = 0; i < width; i++) {

        a = (input[index] & 0xff000000) >> 24; // a is not used obviously
        R = (input[index] & 0xff0000) >> 16;
        G = (input[index] & 0xff00) >> 8;
        B = (input[index] & 0xff) >> 0;

        // well known RGB to YUV algorithm
        Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
        U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
        V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

        // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
        //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
        //    pixel AND every other scanline.
        yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
        if (j % 2 == 0 && index % 2 == 0) {
          yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
          yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
        }

        index++;
      }
    }
    return yuv420sp;
  }

  public static byte[] YV12toYUV420byColor(byte[] input, int width, int height,
      FormatVideoEncoder formatVideoEncoder) {
    switch (formatVideoEncoder) {
      case YUV420PLANAR:
        return YV12toI420(input, width, height);
      case YUV420PACKEDSEMIPLANAR:
        return YV12toNV12(input, width, height);
      case YUV420SEMIPLANAR:
        return YV12toNV12(input, width, height);
      //convert to nv21 and then to i420.
      case YUV420PACKEDPLANAR:
        return NV21toI420(YV12toNV21(input, width, height), width, height);
      default:
        return null;
    }
  }

  // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
  public static byte[] YV12toNV12(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
      preAllocatedBufferColor[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] YV12toI420(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    System.arraycopy(input, frameSize + qFrameSize, preAllocatedBufferColor, frameSize,
        qFrameSize); // Cb (U)
    System.arraycopy(input, frameSize, preAllocatedBufferColor, frameSize + qFrameSize,
        qFrameSize); // Cr (V)
    return preAllocatedBufferColor;
  }

  public static byte[] YV12toNV21(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i * 2 + 1] = input[frameSize + i + qFrameSize]; // Cb (U)
      preAllocatedBufferColor[frameSize + i * 2] = input[frameSize + i]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] NV21toYUV420byColor(byte[] input, int width, int height,
      FormatVideoEncoder formatVideoEncoder) {
    switch (formatVideoEncoder) {
      case YUV420PLANAR:
        return NV21toI420(input, width, height);
      case YUV420PACKEDPLANAR:
        return NV21toI420(input, width, height);
      case YUV420SEMIPLANAR:
        return NV21toNV12(input, width, height);
      //yuv420PSP and NV21 is the same
      case YUV420PACKEDSEMIPLANAR:
        return input;
      default:
        return null;
    }
  }

  // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
  public static byte[] NV21toNV12(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i * 2] = input[frameSize + i * 2 + 1]; // Cb (U)
      preAllocatedBufferColor[frameSize + i * 2 + 1] = input[frameSize + i * 2]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] NV21toI420(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i] = input[frameSize + i * 2 + 1]; // Cb (U)
      preAllocatedBufferColor[frameSize + i + qFrameSize] = input[frameSize + i * 2]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] NV21toYV12(byte[] input, int width, int height) {
    final int frameSize = width * height;
    final int qFrameSize = frameSize / 4;
    System.arraycopy(input, 0, preAllocatedBufferColor, 0, frameSize); // Y
    for (int i = 0; i < qFrameSize; i++) {
      preAllocatedBufferColor[frameSize + i + qFrameSize] = input[frameSize + i * 2 + 1]; // Cb (U)
      preAllocatedBufferColor[frameSize + i] = input[frameSize + i * 2]; // Cr (V)
    }
    return preAllocatedBufferColor;
  }

  public static byte[] rotateNV21(byte[] data, int width, int height, int rotation) {
    switch (rotation) {
      case 0:
        return data;
      case 90:
        return rotateNV21Degree90(data, width, height);
      case 180:
        return rotateNV21Degree180(data, width, height);
      case 270:
        return rotateNV21Degree270(data, width, height);
      default:
        return null;
    }
  }

  private static byte[] rotateNV21Degree90(byte[] data, int imageWidth, int imageHeight) {
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

  private static byte[] rotateNV21Degree180(byte[] data, int imageWidth, int imageHeight) {
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

  private static byte[] rotateNV21Degree270(byte[] data, int imageWidth, int imageHeight) {
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

  public void dumpYUVData(byte[] buffer, int len, String name) {
    File f = new File(Environment.getExternalStorageDirectory().getPath() + "/tmp/", name);
    if (f.exists()) {
      f.delete();
    }
    try {
      FileOutputStream out = new FileOutputStream(f);
      out.write(buffer);
      out.flush();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static byte[] CropYuv(int src_format, byte[] src_yuv, int src_width, int src_height,
      int dst_width, int dst_height) {
    byte[] dst_yuv;
    if (src_yuv == null) return null;
    // simple implementation: copy the corner
    if (src_width == dst_width && src_height == dst_height) {
      dst_yuv = src_yuv;
    } else {
      dst_yuv = new byte[(int) (dst_width * dst_height * 1.5)];
      switch (src_format) {
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // I420
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // YV12
        {
          // copy Y
          int src_yoffset = 0;
          int dst_yoffset = 0;
          for (int i = 0; i < dst_height; i++) {
            System.arraycopy(src_yuv, src_yoffset, dst_yuv, dst_yoffset, dst_width);
            src_yoffset += src_width;
            dst_yoffset += dst_width;
          }

          // copy u
          int src_uoffset = 0;
          int dst_uoffset = 0;
          src_yoffset = src_width * src_height;
          dst_yoffset = dst_width * dst_height;
          for (int i = 0; i < dst_height / 2; i++) {
            System.arraycopy(src_yuv, src_yoffset + src_uoffset, dst_yuv, dst_yoffset + dst_uoffset,
                dst_width / 2);
            src_uoffset += src_width / 2;
            dst_uoffset += dst_width / 2;
          }

          // copy v
          int src_voffset = 0;
          int dst_voffset = 0;
          src_uoffset = src_width * src_height + src_width * src_height / 4;
          dst_uoffset = dst_width * dst_height + dst_width * dst_height / 4;
          for (int i = 0; i < dst_height / 2; i++) {
            System.arraycopy(src_yuv, src_uoffset + src_voffset, dst_yuv, dst_uoffset + dst_voffset,
                dst_width / 2);
            src_voffset += src_width / 2;
            dst_voffset += dst_width / 2;
          }
        }
        break;
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // NV12
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // NV21
        case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
        case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar: {
          // copy Y
          int src_yoffset = 0;
          int dst_yoffset = 0;
          for (int i = 0; i < dst_height; i++) {
            System.arraycopy(src_yuv, src_yoffset, dst_yuv, dst_yoffset, dst_width);
            src_yoffset += src_width;
            dst_yoffset += dst_width;
          }

          // copy u and v
          int src_uoffset = 0;
          int dst_uoffset = 0;
          src_yoffset = src_width * src_height;
          dst_yoffset = dst_width * dst_height;
          for (int i = 0; i < dst_height / 2; i++) {
            System.arraycopy(src_yuv, src_yoffset + src_uoffset, dst_yuv, dst_yoffset + dst_uoffset,
                dst_width);
            src_uoffset += src_width;
            dst_uoffset += dst_width;
          }
        }
        break;

        default: {
          dst_yuv = null;
        }
        break;
      }
    }
    return dst_yuv;
  }

  public static byte[] rotatePixelsNV21(byte[] input, int width, int height, int rotation) {
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

  public static byte[] mirrorNV21(byte[] input, int width, int height) {
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

  public static byte[] bitmapToNV21(int inputWidth, int inputHeight, Bitmap bitmap) {
    int[] argb = new int[inputWidth * inputHeight];
    bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
    byte[] yuv = ARGBtoYUV420SemiPlanar(argb, inputWidth, inputHeight);
    bitmap.recycle();
    return yuv;
  }
}
