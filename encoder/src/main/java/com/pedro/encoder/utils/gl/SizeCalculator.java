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

package com.pedro.encoder.utils.gl;

import com.pedro.encoder.utils.ViewPort;

import kotlin.Pair;

/**
 * Created by pedro on 22/03/19.
 */

public class SizeCalculator {

  public static ViewPort calculateViewPort(AspectRatioMode mode, int previewWidth,
      int previewHeight, int streamWidth, int streamHeight) {
    if (mode == AspectRatioMode.NONE) {
      return new ViewPort(0, 0, previewWidth, previewHeight);
    }
    float streamAspectRatio = (float) streamWidth / (float) streamHeight;
    float previewAspectRatio = (float) previewWidth / (float) previewHeight;
    int xo = 0;
    int yo = 0;
    int xf = previewWidth;
    int yf = previewHeight;
    if (mode == AspectRatioMode.Adjust) {
      if (streamAspectRatio > previewAspectRatio) {
        yf = streamHeight * previewWidth / streamWidth;
        yo = (yf - previewHeight) / -2;
      } else {
        xf = streamWidth * previewHeight / streamHeight;
        xo = (xf - previewWidth) / -2;
      }
    } else { //AspectRatioMode.Fill
      if (streamAspectRatio > previewAspectRatio) {
        xf = streamWidth * previewHeight / streamHeight;
        xo = (xf - previewWidth) / -2;
      } else {
        yf = streamHeight * previewWidth / streamWidth;
        yo = (yf - previewHeight) / -2;
      }
    }
    return new ViewPort(xo, yo, xf, yf);
  }

  public static ViewPort calculateViewPortEncoder(int streamWidth, int streamHeight, boolean isPortrait) {
    float factor = (float) streamWidth / (float) streamHeight;
    if (factor >= 1f) {
      if (isPortrait) {
        int width = (int) (streamHeight / factor);
        int oX = (streamWidth - width) / 2;
        return new ViewPort(oX, 0, width, streamHeight);
      } else {
        return new ViewPort(0, 0, streamWidth, streamHeight);
      }
    } else {
      if (isPortrait) {
        return new ViewPort(0, 0, streamWidth, streamHeight);
      } else {
        int height = (int) (streamWidth * factor);
        int oY = (streamHeight - height) / 2;
        return new ViewPort(0, oY, streamWidth, height);
      }
    }
  }

  public static Pair<Float, Float> calculateFlip(boolean flipStreamHorizontal, boolean flipStreamVertical) {
    return new Pair<>(flipStreamHorizontal ? -1f : 1f, flipStreamVertical ? -1f : 1f);
  }
}