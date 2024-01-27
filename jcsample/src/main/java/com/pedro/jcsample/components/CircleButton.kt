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

package com.pedro.jcsample.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pedro.jcsample.ui.theme.red

/**
 * Created by pedro on 27/1/24.
 */

@Composable
fun CircleButton(
  modifier: Modifier,
  enabled: Boolean,
  onClick: () -> Unit,
  enabledIcon: @Composable () -> Unit,
  disabledIcon: @Composable () -> Unit
) {
  val backgroundColor = if (enabled) red else Color.Transparent
  IconButton(
    modifier = modifier
      .border(2.dp, Color.White, CircleShape)
      .background(backgroundColor, shape = CircleShape)
      .padding(8.dp),
    onClick = onClick
  ) {
    if (enabled) {
      enabledIcon()
    } else {
      disabledIcon()
    }
  }
}

@Composable
fun DefaultEnabled() {
  Icon(
    modifier = Modifier.fillMaxSize(),
    imageVector = Icons.Outlined.Pause,
    contentDescription = "circle button",
    tint = Color.White
  )
}

@Composable
fun DefaultDisabled() {
  Box(modifier = Modifier.fillMaxSize().background(Color.White, shape = CircleShape))
}