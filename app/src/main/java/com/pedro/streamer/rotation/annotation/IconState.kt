package com.pedro.streamer.rotation.annotation

import androidx.annotation.IntDef

@IntDef(IconState.SHOW, IconState.GONE, IconState.ON, IconState.OFF, IconState.ENABLE, IconState.DISABLE)
@Retention(AnnotationRetention.SOURCE)
annotation class IconState {
    companion object {
        const val SHOW = 0x00
        const val GONE = 0x01
        const val ON = 0x02
        const val OFF = 0x03
        const val ENABLE = 0x04
        const val DISABLE = 0x05
    }
}
