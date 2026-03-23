/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.encoder.video;

import android.media.MediaCodec;
import android.util.Pair;

import com.pedro.common.av1.Av1Parser;
import com.pedro.common.av1.Obu;
import com.pedro.common.av1.ObuType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pedro on 3/12/25.
 */
public class VideoEncoderHelper {
  /**
   * decode sps and pps if the encoder never call to MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
   */
  public static Pair<ByteBuffer, ByteBuffer> decodeSpsPpsFromBuffer(ByteBuffer outputBuffer, int length) {
    byte[] csd = new byte[length];
    outputBuffer.get(csd, 0, length);
    outputBuffer.rewind();
    int i = 0;
    int spsIndex = -1;
    int ppsIndex = -1;
    while (i < length - 4) {
      if (csd[i] == 0 && csd[i + 1] == 0 && csd[i + 2] == 0 && csd[i + 3] == 1) {
        if (spsIndex == -1) {
          spsIndex = i;
        } else {
          ppsIndex = i;
          break;
        }
      }
      i++;
    }
    if (spsIndex != -1 && ppsIndex != -1) {
      byte[] sps = new byte[ppsIndex];
      System.arraycopy(csd, spsIndex, sps, 0, ppsIndex);
      byte[] pps = new byte[length - ppsIndex];
      System.arraycopy(csd, ppsIndex, pps, 0, length - ppsIndex);
      return new Pair<>(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps));
    }
    return null;
  }

  /**
   * You need find 0 0 0 1 byte sequence that is the initiation of vps, sps and pps
   * buffers.
   *
   * @param csd0byteBuffer get in mediacodec case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
   * @return list with vps, sps and pps
   */
  public static List<ByteBuffer> extractVpsSpsPpsFromH265(ByteBuffer csd0byteBuffer) {
    List<ByteBuffer> byteBufferList = new ArrayList<>();
    int vpsPosition = -1;
    int spsPosition = -1;
    int ppsPosition = -1;
    int contBufferInitiation = 0;
    int length = csd0byteBuffer.remaining();
    byte[] csdArray = new byte[length];
    csd0byteBuffer.get(csdArray, 0, length);
    csd0byteBuffer.rewind();
    for (int i = 0; i < csdArray.length; i++) {
      if (contBufferInitiation == 3 && csdArray[i] == 1) {
        if (vpsPosition == -1) {
          vpsPosition = i - 3;
        } else if (spsPosition == -1) {
          spsPosition = i - 3;
        } else {
          ppsPosition = i - 3;
        }
      }
      if (csdArray[i] == 0) {
        contBufferInitiation++;
      } else {
        contBufferInitiation = 0;
      }
    }
    if (vpsPosition == -1 || spsPosition == -1 || ppsPosition == -1) return byteBufferList;
    byte[] vps = new byte[spsPosition];
    byte[] sps = new byte[ppsPosition - spsPosition];
    byte[] pps = new byte[csdArray.length - ppsPosition];
    for (int i = 0; i < csdArray.length; i++) {
      if (i < spsPosition) {
        vps[i] = csdArray[i];
      } else if (i < ppsPosition) {
        sps[i - spsPosition] = csdArray[i];
      } else {
        pps[i - ppsPosition] = csdArray[i];
      }
    }
    byteBufferList.add(ByteBuffer.wrap(vps));
    byteBufferList.add(ByteBuffer.wrap(sps));
    byteBufferList.add(ByteBuffer.wrap(pps));
    return byteBufferList;
  }

  /**
   *
   * @param buffer key frame
   * @return av1 ObuSequence
   */
  public static ByteBuffer extractObuSequence(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
    //we can only extract info from keyframes
    if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_KEY_FRAME) return null;
    byte[] av1Data = new byte[buffer.remaining()];
    buffer.get(av1Data);
    Av1Parser av1Parser = new Av1Parser();
    List<Obu> obuList = av1Parser.getObus(av1Data);
    for (Obu obu: obuList) {
      if (av1Parser.getObuType(obu.getHeader()[0]) == ObuType.SEQUENCE_HEADER) {
        return ByteBuffer.wrap(obu.getFullData());
      }
    }
    return null;
  }
}
