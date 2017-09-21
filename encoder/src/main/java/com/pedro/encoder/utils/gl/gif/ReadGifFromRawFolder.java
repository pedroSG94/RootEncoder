package com.pedro.encoder.utils.gl.gif;

import android.content.Context;
import android.content.res.Resources;
import java.io.InputStream;

/**
 * Created by pedro on 21/09/17.
 */

public class ReadGifFromRawFolder {
  public static InputStream getStringFromRaw(Context context, int id) {
    Resources r = context.getResources();
    return r.openRawResource(id);
  }
}
