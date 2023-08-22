/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.srt.srt.packets.control

/**
 * Created by pedro on 22/8/23.
 */
enum class ExtensionField(val value: Int) {
  HS_REQ(0), KM_REQ(2), CONFIG(4);

  companion object {
    infix fun from(value: Int): ExtensionField = ExtensionField.values().firstOrNull { it.value == value } ?: throw RuntimeException("unknown extension field: $value")
  }
}