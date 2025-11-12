package com.pedro.streamer.rotation.bean

import android.util.SparseIntArray
import androidx.annotation.IntDef
import com.pedro.streamer.rotation.annotation.CameraMode
import com.pedro.streamer.rotation.annotation.IconState
import com.pedro.streamer.rotation.annotation.SettingType

data class IconInfo(
    @CameraMode
    val mode: Int,
    @SettingType
    val type: Int,
    @IconState
    var state: Int,
    val images: SparseIntArray,
)
