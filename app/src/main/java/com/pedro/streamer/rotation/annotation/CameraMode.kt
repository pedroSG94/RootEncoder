package com.pedro.streamer.rotation.annotation

import androidx.annotation.IntDef

@IntDef(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.LIVE)
@Retention(AnnotationRetention.SOURCE)
annotation class CameraMode {
    companion object {
        const val PHOTO = 0
        const val VIDEO = 1
        const val LIVE = 2
    }
}