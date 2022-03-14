package com.pedro.rtplibrary.view

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.SurfaceManager
import com.pedro.encoder.input.gl.render.ManagerRender
import com.pedro.encoder.input.video.FpsLimiter
import java.util.concurrent.Semaphore

/**
 * Created by pedro on 14/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class GlCameraInterface(private val context: Context) : Runnable, OnFrameAvailableListener {

  private var thread: Thread? = null
  private var frameAvailable = false
  var running = false
  private var initialized = false
  private val surfaceManager = SurfaceManager()
  private val surfaceManagerEncoder = SurfaceManager()
  private val surfaceManagerPreview = SurfaceManager()
  private var managerRender: ManagerRender? = null
  private val semaphore = Semaphore(0)
  private val sync = Object()
  private var encoderWidth = 0
  private var encoderHeight = 0
  private val fpsLimiter = FpsLimiter()

  fun init() {
    if (!initialized) managerRender = ManagerRender()
    managerRender?.setCameraFlip(false, false)
    initialized = true
  }

  fun setEncoderSize(width: Int, height: Int) {
    encoderWidth = width
    encoderHeight = height
  }

  fun getEncoderSize(): Point {
    return Point(encoderWidth, encoderHeight)
  }

  fun setFps(fps: Int) {
    fpsLimiter.setFPS(fps)
  }

  fun getSurfaceTexture(): SurfaceTexture {
    return managerRender!!.surfaceTexture
  }

  fun getSurface(): Surface {
    return managerRender!!.surface
  }

  fun addMediaCodecSurface(surface: Surface) {
    synchronized(sync) {
      if (surfaceManager.isReady) {
        surfaceManagerEncoder.release()
        surfaceManagerEncoder.eglSetup(surface, surfaceManager)
      }
    }
  }

  fun removeMediaCodecSurface() {
    synchronized(sync) {
      surfaceManagerEncoder.release()
    }
  }

  fun start() {
    synchronized(sync) {
      thread = Thread(this)
      running = true
      thread?.start()
      semaphore.acquireUninterruptibly()
    }
  }

  fun stop() {
    synchronized(sync) {
      running = false
      thread?.interrupt()
      try {
        thread?.join(100)
      } catch (e: InterruptedException) {
        thread?.interrupt()
      }
      thread = null
      surfaceManagerEncoder.release()
      surfaceManager.release()
    }
  }

  override fun run() {
    surfaceManager.release()
    surfaceManager.eglSetup()
    surfaceManager.makeCurrent()
    managerRender?.initGl(context, encoderWidth, encoderHeight, encoderWidth, encoderHeight)
    managerRender?.surfaceTexture?.setOnFrameAvailableListener(this)
    semaphore.release()
    try {
      while (running) {
        if (frameAvailable) {
          frameAvailable = false
          surfaceManager.makeCurrent()
          managerRender?.updateFrame()
          managerRender?.drawOffScreen()
          managerRender?.drawScreen(encoderWidth, encoderHeight, false, 0, 0,
            true, false, false)
          surfaceManager.swapBuffer()

          synchronized(sync) {
            if (surfaceManagerEncoder.isReady && !fpsLimiter.limitFPS()) {
              val w =  encoderWidth
              val h =  encoderHeight
              surfaceManagerEncoder.makeCurrent()
              managerRender?.drawScreen(w, h, false, 0, 0,
                false, false, false)
              surfaceManagerEncoder.swapBuffer()
            }
            if (surfaceManagerPreview.isReady && !fpsLimiter.limitFPS()) {
              val w =  encoderWidth
              val h =  encoderHeight
              surfaceManagerPreview.makeCurrent()
              managerRender?.drawScreen(w, h, false, 0, 0,
                false, false, false)
              surfaceManagerPreview.swapBuffer()
            }
          }
        }
      }
    } catch (ignore: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally {
      managerRender?.release()
      surfaceManagerEncoder.release()
      surfaceManager.release()
    }
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
    synchronized(sync) {
      frameAvailable = true
      sync.notifyAll()
    }
  }

  fun attachPreview(surfaceView: SurfaceView) {
    synchronized(sync) {
      if (surfaceManager.isReady) {
        surfaceManagerPreview.release()
        surfaceManagerPreview.eglSetup(surfaceView.holder.surface, surfaceManager)
      }
    }
  }

  fun deAttachPreview() {
    synchronized(sync) {
      surfaceManagerPreview.release()
    }
  }
}
