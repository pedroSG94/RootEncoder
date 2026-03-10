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

package com.pedro.encoder.utils;

import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Patches H.264 / H.265 SPS NAL units to embed BT.709 colour metadata.
 *
 * Hardware encoders typically ignore MediaFormat KEY_COLOR_* when generating
 * the SPS VUI, so we must fix the raw bytes directly.
 *
 * Call sites:
 * - Recording path: patchMediaFormatColorToBt709(videoFormat) before
 * mediaMuxer.addTrack()
 * - Streaming path: patchSpsNalColorToBt709(spsBytes, isHevc) before
 * onVideoInfo()
 */
public final class SpsColorPatcher {

    private static final String TAG = "COLOR_PATCH";

    public SpsColorPatcher() {
    }

    /**
     * Patches csd-0 in a MediaFormat in-place before mediaMuxer.addTrack().
     * Auto-detects H.264 vs H.265 from the MIME type.
     */
    public void patchMediaFormatColorToBt709(MediaFormat format) {
        if (format == null || !format.containsKey("csd-0"))
            return;
        ByteBuffer csd0 = format.getByteBuffer("csd-0");
        String mime = format.containsKey(MediaFormat.KEY_MIME) ? format.getString(MediaFormat.KEY_MIME) : "";
        ByteBuffer patched;
        if ("video/hevc".equals(mime)) {
            patched = patchSpsNalColorToBt709(csd0, true);
        } else {
            // Try H.264 first; fall back to H.265 scan
            patched = patchSpsNalColorToBt709(csd0, false);
            if (patched == csd0)
                patched = patchSpsNalColorToBt709(csd0, true);
        }
        if (patched != csd0) {
            format.setByteBuffer("csd-0", patched);
            Log.d(TAG, "csd-0 patched with BT.709 colour info");
        }
    }

    /**
     * Patches colour_primaries / transfer_characteristics / matrix_coefficients
     * to BT.709 (1, 1, 1) in a raw SPS NAL ByteBuffer.
     *
     * Works for both:
     * - A full csd-0 buffer containing one or more NAL units (recording)
     * - A single raw SPS NAL ByteBuffer (streaming, passed to onVideoInfo)
     *
     * @param spsData ByteBuffer containing the NAL bytes
     * @param isHevc  true for H.265/HEVC, false for H.264/AVC
     * @return Patched ByteBuffer (new allocation), or original if patching
     *         failed/not needed
     */
    public ByteBuffer patchSpsNalColorToBt709(ByteBuffer spsData, boolean isHevc) {
        if (spsData == null || spsData.remaining() < 4)
            return spsData;
        byte[] data = new byte[spsData.remaining()];
        int savedPos = spsData.position();
        spsData.get(data);
        spsData.position(savedPos);

        return isHevc
                ? patchH265(data)
                : patchH264(data);
    }

    // -------------------------------------------------------------------------
    // H.264 SPS patcher
    // -------------------------------------------------------------------------

    private ByteBuffer patchH264(byte[] data) {
        // Find SPS NAL unit (NAL type 7)
        int spsStart = -1;
        for (int i = 0; i < data.length; i++) {
            if ((data[i] & 0x1F) == 7) {
                spsStart = i + 1;
                break;
            }
        }
        if (spsStart < 0) {
            Log.w(TAG, "H.264 SPS NAL (type 7) not found");
            return ByteBuffer.wrap(data);
        }

        byte[] patched = data.clone();
        BitReader br = new BitReader(patched, spsStart);
        try {
            int profile_idc = br.readBits(8);
            br.skipBits(16); // constraint flags + level_idc
            br.readUVLC(); // seq_parameter_set_id

            if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 || profile_idc == 244
                    || profile_idc == 44 || profile_idc == 83 || profile_idc == 86
                    || profile_idc == 118 || profile_idc == 128 || profile_idc == 138) {
                int chroma = br.readUVLC();
                if (chroma == 3)
                    br.skipBits(1);
                br.readUVLC();
                br.readUVLC();
                br.skipBits(1);
                if (br.readBits(1) != 0) {
                    int cnt = (chroma != 3) ? 8 : 12;
                    for (int i = 0; i < cnt; i++)
                        if (br.readBits(1) != 0)
                            br.skipScalingList(i < 6 ? 16 : 64);
                }
            }

            br.readUVLC(); // log2_max_frame_num_minus4
            int poc = br.readUVLC();
            if (poc == 0) {
                br.readUVLC();
            } else if (poc == 1) {
                br.skipBits(1);
                br.readSVLC();
                br.readSVLC();
                int n = br.readUVLC();
                for (int i = 0; i < n; i++)
                    br.readSVLC();
            }

            br.readUVLC();
            br.skipBits(1); // max_num_ref_frames, gaps_flag
            br.readUVLC();
            br.readUVLC(); // pic_width/height
            if (br.readBits(1) == 0)
                br.skipBits(1); // frame_mbs_only_flag / mb_adaptive
            br.skipBits(1); // direct_8x8_inference_flag
            if (br.readBits(1) != 0) {
                br.readUVLC();
                br.readUVLC();
                br.readUVLC();
                br.readUVLC();
            } // crop

            if (br.readBits(1) == 0) {
                Log.w(TAG, "H.264 no VUI");
                return ByteBuffer.wrap(data);
            }

            if (br.readBits(1) != 0) {
                if (br.readBits(8) == 255)
                    br.skipBits(32);
            } // aspect ratio
            if (br.readBits(1) != 0)
                br.skipBits(1); // overscan

            if (br.readBits(1) != 0) { // video_signal_type_present_flag
                br.skipBits(4); // video_format + full_range
                if (br.readBits(1) != 0) { // colour_description_present_flag
                    br.writeBits(1, 8); // colour_primaries = BT.709
                    br.writeBits(1, 8); // transfer_characteristics = BT.709
                    br.writeBits(1, 8); // matrix_coefficients = BT.709
                    Log.d(TAG, "H.264 SPS colour patched → BT.709 at bit " + br.getBitPos());
                    return ByteBuffer.wrap(patched);
                }
                Log.w(TAG, "H.264 colour_description_present_flag=0");
            } else {
                Log.w(TAG, "H.264 video_signal_type_present_flag=0");
            }
        } catch (Exception e) {
            Log.e(TAG, "H.264 SPS parse error", e);
        }
        return ByteBuffer.wrap(data);
    }

    // -------------------------------------------------------------------------
    // H.265 SPS patcher
    // -------------------------------------------------------------------------

    private ByteBuffer patchH265(byte[] data) {
        // Find H.265 SPS NAL unit (nal_unit_type = 33, 2-byte HEVC NAL header)
        int spsStart = -1;
        for (int i = 0; i < data.length - 1; i++) {
            if (((data[i] >> 1) & 0x3F) == 33) {
                spsStart = i + 2;
                break;
            }
        }
        if (spsStart < 0) {
            Log.w(TAG, "H.265 SPS NAL (type 33) not found");
            return ByteBuffer.wrap(data);
        }

        byte[] patched = data.clone();
        BitReader br = new BitReader(patched, spsStart);
        try {
            br.skipBits(4); // sps_video_parameter_set_id
            int M = br.readBits(3); // sps_max_sub_layers_minus1
            br.skipBits(1); // sps_temporal_id_nesting_flag

            // profile_tier_level
            br.skipBits(96);
            br.skipBits(8); // general profile (96b) + level_idc (8b)
            boolean[] profPresent = new boolean[M], levelPresent = new boolean[M];
            for (int i = 0; i < M; i++) {
                profPresent[i] = br.readBits(1) != 0;
                levelPresent[i] = br.readBits(1) != 0;
            }
            if (M > 0)
                br.skipBits(2 * (8 - M));
            for (int i = 0; i < M; i++) {
                if (profPresent[i])
                    br.skipBits(96);
                if (levelPresent[i])
                    br.skipBits(8);
            }

            br.readUVLC(); // sps_seq_parameter_set_id
            int chroma = br.readUVLC();
            if (chroma == 3)
                br.skipBits(1);
            br.readUVLC();
            br.readUVLC(); // width, height
            if (br.readBits(1) != 0) {
                br.readUVLC();
                br.readUVLC();
                br.readUVLC();
                br.readUVLC();
            } // conf window
            br.readUVLC();
            br.readUVLC(); // bit depth luma/chroma
            int log2MaxPoc = br.readUVLC() + 4;
            int start = br.readBits(1) != 0 ? 0 : M;
            for (int i = start; i <= M; i++) {
                br.readUVLC();
                br.readUVLC();
                br.readUVLC();
            }
            for (int i = 0; i < 6; i++)
                br.readUVLC(); // log2/diff block/transform sizes + depth inter/intra
            if (br.readBits(1) != 0 && br.readBits(1) != 0)
                skipH265ScalingListData(br);
            br.skipBits(2); // amp_enabled + sample_adaptive_offset
            if (br.readBits(1) != 0) {
                br.skipBits(8);
                br.readUVLC();
                br.readUVLC();
                br.skipBits(1);
            } // pcm

            int numStRps = br.readUVLC();
            int[] deltaPocs = new int[numStRps];
            for (int i = 0; i < numStRps; i++)
                deltaPocs[i] = skipH265StRefPicSet(br, i, deltaPocs);
            if (br.readBits(1) != 0) {
                int numLt = br.readUVLC();
                for (int i = 0; i < numLt; i++) {
                    br.skipBits(log2MaxPoc);
                    br.skipBits(1);
                }
            }
            br.skipBits(2); // temporal_mvp + strong_intra_smoothing

            if (br.readBits(1) == 0) {
                Log.w(TAG, "H.265 no VUI");
                return ByteBuffer.wrap(data);
            }

            if (br.readBits(1) != 0) {
                if (br.readBits(8) == 255)
                    br.skipBits(32);
            } // aspect ratio
            if (br.readBits(1) != 0)
                br.skipBits(1); // overscan

            if (br.readBits(1) != 0) { // video_signal_type_present_flag
                br.skipBits(4);
                if (br.readBits(1) != 0) { // colour_description_present_flag
                    br.writeBits(1, 8);
                    br.writeBits(1, 8);
                    br.writeBits(1, 8);
                    Log.d(TAG, "H.265 SPS colour patched → BT.709 at bit " + br.getBitPos());
                    return ByteBuffer.wrap(patched);
                }
                Log.w(TAG, "H.265 colour_description_present_flag=0");
            } else {
                Log.w(TAG, "H.265 video_signal_type_present_flag=0");
            }
        } catch (Exception e) {
            Log.e(TAG, "H.265 SPS parse error", e);
        }
        return ByteBuffer.wrap(data);
    }

    private int skipH265StRefPicSet(BitReader br, int idx, int[] deltaPocs) {
        if (idx != 0 && br.readBits(1) != 0) {
            if (idx == deltaPocs.length)
                br.readUVLC();
            br.skipBits(1);
            br.readUVLC();
            int numPics = deltaPocs[idx - 1] + 1;
            for (int j = 0; j < numPics; j++)
                if (br.readBits(1) == 0)
                    br.skipBits(1);
            return numPics;
        }
        int neg = br.readUVLC(), pos = br.readUVLC();
        for (int i = 0; i < neg; i++) {
            br.readUVLC();
            br.skipBits(1);
        }
        for (int i = 0; i < pos; i++) {
            br.readUVLC();
            br.skipBits(1);
        }
        return neg + pos;
    }

    private void skipH265ScalingListData(BitReader br) {
        for (int s = 0; s < 4; s++) {
            int nm = (s == 3) ? 2 : 6;
            for (int m = 0; m < nm; m++) {
                if (br.readBits(1) == 0) {
                    br.readUVLC();
                } else {
                    int coef = Math.min(64, 1 << (4 + (s << 1)));
                    if (s > 1)
                        br.readSVLC();
                    for (int i = 0; i < coef; i++)
                        br.readSVLC();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Minimal bit-level reader/writer (Exp-Golomb aware)
    // -------------------------------------------------------------------------

    final class BitReader {
        private final byte[] buf;
        private int bitPos;

        BitReader(byte[] buf, int startByte) {
            this.buf = buf;
            this.bitPos = startByte * 8;
        }

        int getBitPos() {
            return bitPos;
        }

        int readBits(int n) {
            int v = 0;
            for (int i = 0; i < n; i++) {
                int mask = 1 << (7 - (bitPos % 8));
                v = (v << 1) | ((buf[bitPos / 8] & mask) != 0 ? 1 : 0);
                bitPos++;
            }
            return v;
        }

        void writeBits(int value, int n) {
            for (int i = n - 1; i >= 0; i--) {
                int shift = 7 - (bitPos % 8);
                if (((value >> i) & 1) != 0)
                    buf[bitPos / 8] |= (byte) (1 << shift);
                else
                    buf[bitPos / 8] &= (byte) ~(1 << shift);
                bitPos++;
            }
        }

        void skipBits(int n) {
            bitPos += n;
        }

        int readUVLC() {
            int z = 0;
            while (readBits(1) == 0)
                z++;
            return z == 0 ? 0 : (1 << z) - 1 + readBits(z);
        }

        int readSVLC() {
            int c = readUVLC();
            return (c % 2 == 0) ? -(c / 2) : (c + 1) / 2;
        }

        void skipScalingList(int size) {
            int last = 8, next = 8;
            for (int j = 0; j < size; j++) {
                if (next != 0) {
                    int d = readSVLC();
                    next = (last + d + 256) % 256;
                }
                last = (next == 0) ? last : next;
            }
        }
    }
}
