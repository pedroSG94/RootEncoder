package com.pedro.encoder.input.video.facedetector

import android.graphics.Rect

/**
 * Created by pedro on 10/10/23.
 */
interface FaceDetectorCallback {
  fun onGetFaces(faces: Array<Face>, scaleSensor: Rect?, sensorOrientation: Int)
}