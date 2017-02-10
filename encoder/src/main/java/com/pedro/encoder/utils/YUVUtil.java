package com.pedro.encoder.utils;

import android.media.MediaCodecInfo;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by pedro on 25/01/17.
 */

public class YUVUtil {

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

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] YV12toYUV420PackedSemiPlanar(byte[] input, int width, int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV21
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        byte[] output = new byte[input.length];

        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2 + 1] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] YV12toYUV420SemiPlanar(byte[] input, int width, int height) {
        /*
         * COLOR_FormatYUV420SemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        byte[] output = new byte[input.length];

        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    public static byte[] NV21toYUV420PackedPlanar(byte[] input, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        byte[] output = new byte[input.length];

        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i + qFrameSize] = input[frameSize + i * 2 + 1]; // Cb (U)
            output[frameSize + i] = input[frameSize + i * 2]; // Cr (V)
        }

        return output;
    }

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] NV21toYUV420SemiPlanar(byte[] input, int width, int height) {
        /*
         * COLOR_FormatYUV420SemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        byte[] output = new byte[input.length];

        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i * 2 + 1]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i * 2]; // Cr (V)
        }
        return output;
    }

    public static byte[] NV21toYUV420Planar(byte[] input, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        byte[] output = new byte[input.length];

        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i] = input[frameSize + i * 2 + 1]; // Cb (U)
            output[frameSize + i + qFrameSize] = input[frameSize + i * 2]; // Cr (V)
        }

        return output;
    }

    public static byte[] YV12toYUV420Planar(byte[] input, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        byte[] output = new byte[input.length];

        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)

        return output;
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

    public static byte[] CropYuv(int src_format, byte[] src_yuv, int src_width, int src_height, int dst_width,
                                 int dst_height) {
        byte[] dst_yuv;
        if (src_yuv == null)
            return null;
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
                        System.arraycopy(src_yuv, src_yoffset + src_uoffset, dst_yuv, dst_yoffset + dst_uoffset, dst_width);
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

    public static byte[] rotateNV21(byte[] input, int width, int height, int rotation) {
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
}
