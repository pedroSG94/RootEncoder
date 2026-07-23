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

package com.pedro.encoder.input.gl.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.FilterAction
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.ViewPort
import com.pedro.encoder.utils.gl.AspectRatioMode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by pedro on 20/3/22.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class MainRender {
  private val cameraRender = CameraRender()
  private val screenRender = ScreenRender()
  private var width = 0
  private var height = 0
  private var previewWidth = 0
  private var previewHeight = 0
  private var context: Context? = null
  private var filterRenders = mutableListOf<BaseFilterRender>()
  private val running = AtomicBoolean(false)

  fun initGl(context: Context, encoderWidth: Int, encoderHeight: Int, previewWidth: Int, previewHeight: Int) {
    this.context = context
    width = encoderWidth
    height = encoderHeight
    this.previewWidth = previewWidth
    this.previewHeight = previewHeight
    cameraRender.initGl(width, height, context, previewWidth, previewHeight)
    screenRender.setStreamSize(encoderWidth, encoderHeight)
    screenRender.setTexId(cameraRender.texId)
    screenRender.initGl(context)
    running.set(true)
  }

  fun isReady(): Boolean = running.get()

  fun drawSource() {
    cameraRender.draw()
  }

  fun drawFilters(isPreview: Boolean) {
    val validFilters = filterRenders.filter {
      if (isPreview) it.renderMode != RenderMode.OUTPUT else it.renderMode != RenderMode.PREVIEW
    }
    reOrderFilters(validFilters)
    validFilters.forEach { it.draw() }
  }

  fun drawScreen(
    width: Int, height: Int, mode: AspectRatioMode, rotation: Int,
    flipStreamVertical: Boolean, flipStreamHorizontal: Boolean, viewPort: ViewPort?
  ) {
    screenRender.draw(width, height, mode, rotation, flipStreamVertical,
      flipStreamHorizontal, viewPort)
  }

  fun drawScreenEncoder(
    width: Int, height: Int, isPortrait: Boolean, rotation: Int,
    flipStreamVertical: Boolean, flipStreamHorizontal: Boolean, viewPort: ViewPort?
  ) {
    screenRender.drawEncoder(width, height, isPortrait, rotation, flipStreamVertical,
      flipStreamHorizontal, viewPort)
  }

  fun drawScreenPreview(
    width: Int, height: Int, isPortrait: Boolean,
    mode: AspectRatioMode, rotation: Int, flipStreamVertical: Boolean, flipStreamHorizontal: Boolean,
    viewPort: ViewPort?
  ) {
    screenRender.drawPreview(width, height, isPortrait, mode, rotation, flipStreamVertical, flipStreamHorizontal, viewPort)
  }

  fun release() {
    running.set(false)
    cameraRender.release()
    for (baseFilterRender in filterRenders) baseFilterRender.release()
    filterRenders.clear()
    screenRender.release()
  }

  private fun setFilter(position: Int, baseFilterRender: BaseFilterRender) {
    val id = filterRenders[position].previousTexId
    val renderHandler = filterRenders[position].renderHandler
    filterRenders[position].release()
    filterRenders[position] = baseFilterRender
    filterRenders[position].previousTexId = id
    filterRenders[position].initGl(width, height, context, previewWidth, previewHeight)
    filterRenders[position].renderHandler = renderHandler
  }

  private fun addFilter(baseFilterRender: BaseFilterRender) {
    filterRenders.add(baseFilterRender)
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight)
    baseFilterRender.initFBOLink()
  }

  private fun addFilter(position: Int, baseFilterRender: BaseFilterRender) {
    filterRenders.add(position, baseFilterRender)
    baseFilterRender.initGl(width, height, context, previewWidth, previewHeight)
    baseFilterRender.initFBOLink()
  }

  private fun clearFilters() {
    for (baseFilterRender in filterRenders) {
      baseFilterRender.release()
    }
    filterRenders.clear()
  }

  private fun removeFilter(position: Int) {
    filterRenders.removeAt(position).release()
  }

  private fun removeFilter(baseFilterRender: BaseFilterRender) {
    baseFilterRender.release()
    filterRenders.remove(baseFilterRender)
  }

  private fun reOrderFilters(filters: List<BaseFilterRender>) {
    for (i in filters.indices) {
      val texId = if (i == 0) cameraRender.texId else filters[i - 1].texId
      filters[i].previousTexId = texId
    }
    val texId = if (filters.isEmpty()) cameraRender.texId else filters[filters.size - 1].texId
    screenRender.setTexId(texId)
  }

  fun setFilterAction(filterAction: FilterAction, position: Int, baseFilterRender: BaseFilterRender) {
    when (filterAction) {
      FilterAction.SET -> if (filterRenders.size > 0) {
        setFilter(position, baseFilterRender)
      } else {
        addFilter(baseFilterRender)
      }
      FilterAction.SET_INDEX -> setFilter(position, baseFilterRender)
      FilterAction.ADD -> addFilter(baseFilterRender)
      FilterAction.ADD_INDEX -> addFilter(position, baseFilterRender)
      FilterAction.CLEAR -> clearFilters()
      FilterAction.REMOVE -> removeFilter(baseFilterRender)
      FilterAction.REMOVE_INDEX -> removeFilter(position)
    }
  }

  fun filtersCount(): Int {
    return filterRenders.size
  }

  fun setPreviewSize(previewWidth: Int, previewHeight: Int) {
    for (i in filterRenders.indices) {
      filterRenders[i].setPreviewSize(previewWidth, previewHeight)
    }
  }

  fun updateFrame() {
    cameraRender.updateTexImage()
  }

  fun getSurfaceTexture(): SurfaceTexture {
    return cameraRender.surfaceTexture
  }

  fun getSurface(): Surface {
    return cameraRender.surface
  }

  fun setCameraRotation(rotation: Int) {
    cameraRender.setRotation(rotation)
  }

  fun setCameraFlip(isFlipHorizontal: Boolean, isFlipVertical: Boolean) {
    cameraRender.setFlip(isFlipHorizontal, isFlipVertical)
  }
}
