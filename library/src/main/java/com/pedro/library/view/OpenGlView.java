/*
 * Copyright (C) 2024 pedroSG94.
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

package com.pedro.library.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.pedro.common.ExtensionsKt;
import com.pedro.encoder.input.gl.FilterAction;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.MainRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import com.pedro.encoder.input.video.FpsLimiter;
import com.pedro.encoder.utils.gl.AspectRatioMode;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.library.R;
import com.pedro.library.util.Filter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by pedro on 10/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlView extends SurfaceView
    implements GlInterface, SurfaceTexture.OnFrameAvailableListener, SurfaceHolder.Callback {

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final MainRender mainRender = new MainRender();
  private final SurfaceManager surfaceManagerPhoto = new SurfaceManager();
  private final SurfaceManager surfaceManager = new SurfaceManager();
  private final SurfaceManager surfaceManagerEncoder = new SurfaceManager();
  private final SurfaceManager surfaceManagerEncoderRecord = new SurfaceManager();
  private final BlockingQueue<Filter> filterQueue = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<Runnable> threadQueue = new LinkedBlockingQueue<>();
  private int previewWidth, previewHeight;
  private int encoderWidth, encoderHeight;
  private int encoderRecordWidth, encoderRecordHeight;
  private TakePhotoCallback takePhotoCallback;
  private int streamRotation;
  private boolean muteVideo = false;
  private boolean isPreviewHorizontalFlip = false;
  private boolean isPreviewVerticalFlip = false;
  private boolean isStreamHorizontalFlip = false;
  private boolean isStreamVerticalFlip = false;
  private AspectRatioMode aspectRatioMode = AspectRatioMode.Adjust;
  private ExecutorService executor = null;
  private final FpsLimiter fpsLimiter = new FpsLimiter();
  private final ForceRenderer forceRenderer = new ForceRenderer();

  public OpenGlView(Context context) {
    super(context);
    getHolder().addCallback(this);
  }

  public OpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.OpenGlView);
    try {
      aspectRatioMode = AspectRatioMode.Companion.fromId(typedArray.getInt(R.styleable.OpenGlView_aspectRatioMode, AspectRatioMode.NONE.ordinal()));
      boolean isFlipHorizontal = typedArray.getBoolean(R.styleable.OpenGlView_isFlipHorizontal, false);
      boolean isFlipVertical = typedArray.getBoolean(R.styleable.OpenGlView_isFlipVertical, false);
      mainRender.setCameraFlip(isFlipHorizontal, isFlipVertical);
    } finally {
      typedArray.recycle();
    }
    getHolder().addCallback(this);
  }

  @Override
  public SurfaceTexture getSurfaceTexture() {
    return mainRender.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return mainRender.getSurface();
  }

  @Override
  public void setFilter(int filterPosition, @NonNull BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.SET_INDEX, filterPosition, baseFilterRender));
  }

  @Override
  public void addFilter(@NonNull BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.ADD, 0, baseFilterRender));
  }

  @Override
  public void addFilter(int filterPosition, @NonNull BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.ADD_INDEX, filterPosition, baseFilterRender));
  }

  @Override
  public void clearFilters() {
    filterQueue.add(new Filter(FilterAction.CLEAR, 0, new NoFilterRender()));
  }

  @Override
  public void removeFilter(int filterPosition) {
    filterQueue.add(new Filter(FilterAction.REMOVE_INDEX, filterPosition, new NoFilterRender()));
  }

  @Override
  public void removeFilter(@NonNull BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.REMOVE, 0, baseFilterRender));
  }

  @Override
  public int filtersCount() {
    return mainRender.filtersCount();
  }

  @Override
  public void setFilter(@NonNull BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.SET, 0, baseFilterRender));
  }

  @Override
  public void setRotation(int rotation) {
    mainRender.setCameraRotation(rotation);
  }

  @Override
  public void forceFpsLimit(int fps) {
    fpsLimiter.setFPS(fps);
  }

  public void setAspectRatioMode(AspectRatioMode aspectRatioMode) {
    this.aspectRatioMode = aspectRatioMode;
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    mainRender.setCameraFlip(isFlipHorizontal, isFlipVertical);
  }

  @Override
  public void setStreamRotation(int streamRotation) {
    this.streamRotation = streamRotation;
  }

  @Override
  public void setIsStreamHorizontalFlip(boolean flip) {
    isStreamHorizontalFlip = flip;
  }

  @Override
  public void setIsStreamVerticalFlip(boolean flip) {
    isStreamVerticalFlip = flip;
  }

  @Override
  public void setIsPreviewHorizontalFlip(boolean flip) {
    isPreviewHorizontalFlip = flip;
  }

  @Override
  public void setIsPreviewVerticalFlip(boolean flip) {
    isPreviewVerticalFlip = flip;
  }

  @Override
  public void muteVideo() {
    muteVideo = true;
  }

  @Override
  public void unMuteVideo() {
    muteVideo = false;
  }

  @Override
  public boolean isVideoMuted() {
    return muteVideo;
  }

  @Override
  public void setForceRender(boolean enabled, int fps) {
    forceRenderer.setEnabled(enabled, fps);
  }

  @Override
  public void setForceRender(boolean enabled) {
    setForceRender(enabled, 5);
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
  }

  @Override
  public void setEncoderRecordSize(int width, int height) {
    this.encoderRecordWidth = width;
    this.encoderRecordHeight = height;
  }

  @Override
  public Point getEncoderSize() {
    return new Point(encoderWidth, encoderHeight);
  }

  @Override
  public void takePhoto(TakePhotoCallback takePhotoCallback) {
    this.takePhotoCallback = takePhotoCallback;
  }

  private void draw(boolean forced) {
    if (!isRunning()) return;
    boolean limitFps = fpsLimiter.limitFPS();
    if (!forced) forceRenderer.frameAvailable();

    if (surfaceManager.isReady() && mainRender.isReady()) {
      surfaceManager.makeCurrent();
      mainRender.updateFrame();
      mainRender.drawOffScreen();
      if (!limitFps) {
        mainRender.drawScreen(previewWidth, previewHeight, aspectRatioMode, 0,
            isPreviewVerticalFlip, isPreviewHorizontalFlip);
        surfaceManager.swapBuffer();
      }
    }

    if (!filterQueue.isEmpty() && mainRender.isReady()) {
      try {
        Filter filter = filterQueue.take();
        mainRender.setFilterAction(filter.filterAction, filter.position, filter.baseFilterRender);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    if (surfaceManagerEncoder.isReady() && mainRender.isReady() && !limitFps) {
      int w = muteVideo ? 0 : encoderWidth;
      int h = muteVideo ? 0 : encoderHeight;
      surfaceManagerEncoder.makeCurrent();
      mainRender.drawScreen(w, h, aspectRatioMode,
          streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
      surfaceManagerEncoder.swapBuffer();
    }
    // render VideoEncoder (record if the resolution is different than stream)
    if (surfaceManagerEncoderRecord.isReady() && mainRender.isReady() && !limitFps) {
      int w = muteVideo ? 0 : encoderRecordWidth;
      int h = muteVideo ? 0 : encoderRecordHeight;
      surfaceManagerEncoderRecord.makeCurrent();
      mainRender.drawScreen(w, h, aspectRatioMode,
          streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
      surfaceManagerEncoderRecord.swapBuffer();
    }
    if (takePhotoCallback != null && surfaceManagerPhoto.isReady() && mainRender.isReady()) {
      surfaceManagerPhoto.makeCurrent();
      mainRender.drawScreen(encoderWidth, encoderHeight, aspectRatioMode,
          streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
      takePhotoCallback.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight));
      takePhotoCallback = null;
      surfaceManagerPhoto.swapBuffer();
    }
  }

  @Override
  public void addMediaCodecSurface(Surface surface) {
    ExecutorService executor = this.executor;
    if (executor == null) return;
    ExtensionsKt.secureSubmit(executor, () -> {
      if (surfaceManager.isReady()) {
        surfaceManagerEncoder.release();
        surfaceManagerEncoder.eglSetup(surface, surfaceManager);
      }
      return null;
    });
  }

  @Override
  public void removeMediaCodecSurface() {
    threadQueue.clear();
    ExecutorService executor = this.executor;
    if (executor == null) return;
    ExtensionsKt.secureSubmit(executor, () -> {
      surfaceManagerEncoder.release();
      return null;
    });
  }

  @Override
  public void addMediaCodecRecordSurface(Surface surface) {
    ExecutorService executor = this.executor;
    if (executor == null) return;
    ExtensionsKt.secureSubmit(executor, () -> {
      if (surfaceManager.isReady()) {
        surfaceManagerEncoderRecord.release();
        surfaceManagerEncoderRecord.eglSetup(surface, surfaceManager);
      }
      return null;
    });
  }

  @Override
  public void removeMediaCodecRecordSurface() {
    threadQueue.clear();
    ExecutorService executor = this.executor;
    if (executor == null) return;
    ExtensionsKt.secureSubmit(executor, () -> {
      surfaceManagerEncoderRecord.release();
      return null;
    });
  }

  @Override
  public void start() {
    threadQueue.clear();
    executor = ExtensionsKt.newSingleThreadExecutor(threadQueue);
    ExecutorService executor = this.executor;
    ExtensionsKt.secureSubmit(executor, () -> {
      surfaceManager.release();
      surfaceManager.eglSetup(getHolder().getSurface());
      surfaceManager.makeCurrent();
      mainRender.initGl(getContext(), encoderWidth, encoderHeight, encoderWidth, encoderHeight);
      surfaceManagerPhoto.release();
      surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager);
      running.set(true);
      mainRender.getSurfaceTexture().setOnFrameAvailableListener(this);
      forceRenderer.start(() -> {
        ExecutorService ex = this.executor;
        if (ex == null) return null;
        ex.execute(() -> draw(true));
        return null;
      });
      return null;
    });
  }

  @Override
  public void stop() {
    running.set(false);
    threadQueue.clear();
    ExecutorService executor = this.executor;
    if (executor == null) return;
    ExtensionsKt.secureSubmit(executor, () -> {
      forceRenderer.stop();
      surfaceManagerPhoto.release();
      surfaceManagerEncoder.release();
      surfaceManagerEncoderRecord.release();
      surfaceManager.release();
      mainRender.release();
      return null;
    });
    executor.shutdownNow();
    this.executor = null;
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    if (!isRunning()) return;
    ExecutorService ex = this.executor;
    if (ex == null) return;
    ex.execute(() -> draw(false));
  }

  @Override
  public void surfaceCreated(@NonNull SurfaceHolder holder) {
  }

  @Override
  public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    this.previewWidth = width;
    this.previewHeight = height;
    mainRender.setPreviewSize(previewWidth, previewHeight);
  }

  @Override
  public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
    stop();
  }
}
