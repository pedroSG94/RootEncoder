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

package com.pedro.rtmp.flv.video

/**
 * Created by pedro on 29/04/21.
 */
enum class VideoNalType(val value: Int) {
  UNSPEC(0), SLICE(1), DPA(2), DPB(3), DPC(4), IDR(5), SEI(6),
  SPS(7), PPS(8), AUD(9), EO_SEQ(10), EO_STREAM(11), FILL(12),
  HEVC_VPS(32), HEVC_SPS(33), HEVC_PPS(34),
  //H265 IDR
  IDR_N_LP(20), IDR_W_DLP(19)
}