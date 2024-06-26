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

package com.pedro.encoder.utils.yuv;

import android.graphics.Bitmap;
import android.media.MediaCodecInfo;

import com.pedro.encoder.Frame;
import com.pedro.encoder.video.FormatVideoEncoder;

/**
 * Created by pedro on 25/01/17.
 * https://wiki.videolan.org/YUV/#I420
 *
 * Example YUV images 4x4 px.
 *
 * NV21 example:
 *
 * Y1   Y2   Y3   Y4
 * Y5   Y6   Y7   Y8
 * Y9   Y10  Y11  Y12
 * Y13  Y14  Y15  Y16
 * U1   V1   U2   V2
 * U3   V3   U4   V4
 *
 *
 * YV12 example:
 *
 * Y1   Y2   Y3   Y4
 * Y5   Y6   Y7   Y8
 * Y9   Y10  Y11  Y12
 * Y13  Y14  Y15  Y16
 * U1   U2   U3   U4
 * V1   V2   V3   V4
 *
 *
 * YUV420 planar example (I420):
 *
 * Y1   Y2   Y3   Y4
 * Y5   Y6   Y7   Y8
 * Y9   Y10  Y11  Y12
 * Y13  Y14  Y15  Y16
 * V1   V2   V3   V4
 * U1   U2   U3   U4
 *
 *
 * YUV420 semi planar example (NV12):
 *
 * Y1   Y2   Y3   Y4
 * Y5   Y6   Y7   Y8
 * Y9   Y10  Y11  Y12
 * Y13  Y14  Y15  Y16
 * V1   U1   V2   U2
 * V3   U3   V4   U4
 */

public class YUVUtil {


  public static void preAllocateBuffers(int length) {
    NV21Utils.preAllocateBuffers(length);
    YV12Utils.preAllocateBuffers(length);
  }

  public static byte[] NV21toYUV420byColor(byte[] input, int width, int height,
      FormatVideoEncoder formatVideoEncoder) {
    switch (formatVideoEncoder) {
      case YUV420PLANAR:
        return NV21Utils.toI420(input, width, height);
      case YUV420SEMIPLANAR:
        return NV21Utils.toNV12(input, width, height);
      default:
        return null;
    }
  }

  public static byte[] rotateNV21(byte[] data, int width, int height, int rotation) {
    switch (rotation) {
      case 0:
        return data;
      case 90:
        return NV21Utils.rotate90(data, width, height);
      case 180:
        return NV21Utils.rotate180(data, width, height);
      case 270:
        return NV21Utils.rotate270(data, width, height);
      default:
        return null;
    }
  }

  public static byte[] YV12toYUV420byColor(byte[] input, int width, int height,
      FormatVideoEncoder formatVideoEncoder) {
    switch (formatVideoEncoder) {
      case YUV420PLANAR:
        return YV12Utils.toI420(input, width, height);
      case YUV420SEMIPLANAR:
        return YV12Utils.toNV12(input, width, height);
      default:
        return null;
    }
  }

  public static byte[] rotateYV12(byte[] data, int width, int height, int rotation) {
    switch (rotation) {
      case 0:
        return data;
      case 90:
        return YV12Utils.rotate90(data, width, height);
      case 180:
        return YV12Utils.rotate180(data, width, height);
      case 270:
        return YV12Utils.rotate270(data, width, height);
      default:
        return null;
    }
  }

  public static Bitmap frameToBitmap(Frame frame, int width, int height, int orientation) {
    int w = (orientation == 90 || orientation == 270) ? height : width;
    int h = (orientation == 90 || orientation == 270) ? width : height;
    int[] argb = NV21Utils.toARGB(rotateNV21(frame.getBuffer(), width, height, orientation), w, h);
    return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
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
}
