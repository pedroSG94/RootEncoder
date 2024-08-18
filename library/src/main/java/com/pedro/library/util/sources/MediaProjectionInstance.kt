package com.pedro.library.util.sources

import android.os.Build
import androidx.annotation.RequiresApi

object MediaProjectionInstance {

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    var mediaProjectionHandler = MediaProjectionHandler()
}