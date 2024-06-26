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

package com.pedro.rtmp.flv.video.config

import java.nio.ByteBuffer

/**
 * Created by pedro on 29/04/21.
 *
 *  ISO/IEC 14496-15
 *
 *  AVCDecoderConfigurationRecord
 *
 * 5 bytes sps/pps header:
 * 1 byte configurationVersion (always 1), 1 byte AVCProfileIndication, 1 byte profile_compatibility,
 * 1 byte AVCLevelIndication, 1 byte lengthSizeMinusOneWithReserved (always 0xff)
 * 3 bytes size of sps:
 * 1 byte numOfSequenceParameterSetsWithReserved (always 0xe1), 2 bytes sequenceParameterSetLength(2B) (sps size)
 * N bytes of sps.
 * sequenceParameterSetNALUnit (sps data)
 * 3 bytes size of pps:
 * 1 byte numOfPictureParameterSets (always 1), 2 bytes pictureParameterSetLength (pps size)
 * N bytes of pps:
 * pictureParameterSetNALUnit (pps data)
 */
class VideoSpecificConfigAVC(private val sps: ByteArray, private val pps: ByteArray) {

  val size = calculateSize(sps, pps)

  fun write(buffer: ByteArray, offset: Int) {
    val data = ByteBuffer.wrap(buffer, offset, size)
    //5 bytes sps/pps header
    data.put(0x01)
    val profileIdc = sps[1]
    data.put(profileIdc)
    val profileCompatibility = sps[2]
    data.put(profileCompatibility)
    val levelIdc = sps[3]
    data.put(levelIdc)
    data.put(0xff.toByte())
    //3 bytes size of sps
    data.put(0xe1.toByte())
    data.putShort(sps.size.toShort())
    //N bytes of sps
    data.put(sps)
    //3 bytes size of pps
    data.put(0x01)
    data.putShort(pps.size.toShort())
    //N bytes of pps
    data.put(pps)
  }

  private fun calculateSize(sps: ByteArray, pps: ByteArray): Int {
    return 5 + 3 + sps.size + 3 + pps.size
  }
}