package com.pedro.encoder.utils.gl;

import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.Matrix;

/**
 * Created by pedro on 22/03/19.
 */

public class SizeCalculator {

  public static void calculateViewPort(boolean keepAspectRatio, int mode, int previewWidth,
      int previewHeight, int streamWidth, int streamHeight) {
    if (keepAspectRatio) {
      if (previewWidth > previewHeight) { //landscape
        if (mode == 0) { //adjust
          int realWidth = previewHeight * streamWidth / streamHeight;
          GLES20.glViewport((previewWidth - realWidth) / 2, 0, realWidth, previewHeight);
        } else { //fill
          int realHeight = previewWidth * streamHeight / streamWidth;
          GLES20.glViewport(0, -((realHeight - previewWidth) / 2), previewWidth, realHeight);
        }
      } else { //portrait
        if (mode == 0) { //adjust
          int realHeight = previewWidth * streamHeight / streamWidth;
          GLES20.glViewport(0, (previewHeight - realHeight) / 2, previewWidth, realHeight);
        } else { //fill
          int realWidth = previewHeight * streamWidth / streamHeight;
          GLES20.glViewport(-((realWidth - previewWidth) / 2), 0, realWidth, previewHeight);
        }
      }
    } else {
      GLES20.glViewport(0, 0, previewWidth, previewHeight);
    }
  }

  public static void updateMatrix(int rotation, int width, int height, boolean isPreview,
      boolean isPortrait, float[] MVPMatrix) {
    Matrix.setIdentityM(MVPMatrix, 0);
    PointF scale = getScale(rotation, width, height, isPortrait, isPreview);
    Matrix.scaleM(MVPMatrix, 0, scale.x, scale.y, 1f);
    if (!isPreview && !isPortrait) rotation += 90;
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