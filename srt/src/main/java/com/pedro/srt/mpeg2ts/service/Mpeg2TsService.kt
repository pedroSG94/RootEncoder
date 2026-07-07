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

package com.pedro.srt.mpeg2ts.service

import com.pedro.srt.mpeg2ts.Codec
import com.pedro.srt.mpeg2ts.Pid
import com.pedro.srt.mpeg2ts.psi.Pmt

/**
 * Created by pedro on 26/8/23.
 */
data class Mpeg2TsService(
  val type: Byte = 0x01, //digital tv
  val id: Short = 0x4698,
  val name: String = "Mpeg2TsService",
  val providerName: String = "com.pedro.srt",
  var pmt: Pmt? = null,
  val tracks: MutableList<Track> = mutableListOf(),
  var pcrPid: Short? = null,
  private val pid: Pid = Pid()
) {

  fun addTrack(codec: Codec) {
    val trackPid = pid.generatePID()
    tracks.add(Track(codec, trackPid))
    if (pcrPid == null) pcrPid = trackPid
    else if (!codec.isAudio()) pcrPid = trackPid
  }

  fun generatePmt() {
    if (pmt == null) {
      pmt = Pmt(
        pid.generatePID().toInt(),
        version = 0,
        service = this
      )
    }
  }

  fun clear() {
    tracks.clear()
    pid.reset()
    pcrPid = null
    pmt = null
  }
}