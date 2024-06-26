/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pedro.streamer.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/**
 * Created by pedro on 21/06/17.
 * Get absolute path from onActivityResult
 * https://stackoverflow.com/questions/33295300/how-to-get-absolute-path-in-android-for-file
 */
object PathUtils {
  @JvmStatic
  fun getRecordPath(): File {
    val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    return File(storageDir.absolutePath + "/RootEncoder")
  }

  @JvmStatic
  fun updateGallery(context: Context, path: String) {
    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    context.toast("Video saved at: $path")
  }
}
