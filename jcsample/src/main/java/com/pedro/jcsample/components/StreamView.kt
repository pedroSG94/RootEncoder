/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.jcsample.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Created by pedro on 26/1/24.
 */

@Composable
fun StreamView(
  modifier: Modifier = Modifier,
  onCreate: (SurfaceView) -> Unit,
  onDestroy: (SurfaceView) -> Unit
) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      SurfaceView(context).apply {
        holder.addCallback(object: SurfaceHolder.Callback {
          override fun surfaceCreated(holder: SurfaceHolder) {
          }

          override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, heigth: Int) {
            onCreate(this@apply)
          }

          override fun surfaceDestroyed(holder: SurfaceHolder) {
            onDestroy(this@apply)
          }
        })
      }
    })
}