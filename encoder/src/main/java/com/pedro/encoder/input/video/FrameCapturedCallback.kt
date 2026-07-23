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
package com.pedro.encoder.input.video

/*
This method is called when the camera device has started capturing the output image for the request,
at the beginning of image exposure, or when the camera device has started processing an input image
for a reprocess request.
For a regular capture request, this callback is invoked right as the capture of a frame begins,
so it is the most appropriate time for playing a shutter sound, or triggering UI indicators of capture.
The request that is being used for this capture is provided, along with the actual timestamp for
the start of exposure. For a reprocess request, this timestamp will be the input image's start of
exposure which matches the result timestamp field of the TotalCaptureResult that was used to create
 the reprocess request. This timestamp matches the timestamps that will be included in the result
 timestamp field, and in the buffers sent to each output Surface. These buffer timestamps are
 accessible through, for example, Image.getTimestamp() or android.graphics.SurfaceTexture.getTimestamp().
 The frame number included is equal to the frame number that will be included in CaptureResult.getFrameNumber.
*/

interface FrameCapturedCallback {
    fun onFrameCaptured(frameNumber: Long, timestamp: Long)
}