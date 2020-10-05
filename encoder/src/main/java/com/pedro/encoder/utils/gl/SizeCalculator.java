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

  public static void calculateViewPort(boolean keepAspectRatio, int mode, int previewWidth,
      int previewHeight, int streamWidth, int streamHeight) {
    Pair<Point, Point> pair =
        getViewport(keepAspectRatio, mode, previewWidth, previewHeight, streamWidth, streamHeight);
    GLES20.glViewport(pair.first.x, pair.first.y, pair.second.x, pair.second.y);
  }

  public static Pair<Point, Point> getViewport(boolean keepAspectRatio, int mode, int previewWidth,
      int previewHeight, int streamWidth, int streamHeight) {
    if (keepAspectRatio) {
      float streamAspectRatio = (float) streamWidth / (float) streamHeight;
      float previewAspectRatio = (float) previewWidth / (float) previewHeight;
      int xo = 0;
      int yo = 0;
      int xf = previewWidth;
      int yf = previewHeight;
      if ((streamAspectRatio > 1f
          && previewAspectRatio > 1f
          && streamAspectRatio > previewAspectRatio) || (streamAspectRatio < 1f
          && previewAspectRatio < 1
          && streamAspectRatio > previewAspectRatio) || (streamAspectRatio > 1f
          && previewAspectRatio < 1f)) {
        if (mode == 0 || mode == 2) {
          yf = streamHeight * previewWidth / streamWidth;
          yo = (yf - previewHeight) / -2;
        } else {
          xf = streamWidth * previewHeight / streamHeight;
          xo = (xf - previewWidth) / -2;
        }
      } else if ((streamAspectRatio > 1f
          && previewAspectRatio > 1f
          && streamAspectRatio < previewAspectRatio) || (streamAspectRatio < 1f
          && previewAspectRatio < 1f
          && streamAspectRatio < previewAspectRatio) || (streamAspectRatio < 1f
          && previewAspectRatio > 1f)) {
        if (mode == 0 || mode == 2) {
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

  public static void processMatrix(int rotation, int width, int height, boolean isPreview,
      boolean isPortrait, boolean flipStreamHorizontal, boolean flipStreamVertical, int mode,
      float[] MVPMatrix) {
    PointF scale;
    if (mode == 2 || mode == 3) { // stream rotation is enabled
      scale = getScale(rotation, width, height, isPortrait, isPreview);
      if (!isPreview && !isPortrait) rotation += 90;
    } else {
      scale = new PointF(1f, 1f);
    }
    if (!isPreview) {
      float xFlip = flipStreamHorizontal ? -1f : 1f;
      float yFlip = flipStreamVertical ? -1f : 1f;
      scale = new PointF(scale.x * xFlip, scale.y * yFlip);
    }
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