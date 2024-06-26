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

package com.pedro.rtmp.flv.audio

/**
 * Created by pedro on 29/04/21.
 * list of audio codec supported by FLV
 */
enum class AudioFormat(val value: Int) {
  PCM(0), ADPCM(1), MP3(2), PCM_LE(3), NELLYMOSER_16K(4),
  NELLYMOSER_8K(5), NELLYMOSER(6), G711_A(7), G711_MU(8), RESERVED(9),
  AAC(10), SPEEX(11), MP3_8K(14), DEVICE_SPECIFIC(15)
}