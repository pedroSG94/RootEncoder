/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.streamer.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;

/**
 * Created by pedro on 21/06/17.
 * Get absolute path from onActivityResult
 * https://stackoverflow.com/questions/33295300/how-to-get-absolute-path-in-android-for-file
 */

public class PathUtils {

  public static File getRecordPath() {
    File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    return new File(storageDir.getAbsolutePath() + "/RootEncoder");
  }

  public static File getRecordPath(Context context) {
    return context.getExternalFilesDir(null);
  }

  public static void updateGallery(Context context, String path) {
    context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(path))));
  }
}
