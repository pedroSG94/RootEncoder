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