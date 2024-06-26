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

package com.pedro.rtmp.rtmp.message.data

import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.RtmpMessage

/**
 * Created by pedro on 21/04/21.
 */
abstract class Data(timeStamp: Int, streamId: Int, basicHeader: BasicHeader): RtmpMessage(basicHeader) {

  protected var bodySize = 0

  init {
    header.messageLength = bodySize
    header.timeStamp = timeStamp
    header.messageStreamId = streamId
  }

  override fun getSize(): Int = bodySize
}