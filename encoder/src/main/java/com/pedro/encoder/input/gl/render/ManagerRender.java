package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.encoder.utils.gl.TranslateTo;

/**
 * Created by pedro on 27/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ManagerRender {

  private CameraRender cameraRender;
  private StreamObjectRender streamObjectRender;
  private BaseFilterRender baseFilterRender;
  private ScreenRender screenRender;

  private int width;
  private int height;
  private Context context;

  public ManagerRender() {
    cameraRender = new CameraRender();
    baseFilterRender = new NoFilterRender();
    streamObjectRender = new StreamObjectRender();
    screenRender = new ScreenRender();
  }

  public void initGl(int width, int height, boolean isCamera2Landscape, Context context) {
    this.width = width;
    this.height = height;
    this.context = context;
    cameraRender.isCamera2LandScape(isCamera2Landscape);
    cameraRender.initGl(width, height, context);
    streamObjectRender.setTexId(cameraRender.getTexId());
    streamObjectRender.initGl(width, height, context);
    baseFilterRender.setTexId(streamObjectRender.getTexId());
    baseFilterRender.initGl(width, height, context);
    baseFilterRender.initFBOLink();
    screenRender.setTexId(baseFilterRender.getTexId());
    screenRender.initGl(context);
  }

  public void drawOffScreen() {
    cameraRender.draw();
    streamObjectRender.draw();
    baseFilterRender.draw();
  }

  public void drawScreen(int width, int height, boolean keepAspectRatio) {
    screenRender.draw(width, height, keepAspectRatio);
  }

  public void release() {
    cameraRender.release();
    streamObjectRender.release();
    baseFilterRender.release();
    screenRender.release();
  }

  public void enableAA(boolean AAEnabled) {
    screenRender.setAAEnabled(AAEnabled);
  }

  public boolean isAAEnabled() {
    return screenRender.isAAEnabled();
  }

  public void updateFrame() {
    cameraRender.updateTexImage();
  }

  public SurfaceTexture getSurfaceTexture() {
    return cameraRender.getSurfaceTexture();
  }

  public Surface getSurface() {
    return cameraRender.getSurface();
  }

  public void setFilter(BaseFilterRender baseFilterRender) {
    this.baseFilterRender = baseFilterRender;
    this.baseFilterRender.initGl(width, height, context);
  }

  public void setImage(ImageStreamObject imageStreamObject) {
    streamObjectRender.setImage(imageStreamObject);
  }

  public void setText(TextStreamObject textStreamObject) {
    streamObjectRender.setText(textStreamObject);
  }

  public void setGif(GifStreamObject gifStreamObject) {
    streamObjectRender.setGif(gifStreamObject);
  }

  public void clear() {
    streamObjectRender.clear();
  }

  public void setAlpha(float alpha) {
    streamObjectRender.setAlpha(alpha);
  }

  public void setScale(float scaleX, float scaleY) {
    streamObjectRender.setScale(scaleX, scaleY);
  }

  public void setPosition(float x, float y) {
    streamObjectRender.setPosition(x, y);
  }

  public void setPosition(TranslateTo positionTo) {
    streamObjectRender.setPosition(positionTo);
  }

  public PointF getScale() {
    return streamObjectRender.getScale();
  }

  public PointF getPosition() {
    return streamObjectRender.getPosition();
  }

  public void setStreamSize(int encoderWidth, int encoderHeight) {
    streamObjectRender.setStreamSize(encoderWidth, encoderHeight);
    screenRender.setStreamSize(encoderWidth, encoderHeight);
  }

  public void faceChanged(boolean isFrontCamera) {
    cameraRender.faceChanged(isFrontCamera);
  }
}
