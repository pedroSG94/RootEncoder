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
 * Typed transport events emitted by [com.pedro.common.base.BaseSender].
 *
 * These supplement [ConnectChecker.onConnectionFailed] with machine-readable signal types,
 * allowing callers to differentiate between queue-pressure events (which do not require a
 * network reconnect) and actual network send failures (which do).
 *
 * Designed as a sealed class so all variants are exhaustive-checked by callers.
 */
sealed class TransportEvent {

    /**
     * The sender's internal frame queue has reached capacity and rejected a frame.
     *
     * This does not indicate a network failure. The connection is healthy; the sender
     * cannot consume frames fast enough. Callers should reduce frame rate or bitrate,
     * request a keyframe, or clear the queue — but NOT reconnect.
     *
     * @param droppedVideo Total dropped video frames of this type since sender start.
     * @param droppedAudio Total dropped audio frames of this type since sender start.
     * @param queueCapacity  Maximum number of frames the queue can hold.
     * @param queueSize  Number of frames currently in the queue.
     */
    data class QueueOverflow(
        val droppedVideo: Long,
        val droppedAudio: Long,
        val queueCapacity: Int,
        val queueSize: Int,
    ) : TransportEvent()

    /**
     * A network-level send error occurred inside the sender dispatch loop.
     *
     * This is equivalent to the existing [ConnectChecker.onConnectionFailed] call and
     * is emitted in addition to (not instead of) that callback for typed handling.
     *
     * @param message  Human-readable error description.
     * @param cause  Original exception, if available.
     */
    data class NetworkSendError(
        val message: String,
        val cause: Throwable? = null,
    ) : TransportEvent()
}
