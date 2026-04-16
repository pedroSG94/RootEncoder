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

package com.pedro.common

import com.pedro.common.frame.MediaFrame

/**
 * Lifecycle callback for frames sent through a [com.pedro.common.base.BaseSender].
 *
 * The callback is invoked from the **sender's dispatch thread** (IO coroutine) after the
 * sender has fully consumed a [MediaFrame] — i.e., after the frame data has been encoded
 * into protocol packets and written to the network socket. At that point the [MediaFrame.data]
 * buffer is no longer referenced by the sender and may be safely returned to a buffer pool.
 *
 * ## Thread guarantee
 * [onFrameConsumed] is called on the sender's internal coroutine dispatcher (typically
 * [kotlinx.coroutines.Dispatchers.IO]). Implementations must not block this thread.
 *
 * ## Usage with FramePayloadPool (RTMP pooled-copy)
 * ```kotlin
 * rtmpSender.frameLifecycleListener = FrameLifecycleListener { frame ->
 *     (frame.data as? PooledBuffer)?.release()
 * }
 * ```
 */
fun interface FrameLifecycleListener {

    /**
     * Called after [frame] has been fully consumed by the sender.
     *
     * The sender will not access [frame] or [MediaFrame.data] after this call.
     * The implementation may safely release, recycle, or pool the underlying buffer.
     *
     * @param frame  The frame that was consumed. Same instance that was passed to
     *               [com.pedro.common.base.BaseSender.sendMediaFrame].
     */
    fun onFrameConsumed(frame: MediaFrame)
}
