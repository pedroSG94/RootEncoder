package com.pedro.encoder.utils.gl;

import android.opengl.GLES20;

/**
 * Created by pedro on 22/03/19.
 */

public class PreviewSizeCalculator {

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
}