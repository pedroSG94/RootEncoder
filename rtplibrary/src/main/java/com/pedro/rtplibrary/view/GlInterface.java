package com.pedro.rtplibrary.view;

import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;

public interface GlInterface {

  void init();

  void setEncoderSize(int width, int height);

  SurfaceTexture getSurfaceTexture();

  Surface getSurface();

  void addMediaCodecSurface(Surface surface);

  void removeMediaCodecSurface();

  void setFilter(BaseFilterRender baseFilterRender);

  void setGif(GifStreamObject gifStreamObject);

  void setImage(ImageStreamObject imageStreamObject);

  void setText(TextStreamObject textStreamObject);

  void clear();

  void setStreamObjectAlpha(float alpha);

  void setStreamObjectSize(float sizeX, float sizeY);

  void setStreamObjectPosition(float x, float y);

  void setStreamObjectPosition(TranslateTo translateTo);

  void enableAA(boolean AAEnabled);

  void setCameraFace(boolean frontCamera);

  boolean isAAEnabled();

  void setWaitTime(int waitTime);

  PointF getScale();

  PointF getPosition();

  void start(boolean isCamera2Landscape);

  void stop();
}
