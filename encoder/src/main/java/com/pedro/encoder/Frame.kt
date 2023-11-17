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
package com.pedro.encoder

import android.graphics.ImageFormat

/**
 * Created by pedro on 17/02/18.
 */
class Frame {
  var buffer: ByteArray
  var offset: Int
  var size: Int
  var orientation = 0
  var isFlip = false
  var format = ImageFormat.NV21 //nv21 or yv12 supported
  var timeStamp: Long

  /**
   * Used with video frame
   */
  constructor(buffer: ByteArray, orientation: Int, flip: Boolean, format: Int, timeStamp: Long) {
    this.buffer = buffer
    this.orientation = orientation
    isFlip = flip
    this.format = format
    offset = 0
    size = buffer.size
    this.timeStamp = timeStamp
  }

  /**
   * Used with audio frame
   */
  constructor(buffer: ByteArray, offset: Int, size: Int, timeStamp: Long) {
    this.buffer = buffer
    this.offset = offset
    this.size = size
    this.timeStamp = timeStamp
  }
}