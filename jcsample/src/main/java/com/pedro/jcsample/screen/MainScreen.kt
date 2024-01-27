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

package com.pedro.jcsample.screen

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pedro.jcsample.components.CircleButton
import com.pedro.jcsample.components.DefaultDisabled
import com.pedro.jcsample.components.DefaultEnabled
import com.pedro.jcsample.components.StreamView
import com.pedro.jcsample.ui.theme.RootEncoderTheme

/**
 * Created by pedro on 26/1/24.
 */

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  onStart: (SurfaceView) -> Unit = {},
  onStop: (SurfaceView) -> Unit = {},
  recordClick: () -> Unit = {},
  streamClick: () -> Unit = {},
  switchClick: () -> Unit = {}
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.BottomCenter
  ) {
    StreamView(
      modifier = Modifier.fillMaxSize(),
      onCreate = onStart,
      onDestroy = onStop
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      var recording by remember { mutableStateOf(false) }
      var streaming by remember { mutableStateOf(false) }
      CircleButton(
        modifier = Modifier.size(56.dp), enabled = recording,
        enabledIcon = {
          DefaultEnabled()
        },
        disabledIcon = {
          Icon(
            modifier = Modifier.fillMaxSize(),
            imageVector = Icons.Outlined.Videocam,
            contentDescription = "circle button",
            tint = Color.White
          )
        },
        onClick = {
          recording = !recording
        }
      )
      CircleButton(
        modifier = Modifier.size(80.dp), enabled = streaming,
        enabledIcon = {
          DefaultEnabled()
        },
        disabledIcon = {
          DefaultDisabled()
        },
        onClick = {
          streaming = !streaming
        }
      )
      IconButton(
        modifier = Modifier
          .size(56.dp)
          .background(Color.Transparent, shape = CircleShape)
          .border(2.dp, Color.White, CircleShape)
          .padding(8.dp),
        onClick = {
          switchClick()
        }
      ) {
        Icon(
          modifier = Modifier.fillMaxSize(),
          imageVector = Icons.Outlined.Cameraswitch,
          contentDescription = "switch camera",
          tint = Color.White
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  RootEncoderTheme {
    MainScreen()
  }
}