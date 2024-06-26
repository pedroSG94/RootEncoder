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

package com.pedro.common.av1

/**
 * Created by pedro on 8/12/23.
 */
enum class ObuType(val value: Int) {
  RESERVED(0),
  SEQUENCE_HEADER(1),
  TEMPORAL_DELIMITER(2),
  FRAME_HEADER(3),
  TILE_GROUP(4),
  METADATA(5),
  FRAME(6),
  REDUNDANT_FRAME_HEADER(7),
  TILE_LIST(8),
  //9-14 Reserved
  PADDING(15)
}