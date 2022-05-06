/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.rtplibrary.view;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;
import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.gl.FilterAction;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.video.FpsLimiter;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.rtplibrary.util.Filter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 4/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OffScreenGlThread
    implements GlInterface, Runnable, SurfaceTexture.OnFrameAvailableListener {

  private final Context context;
  private Thread thread = null;
  private boolean frameAvailable = false;
  private boolean running = true;
  private boolean initialized = false;

  private final SurfaceManager surfaceManagerPhoto = new SurfaceManager();
  private final SurfaceManager surfaceManager = new SurfaceManager();
  private final SurfaceManager surfaceManagerEncoder = new SurfaceManager();

  private ManagerRender managerRender = null;

  private final Semaphore semaphore = new Semaphore(0);
  private final BlockingQueue<Filter> filterQueue = new LinkedBlockingQueue<>();
  private final Object sync = new Object();
  private int encoderWidth, encoderHeight;
  private boolean loadAA = false;
  private int streamRotation;
  private boolean muteVideo = false;
  protected boolean isPreviewHorizontalFlip = false;
  protected boolean isPreviewVerticalFlip = false;
  private boolean isStreamHorizontalFlip = false;
  private boolean isStreamVerticalFlip = false;

  private boolean AAEnabled = false;
  private final FpsLimiter fpsLimiter = new FpsLimiter();
  private TakePhotoCallback takePhotoCallback;
  private boolean forceRender = false;

  public OffScreenGlThread(Context context) {
    this.context = context;
  }

  @Override
  public void init() {
    if (!initialized) managerRender = new ManagerRender();
    managerRender.setCameraFlip(false, false);
    initialized = true;
  }

  @Override
  public void setForceRender(boolean forceRender) {
    this.forceRender = forceRender;
  }

  @Override
  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
  }

  @Override
  public Point getEncoderSize() {
    return new Point(encoderWidth, encoderHeight);
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
  public void setFps(int fps) {
    fpsLimiter.setFPS(fps);
  }

  @Override
  public SurfaceTexture getSurfaceTexture() {
    return managerRender.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return managerRender.getSurface();
  }

  @Override
  public void addMediaCodecSurface(Surface surface) {
    synchronized (sync) {
      if (surfaceManager.isReady()) {
        surfaceManagerPhoto.release();
        surfaceManagerEncoder.release();
        surfaceManagerEncoder.eglSetup(surface, surfaceManager);
        surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManagerEncoder);
      }
    }
  }

  @Override
  public void removeMediaCodecSurface() {
    synchronized (sync) {
      surfaceManagerPhoto.release();
      surfaceManagerEncoder.release();
      surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager);
    }
  }

  @Override
  public void takePhoto(TakePhotoCallback takePhotoCallback) {
    this.takePhotoCallback = takePhotoCallback;
  }

  @Override
  public void setFilter(int filterPosition, BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.SET_INDEX, filterPosition, baseFilterRender));
  }

  @Override
  public void addFilter(BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.ADD, 0, baseFilterRender));
  }

  @Override
  public void addFilter(int filterPosition, BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.ADD_INDEX, filterPosition, baseFilterRender));
  }

  @Override
  public void clearFilters() {
    filterQueue.add(new Filter(FilterAction.CLEAR, 0, null));
  }

  @Override
  public void removeFilter(int filterPosition) {
    filterQueue.add(new Filter(FilterAction.REMOVE_INDEX, filterPosition, null));
  }

  @Override
  public void removeFilter(BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.REMOVE, 0, baseFilterRender));
  }

  @Override
  public int filtersCount() {
    return managerRender.filtersCount();
  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.SET, 0, baseFilterRender));
  }

  @Override
  public void enableAA(boolean AAEnabled) {
    this.AAEnabled = AAEnabled;
    loadAA = true;
  }

  @Override
  public void setRotation(int rotation) {
    managerRender.setCameraRotation(rotation);
  }

  @Override
  public void setStreamRotation(int rotation) {
    streamRotation = rotation;
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
  public boolean isAAEnabled() {
    return managerRender != null && managerRender.isAAEnabled();
  }

  @Override
  public void start() {
    synchronized (sync) {
      thread = new Thread(this);
      running = true;
      thread.start();
      semaphore.acquireUninterruptibly();
    }
  }

  @Override
  public void stop() {
    synchronized (sync) {
      running = false;
      if (thread != null) {
        thread.interrupt();
        try {
          thread.join(100);
        } catch (InterruptedException e) {
          thread.interrupt();
        }
        thread = null;
      }
      surfaceManagerPhoto.release();
      surfaceManagerEncoder.release();
      surfaceManager.release();
    }
  }

  @Override
  public void run() {
    surfaceManager.release();
    surfaceManager.eglSetup();
    surfaceManager.makeCurrent();
    managerRender.initGl(context, encoderWidth, encoderHeight, encoderWidth, encoderHeight);
    managerRender.getSurfaceTexture().setOnFrameAvailableListener(this);
    surfaceManagerPhoto.release();
    surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager);
    semaphore.release();
    try {
      while (running) {
        if (frameAvailable || forceRender) {
          frameAvailable = false;
          surfaceManager.makeCurrent();
          managerRender.updateFrame();
          managerRender.drawOffScreen();
          managerRender.drawScreen(encoderWidth, encoderHeight, false, 0, 0, isPreviewVerticalFlip, isPreviewHorizontalFlip);
          surfaceManager.swapBuffer();

          if (!filterQueue.isEmpty()) {
            Filter filter = filterQueue.take();
            managerRender.setFilterAction(filter.getFilterAction(), filter.getPosition(), filter.getBaseFilterRender());
          } else if (loadAA) {
            managerRender.enableAA(AAEnabled);
            loadAA = false;
          }

          synchronized (sync) {
            if (surfaceManagerEncoder.isReady() && !fpsLimiter.limitFPS()) {
              int w = muteVideo ? 0 : encoderWidth;
              int h = muteVideo ? 0 : encoderHeight;
              surfaceManagerEncoder.makeCurrent();
              managerRender.drawScreen(w, h, false, 0,
                  streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
              surfaceManagerEncoder.swapBuffer();
            }
            if (takePhotoCallback != null && surfaceManagerPhoto.isReady()) {
              surfaceManagerPhoto.makeCurrent();
              managerRender.drawScreen(encoderWidth, encoderHeight, false, 0,
                  streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
              takePhotoCallback.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight));
              takePhotoCallback = null;
              surfaceManagerPhoto.swapBuffer();
            }
          }
        }
      }
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    } finally {
      managerRender.release();
      surfaceManagerPhoto.release();
      surfaceManagerEncoder.release();
      surfaceManager.release();
    }
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    synchronized (sync) {
      frameAvailable = true;
      sync.notifyAll();
    }
  }
}
