package com.pedro.streamer.rotation.topmethod

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.onBackPressedListener(isEnabled: Boolean, callback: () -> Unit) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(isEnabled) {
        override fun handleOnBackPressed() {
            callback()
        }
    })
}