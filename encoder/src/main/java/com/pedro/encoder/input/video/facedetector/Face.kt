package com.pedro.encoder.input.video.facedetector

import android.graphics.Point
import android.graphics.Rect

/**
 * Created by pedro on 10/10/23.
 */
data class Face(
  val id: Int?, //depend if device support it, if not supported the value could be -1
  val leftEye: Point?, //depend if device support it
  val rightEye: Point?, //depend if device support it
  val mouth: Point?, //depend if device support it
  val rect: Rect,
  val score: Int //range 1 to 100
)
