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

package com.pedro.encoder.audio;

/**
 * PCM to G711U encoder/decoder
 */
public class G711Codec {

    private final static byte[] table13to8 = new byte[8192];
    private final static short[] table8to16 = new short[256];

    static {
        // b13 --> b8
        for (int p = 1, q = 0; p <= 0x80; p <<= 1, q += 0x10) {
            for (int i = 0, j = (p << 4) - 0x10; i < 16; i++, j += p) {
                int v = (i + q) ^ 0x7F;
                byte value1 = (byte) v;
                byte value2 = (byte) (v + 128);
                for (int m = j, e = j + p; m < e; m++) {
                    table13to8[m] = value1;
                    table13to8[8191 - m] = value2;
                }
            }
        }

        // b8 --> b16
        for (int q = 0; q <= 7; q++) {
            for (int i = 0, m = (q << 4); i < 16; i++, m++) {
                int v = (((i + 0x10) << q) - 0x10) << 3;
                table8to16[m ^ 0x7F] = (short) v;
                table8to16[(m ^ 0x7F) + 128] = (short) (65536 - v);
            }
        }
    }

    public void configure(int sampleRate, int channels) {
        if (sampleRate != 8000 || channels != 1) {
            throw new IllegalArgumentException("G711 codec only support 8000 sampleRate and mono channel");
        }
    }

    public byte[] decode(byte[] buffer, int offset, int size) {
        byte[] out = new byte[size * 2];
        for (int i = 0, j = offset; i < size; i++, j++) {
            short sample = table8to16[(short) ((buffer[2 * j] & 0xFF) | (buffer[2 * j + 1] << 8)) & 0xFF];
            out[2 * i] = (byte) (sample & 0xFF);
            out[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return out;
    }

    public byte[] encode(byte[] buffer, int offset, int size) {
        byte[] out = new byte[size / 2];
        for (int i = 0, j = offset; i < out.length; i++, j++) {
            short sample = (short) ((buffer[2 * i] & 0xFF) | (buffer[2 * i + 1] << 8));
            out[j] = table13to8[(sample >> 4) & 0x1FFF];
        }
        return out;
    }
}