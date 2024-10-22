/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.encoder.input.decoder

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import com.pedro.common.frame.MediaFrame
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Created by pedro on 18/10/24.
 */
interface Extractor {

  fun selectTrack(type: MediaFrame.Type): String

  fun initialize(path: String)

  fun initialize(context: Context, uri: Uri)

  fun initialize(fileDescriptor: FileDescriptor)

  fun readFrame(buffer: ByteBuffer): Int

  fun advance(): Boolean

  fun getTimeStamp(): Long

  fun getSleepTime(ts: Long): Long

  fun seekTo(time: Long)

  fun release()

  fun getVideoInfo(): VideoInfo

  fun getAudioInfo(): AudioInfo

  fun getFormat(): MediaFormat
}