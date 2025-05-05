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
package com.pedro.library.view

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import com.pedro.common.newSingleThreadExecutor
import com.pedro.common.secureSubmit
import com.pedro.encoder.input.gl.FilterAction
import com.pedro.encoder.input.gl.SurfaceManager
import com.pedro.encoder.input.gl.render.MainRender
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import com.pedro.encoder.input.video.FpsLimiter
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.AspectRatioMode.Companion.fromId
import com.pedro.encoder.utils.gl.GlUtil
import com.pedro.library.R
import com.pedro.library.util.Filter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by pedro on 10/03/18.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
open class OpenGlView : SurfaceView, GlInterface, OnFrameAvailableListener, SurfaceHolder.Callback {
    private val running = AtomicBoolean(false)
    private val mainRender = MainRender()
    private val surfaceManagerPhoto = SurfaceManager()
    private val surfaceManager = SurfaceManager()
    private val surfaceManagerEncoder = SurfaceManager()
    private val surfaceManagerEncoderRecord = SurfaceManager()
    private val filterQueue: BlockingQueue<Filter> = LinkedBlockingQueue()
    private val threadQueue = LinkedBlockingQueue<Runnable>()
    private var previewWidth = 0
    private var previewHeight = 0
    private var encoderWidth = 0
    private var encoderHeight = 0
    private var encoderRecordWidth = 0
    private var encoderRecordHeight = 0
    private var takePhotoCallback: TakePhotoCallback? = null
    private var streamRotation = 0
    private var muteVideo = false
    private var isPreviewHorizontalFlip = false
    private var isPreviewVerticalFlip = false
    private var isStreamHorizontalFlip = false
    private var isStreamVerticalFlip = false
    private var aspectRatioMode = AspectRatioMode.Adjust
    private var executor: ExecutorService? = null
    private val fpsLimiter = FpsLimiter()
    private val forceRenderer = ForceRenderer()
    private var renderErrorCallback: RenderErrorCallback? = null

    constructor(context: Context?) : super(context) {
        holder.addCallback(this)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.OpenGlView)
        try {
            aspectRatioMode = fromId(typedArray.getInt(R.styleable.OpenGlView_aspectRatioMode, AspectRatioMode.NONE.ordinal))
            val isFlipHorizontal = typedArray.getBoolean(R.styleable.OpenGlView_isFlipHorizontal, false)
            val isFlipVertical = typedArray.getBoolean(R.styleable.OpenGlView_isFlipVertical, false)
            mainRender.setCameraFlip(isFlipHorizontal, isFlipVertical)
        } finally {
            typedArray.recycle()
        }
        holder.addCallback(this)
    }

    override fun getSurfaceTexture(): SurfaceTexture {
        return mainRender.getSurfaceTexture()
    }

    override fun getSurface(): Surface {
        return mainRender.getSurface()
    }

    override fun setFilter(filterPosition: Int, baseFilterRender: BaseFilterRender) {
        filterQueue.add(Filter(FilterAction.SET_INDEX, filterPosition, baseFilterRender))
    }

    override fun addFilter(baseFilterRender: BaseFilterRender) {
        filterQueue.add(Filter(FilterAction.ADD, 0, baseFilterRender))
    }

    override fun addFilter(filterPosition: Int, baseFilterRender: BaseFilterRender) {
        filterQueue.add(Filter(FilterAction.ADD_INDEX, filterPosition, baseFilterRender))
    }

    override fun clearFilters() {
        filterQueue.add(Filter(FilterAction.CLEAR, 0, NoFilterRender()))
    }

    override fun removeFilter(filterPosition: Int) {
        filterQueue.add(Filter(FilterAction.REMOVE_INDEX, filterPosition, NoFilterRender()))
    }

    override fun removeFilter(baseFilterRender: BaseFilterRender) {
        filterQueue.add(Filter(FilterAction.REMOVE, 0, baseFilterRender))
    }

    override fun filtersCount(): Int {
        return mainRender.filtersCount()
    }

    override fun setFilter(baseFilterRender: BaseFilterRender) {
        filterQueue.add(Filter(FilterAction.SET, 0, baseFilterRender))
    }

    override fun setRotation(rotation: Int) {
        mainRender.setCameraRotation(rotation)
    }

    override fun forceFpsLimit(fps: Int) {
        fpsLimiter.setFPS(fps)
    }

    fun setAspectRatioMode(aspectRatioMode: AspectRatioMode) {
        this.aspectRatioMode = aspectRatioMode
    }

    fun setCameraFlip(isFlipHorizontal: Boolean, isFlipVertical: Boolean) {
        mainRender.setCameraFlip(isFlipHorizontal, isFlipVertical)
    }

    override fun setStreamRotation(streamRotation: Int) {
        this.streamRotation = streamRotation
    }

    override fun setIsStreamHorizontalFlip(flip: Boolean) {
        isStreamHorizontalFlip = flip
    }

    override fun setIsStreamVerticalFlip(flip: Boolean) {
        isStreamVerticalFlip = flip
    }

    override fun setIsPreviewHorizontalFlip(flip: Boolean) {
        isPreviewHorizontalFlip = flip
    }

    override fun setIsPreviewVerticalFlip(flip: Boolean) {
        isPreviewVerticalFlip = flip
    }

    override fun muteVideo() {
        muteVideo = true
    }

    override fun unMuteVideo() {
        muteVideo = false
    }

    override fun isVideoMuted() = muteVideo

    override fun setForceRender(enabled: Boolean, fps: Int) {
        forceRenderer.setEnabled(enabled, fps)
    }

    override fun setForceRender(enabled: Boolean) {
        setForceRender(enabled, 5)
    }

    override fun isRunning() = running.get()

    override fun setRenderErrorCallback(callback: RenderErrorCallback) {
        renderErrorCallback = callback
    }

    override fun setEncoderSize(width: Int, height: Int) {
        encoderWidth = width
        encoderHeight = height
    }

    override fun setEncoderRecordSize(width: Int, height: Int) {
        encoderRecordWidth = width
        encoderRecordHeight = height
    }

    override fun getEncoderSize() = Point(encoderWidth, encoderHeight)

    override fun takePhoto(takePhotoCallback: TakePhotoCallback) {
        this.takePhotoCallback = takePhotoCallback
    }

    private fun draw(forced: Boolean) {
        if (!isRunning) return
        val limitFps = fpsLimiter.limitFPS()
        if (!forced) forceRenderer.frameAvailable()

        if (surfaceManager.isReady && mainRender.isReady()) {
            surfaceManager.makeCurrent()
            mainRender.updateFrame()
            mainRender.drawSource()
            if (!limitFps) {
                mainRender.drawFilters(true)
                mainRender.drawScreen(
                    previewWidth, previewHeight, aspectRatioMode, 0,
                    isPreviewVerticalFlip, isPreviewHorizontalFlip, null
                )
                surfaceManager.swapBuffer()
            }
        }
        if (!filterQueue.isEmpty() && mainRender.isReady()) {
            try {
                val filter = filterQueue.take()
                mainRender.setFilterAction(filter.filterAction, filter.position, filter.baseFilterRender)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
        if (surfaceManagerEncoder.isReady || surfaceManagerEncoderRecord.isReady || surfaceManagerPhoto.isReady) {
            mainRender.drawFilters(false)
        }
        if (surfaceManagerEncoder.isReady && mainRender.isReady() && !limitFps) {
            val w = if (muteVideo) 0 else encoderWidth
            val h = if (muteVideo) 0 else encoderHeight
            surfaceManagerEncoder.makeCurrent()
            mainRender.drawScreen(
                w, h, aspectRatioMode,
                streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip, null
            )
            surfaceManagerEncoder.swapBuffer()
        }
        // render VideoEncoder (record if the resolution is different than stream)
        if (surfaceManagerEncoderRecord.isReady && mainRender.isReady() && !limitFps) {
            val w = if (muteVideo) 0 else encoderRecordWidth
            val h = if (muteVideo) 0 else encoderRecordHeight
            surfaceManagerEncoderRecord.makeCurrent()
            mainRender.drawScreen(w, h, aspectRatioMode, streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip, null)
            surfaceManagerEncoderRecord.swapBuffer()
        }
        if (takePhotoCallback != null && surfaceManagerPhoto.isReady && mainRender.isReady()) {
            surfaceManagerPhoto.makeCurrent()
            mainRender.drawScreen(encoderWidth, encoderHeight, aspectRatioMode, streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip, null)
            takePhotoCallback?.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight))
            takePhotoCallback = null
            surfaceManagerPhoto.swapBuffer()
        }
    }

    override fun addMediaCodecSurface(surface: Surface) {
        if (surfaceManager.isReady) {
            surfaceManagerEncoder.release()
            surfaceManagerEncoder.eglSetup(surface, surfaceManager)
        }
    }

    override fun removeMediaCodecSurface() {
        threadQueue.clear()
        surfaceManagerEncoder.release()
    }

    override fun addMediaCodecRecordSurface(surface: Surface) {
        if (surfaceManager.isReady) {
            surfaceManagerEncoderRecord.release()
            surfaceManagerEncoderRecord.eglSetup(surface, surfaceManager)
        }
    }

    override fun removeMediaCodecRecordSurface() {
        threadQueue.clear()
        surfaceManagerEncoderRecord.release()
    }

    override fun start() {
        threadQueue.clear()
        executor?.shutdownNow()
        executor = null
        executor = newSingleThreadExecutor(threadQueue)
        executor?.secureSubmit {
            surfaceManager.release()
            surfaceManager.eglSetup(holder.surface)
            surfaceManagerPhoto.release()
            surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager)
            surfaceManager.makeCurrent()
            mainRender.initGl(context, encoderWidth, encoderHeight, encoderWidth, encoderHeight)
            running.set(true)
            mainRender.getSurfaceTexture().setOnFrameAvailableListener(this)
            forceRenderer.start {
                executor?.execute {
                    try {
                        draw(true)
                    } catch (e: RuntimeException) {
                        renderErrorCallback?.onRenderError(e) ?: throw e
                    }
                }
            }
        }
    }

    override fun stop() {
        running.set(false)
        threadQueue.clear()
        executor?.shutdownNow()
        executor = null
        forceRenderer.stop()
        surfaceManagerPhoto.release()
        surfaceManagerEncoder.release()
        surfaceManagerEncoderRecord.release()
        surfaceManager.release()
        mainRender.release()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (!isRunning) return
        executor?.execute {
            try {
                draw(false)
            } catch (e: RuntimeException) {
                renderErrorCallback?.onRenderError(e) ?: throw e
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this.previewWidth = width
        this.previewHeight = height
        mainRender.setPreviewSize(previewWidth, previewHeight)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stop()
    }
}
