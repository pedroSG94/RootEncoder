package com.pedro.encoder.input.gl.render.filters.object;

import android.opengl.GLES20;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.utils.gl.GifStreamObject;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by pedro on 27/07/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GifObjectFilterRender extends BaseObjectFilterRender {

  public GifObjectFilterRender() {
    super();
    streamObject = new GifStreamObject();
  }

  @Override
  protected void drawFilter() {
    super.drawFilter();
    int position = ((GifStreamObject) streamObject).updateFrame(streamObjectTextureId.length);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[position]);
    //Set alpha. 0f if no image loaded.
    GLES20.glUniform1f(uAlphaHandle, streamObjectTextureId[position] == -1 ? 0f : alpha);
  }

  public void setGif(InputStream inputStream) throws IOException {
    ((GifStreamObject) streamObject).load(inputStream);
    shouldLoad = true;
  }
}
