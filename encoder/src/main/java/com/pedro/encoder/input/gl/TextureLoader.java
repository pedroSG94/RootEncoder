package com.pedro.encoder.input.gl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.utils.gl.GlUtil;

/**
 * Created by pedro on 9/10/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class TextureLoader {

  public int[] load(Bitmap[] bitmaps) {
    int[] textureId = new int[bitmaps.length];
    GlUtil.createTextures(bitmaps.length, textureId, 0);
    for (int i = 0; i < bitmaps.length; i++) {
      if (bitmaps[i] != null) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[i]);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmaps[i], 0);
        bitmaps[i].recycle();
        bitmaps[i] = null;
      }
    }
    return textureId;
  }
}
