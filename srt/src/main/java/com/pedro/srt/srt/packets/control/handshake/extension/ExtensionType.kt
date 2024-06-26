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

package com.pedro.srt.srt.packets.control.handshake.extension

/**
 * Created by pedro on 22/8/23.
 */
enum class ExtensionType(val value: Int) {
  SRT_CMD_HS_REQ(1), SRT_CMD_HS_RSP(2), SRT_CMD_KM_REQ(3), SRT_CMD_KM_RSP(4),
  SRT_CMD_SID(5), SRT_CMD_CONGESTION(6), SRT_CMD_FILTER(7), SRT_CMD_GROUP(8)
}