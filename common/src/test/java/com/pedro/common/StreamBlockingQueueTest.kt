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

package com.pedro.common

import com.pedro.common.frame.MediaFrame
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Created by pedro on 2/12/25.
 */
class StreamBlockingQueueTest {

  @Test
  fun checkItemLimit() {
    val queue = StreamBlockingQueue(50)
    val frame = MediaFrame(ByteBuffer.wrap(byteArrayOf()), MediaFrame.Info(0, 0, 0L, false), MediaFrame.Type.VIDEO)
    (0..60).forEach { i ->
      queue.trySend(frame)
    }
    assertEquals(50, queue.getSize())
    assertEquals(false, queue.trySend(frame))
  }
}