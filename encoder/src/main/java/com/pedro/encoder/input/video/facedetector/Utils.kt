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

@file:Suppress("DEPRECATION")

package com.pedro.encoder.input.video.facedetector

import android.os.Build
import androidx.annotation.RequiresApi
import android.hardware.Camera.Face as Camera1Face
import android.hardware.camera2.params.Face as Camera2Face

/**
 * Created by pedro on 10/10/23.
 */

fun Camera1Face.toFace(): Face {
  return Face(
    id = id,
    leftEye = leftEye,
    rightEye = rightEye,
    mouth = mouth,
    rect = rect,
    score = score
  )
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun Camera2Face.toFace(): Face {
  return Face(
    id = id,
    leftEye = leftEyePosition,
    rightEye = rightEyePosition,
    mouth = mouthPosition,
    rect = bounds,
    score = score
  )
}

fun mapCamera1Faces(faces: Array<Camera1Face>): Array<Face> {
  return faces.map { it.toFace() }.toTypedArray()
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun mapCamera2Faces(faces: Array<Camera2Face>): Array<Face> {
  return faces.map { it.toFace() }.toTypedArray()
}