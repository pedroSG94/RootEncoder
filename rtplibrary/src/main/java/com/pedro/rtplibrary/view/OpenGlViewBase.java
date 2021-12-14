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
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.video.FpsLimiter;
import com.pedro.rtplibrary.util.Filter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Created by pedro on 10/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class OpenGlViewBase extends SurfaceView
    implements GlInterface, Runnable, SurfaceTexture.OnFrameAvailableListener,
    SurfaceHolder.Callback {

  public final static String TAG = "OpenGlViewBase";

  protected Thread thread = null;
  protected boolean frameAvailable = false;
  protected boolean running = false;
  protected boolean initialized = false;

  protected final SurfaceManager surfaceManagerPhoto = new SurfaceManager();
  protected final SurfaceManager surfaceManager = new SurfaceManager();
  protected final SurfaceManager surfaceManagerEncoder = new SurfaceManager();

  protected final FpsLimiter fpsLimiter = new FpsLimiter();
  protected final Semaphore semaphore = new Semaphore(0);
  protected final BlockingQueue<Filter> filterQueue = new LinkedBlockingQueue<>();
  protected final Object sync = new Object();
  protected int previewWidth, previewHeight;
  protected int encoderWidth, encoderHeight;
  protected TakePhotoCallback takePhotoCallback;
  protected int streamRotation;
  protected boolean muteVideo = false;
  protected boolean isPreviewHorizontalFlip = false;
  protected boolean isPreviewVerticalFlip = false;
  protected boolean isStreamHorizontalFlip = false;
  protected boolean isStreamVerticalFlip = false;
  protected boolean forceRender = false;

  public OpenGlViewBase(Context context) {
    super(context);
    getHolder().addCallback(this);
  }

  public OpenGlViewBase(Context context, AttributeSet attrs) {
    super(context, attrs);
    getHolder().addCallback(this);
  }

  @Override
  public void setForceRender(boolean forceRender) {
    this.forceRender = forceRender;
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
  public abstract void init();

  @Override
  public abstract SurfaceTexture getSurfaceTexture();

  @Override
  public abstract Surface getSurface();

  @Override
  public void setFps(int fps) {
    fpsLimiter.setFPS(fps);
  }

  @Override
  public void takePhoto(TakePhotoCallback takePhotoCallback) {
    this.takePhotoCallback = takePhotoCallback;
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
  public void setEncoderSize(int width, int height) {
    this.encoderWidth = width;
    this.encoderHeight = height;
  }

  @Override
  public Point getEncoderSize() {
    return new Point(encoderWidth, encoderHeight);
  }

  @Override
  public void start() {
    synchronized (sync) {
      Log.i(TAG, "Thread started.");
      thread = new Thread(this, "glThread");
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
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    synchronized (sync) {
      frameAvailable = true;
      sync.notifyAll();
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    stop();
  }
}
