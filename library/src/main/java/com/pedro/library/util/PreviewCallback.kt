package com.pedro.library.util

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener

class PreviewCallback(
    private val onCreated: (surface: Surface, width: Int, height: Int) -> Unit,
    private val onChanged: (width: Int, height: Int) -> Unit,
    private val onDestroyed: () -> Unit,
) {
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null

    fun setSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        this.surfaceView?.holder?.addCallback(surfaceViewCallback)
    }

    fun setTextureView(textureView: TextureView) {
        this.textureView = textureView
        this.textureView?.surfaceTextureListener = textureViewCallback
    }

    fun removeCallbacks() {
        surfaceView?.holder?.removeCallback(surfaceViewCallback)
        textureView?.surfaceTextureListener = null
        surfaceView = null
        textureView = null
    }

    private val surfaceViewCallback = object: SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            onCreated(holder.surface, holder.surfaceFrame.width(), holder.surfaceFrame.height())
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            onChanged(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            onDestroyed()
        }
    }

    private val textureViewCallback = object: SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            onCreated(Surface(texture), width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            onChanged(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            onDestroyed()
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }
}