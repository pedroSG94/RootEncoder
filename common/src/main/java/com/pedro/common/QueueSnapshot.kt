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

/**
 * Point-in-time snapshot of a sender's internal frame queue.
 *
 * Returned by [com.pedro.common.base.BaseSender.getQueueSnapshot].
 * Safe to pass across threads; all fields are immutable.
 *
 * @param capacity   Maximum number of frames the queue can hold.
 * @param items      Number of frames currently queued.
 * @param softThresholdPercent  Usage % at which congestion enters soft state (default 70).
 * @param hardThresholdPercent  Usage % at which congestion enters hard state (default 85).
 */
data class QueueSnapshot(
    val capacity: Int,
    val items: Int,
    val softThresholdPercent: Float = 70f,
    val hardThresholdPercent: Float = 85f,
) {
    /** Usage in [0.0, 1.0]. 0.0 when capacity is zero. */
    val usageRatio: Double
        get() = if (capacity > 0) items.toDouble() / capacity else 0.0

    /** Human-readable summary for log lines. */
    fun summary(): String = "$items/$capacity (${"%.0f".format(usageRatio * 100)}%)"
}
