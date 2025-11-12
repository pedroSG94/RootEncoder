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

package com.pedro.library.view.preview

import com.pedro.encoder.utils.ViewPort
import com.pedro.encoder.utils.gl.AspectRatioMode

/**
   * User-facing configuration for a multi-preview surface.
   * All configuration fields are mutable to support dynamic updates.
   *
   * @param width the width of the preview. 0 to use preview or encoder resolution
   * @param height the height of the preview. 0 to use preview or encoder resolution
   * @param horizontalFlip true to flip horizontally
   * @param verticalFlip true to flip vertically
   * @param aspectRatioMode aspect ratio mode for this surface
   * @param isPortrait true for portrait orientation, false for landscape
   * @param viewPort viewport for this surface. null for full screen
   */
data class MultiPreviewConfig(
  var width: Int = 0,
  var height: Int = 0,
  var horizontalFlip: Boolean = false,
  var verticalFlip: Boolean = false,
  var aspectRatioMode: AspectRatioMode = AspectRatioMode.Adjust,
  var isPortrait: Boolean = false,
  var viewPort: ViewPort? = null
)