package com.pedro.encoder.input.sources

data class OrientationConfig(
    val cameraOrientation: Int? = null,
    val isPortrait: Boolean? = null,
    val forced: OrientationForced = OrientationForced.NONE
)