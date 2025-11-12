package com.pedro.streamer.rotation.annotation

@Retention(AnnotationRetention.SOURCE)
annotation class SettingType {
    companion object {
        const val LIVE = 0x00
        const val RESOLUTION = 0x01
        const val MICROPHONE = 0x02
    }
}
