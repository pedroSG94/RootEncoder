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

package com.pedro.encoder.utils.gl;

import android.graphics.Point;
import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Pair;

/**
 * Created by pedro on 22/03/19.
 */

public class SizeCalculator {

  public static void calculateViewPort(AspectRatioMode mode, int previewWidth,
      int previewHeight, int streamWidth, int streamHeight) {
    Pair<Point, Point> pair =
        getViewport(mode, previewWidth, previewHeight, streamWidth, streamHeight);
    GLES20.glViewport(pair.first.x, pair.first.y, pair.second.x, pair.second.y);
  }

  public static void calculateViewPortEncoder(int streamWidth, int streamHeight, boolean isPortrait) {
    Pair<Point, Point> pair;
    float factor = (float) streamWidth / (float) streamHeight;
    if (factor >= 1f) {
      if (isPortrait) {
        int width = (int) (streamHeight / factor);
        int oX = (streamWidth - width) / 2;
        pair = new Pair<>(new Point(oX, 0), new Point(width, streamHeight));
      } else {
        pair = new Pair<>(new Point(0, 0), new Point(streamWidth, streamHeight));
      }
    } else {
      if (isPortrait) {
        pair = new Pair<>(new Point(0, 0), new Point(streamWidth, streamHeight));
      } else {
        int height = (int) (streamWidth * factor);
        int oY = (streamHeight - height) / 2;
        pair = new Pair<>(new Point(0, oY), new Point(streamWidth, height));
      }
    }
    GLES20.glViewport(pair.first.x, pair.first.y, pair.second.x, pair.second.y);
  }

  public static Pair<Point, Point> getViewport(AspectRatioMode mode, int previewWidth,
      int previewHeight, int streamWidth, int streamHeight) {
    if (mode != AspectRatioMode.NONE) {
      float streamAspectRatio = (float) streamWidth / (float) streamHeight;
      float previewAspectRatio = (float) previewWidth / (float) previewHeight;
      int xo = 0;
      int yo = 0;
      int xf = previewWidth;
      int yf = previewHeight;
      if ((previewAspectRatio > 1f && streamAspectRatio > previewAspectRatio) ||
          (streamAspectRatio < 1f && previewAspectRatio < 1 && streamAspectRatio > previewAspectRatio) ||
          (streamAspectRatio > 1f && previewAspectRatio < 1f)) {
        if (mode == AspectRatioMode.Adjust) {
          yf = streamHeight * previewWidth / streamWidth;
          yo = (yf - previewHeight) / -2;
        } else { //fill
          xf = streamWidth * previewHeight / streamHeight;
          xo = (xf - previewWidth) / -2;
        }
      } else if ((streamAspectRatio > 1f && previewAspectRatio > 1f && streamAspectRatio < previewAspectRatio) ||
          (previewAspectRatio < 1f && streamAspectRatio < previewAspectRatio) ||
          (streamAspectRatio < 1f && previewAspectRatio > 1f)) {
        if (mode == AspectRatioMode.Adjust) {
          xf = streamWidth * previewHeight / streamHeight;
          xo = (xf - previewWidth) / -2;
        } else {
          yf = streamHeight * previewWidth / streamWidth;
          yo = (yf - previewHeight) / -2;
        }
      }
      return new Pair<>(new Point(xo, yo), new Point(xf, yf));
    } else {
      return new Pair<>(new Point(0, 0), new Point(previewWidth, previewHeight));
    }
  }

  public static void processMatrix(int rotation, boolean flipStreamHorizontal,
      boolean flipStreamVertical, float[] MVPMatrix) {
    PointF scale = new PointF(1f, 1f);

    float xFlip = flipStreamHorizontal ? -1f : 1f;
    float yFlip = flipStreamVertical ? -1f : 1f;
    scale = new PointF(scale.x * xFlip, scale.y * yFlip);

    updateMatrix(rotation, scale, MVPMatrix);
  }

  private static void updateMatrix(int rotation, PointF scale, float[] MVPMatrix) {
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.scaleM(MVPMatrix, 0, scale.x, scale.y, 1f);
    Matrix.rotateM(MVPMatrix, 0, rotation, 0f, 0f, -1f);
  }

  private static PointF getScale(int rotation, int width, int height, boolean isPortrait,
      boolean isPreview) {
    float scaleX = 1f;
    float scaleY = 1f;
    if (!isPreview) {
      if (isPortrait && rotation != 0 && rotation != 180) { //portrait
        final float adjustedWidth = width * (width / (float) height);
        scaleY = adjustedWidth / height;
      } else if (!isPortrait && rotation != 90 && rotation != 270) { //landscape
        final float adjustedWidth = height * (height / (float) width);
        scaleX = adjustedWidth / width;
      }
    }
    return new PointF(scaleX, scaleY);
  }
}