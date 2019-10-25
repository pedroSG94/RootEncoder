package com.pedro.encoder.input.gl.render.filters.object;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.utils.gl.ImageStreamObject;

/**
 * Created by pedro on 27/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ImageObjectFilterRender extends BaseObjectFilterRender {

  public ImageObjectFilterRender() {
    super();
    streamObject = new ImageStreamObject();
  }

  @Override
  protected void drawFilter() {
    super.drawFilter();
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[0]);
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, streamObjectTextureId[0] == -1 ? 0f : alpha);
  }

  public void setImage(Bitmap bitmap) {
    ((ImageStreamObject) streamObject).load(bitmap);
    shouldLoad = true;
  }
}
