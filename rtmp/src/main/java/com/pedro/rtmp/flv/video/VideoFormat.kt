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
enum class VideoFormat(val value: Int) {
  SORENSON_H263(2), SCREEN_1(3), VP6(4), VP6_ALPHA(5),
  SCREEN_2(6), AVC(7), UNKNOWN(255),
  //fourCC extension
  HEVC(1752589105), // { "h", "v", "c", "1" }
  AV1(1635135537), // { "a", "v", "0", "1" }
  VP9(1987063865) // { "v", "p", "0", "9" }
}