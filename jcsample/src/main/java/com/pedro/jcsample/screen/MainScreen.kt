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

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pedro.jcsample.MainState
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
  switchClick: () -> Unit = {},
  onTextChanged: (String) -> Unit = {},
  mainState: MainState
) {
  val configuration = LocalConfiguration.current

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) Alignment.CenterEnd else Alignment.BottomCenter
  ) {
    StreamView(
      modifier = Modifier.fillMaxSize(),
      onCreate = onStart,
      onDestroy = onStop
    )
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(16.dp),
      contentAlignment = Alignment.TopCenter
    ) {
      var url by remember { mutableStateOf(mainState.url) }

      TextField(
        value = url, 
        onValueChange = {
          url = it
          onTextChanged(url)
        },
        placeholder = { Text(text = "protocol://ip:port/appName/streamName") }
      )
    }
    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      Column(
        modifier = Modifier
          .fillMaxHeight()
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
      ) {
        ButtonsLayout(recordClick, streamClick, switchClick, mainState)
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        ButtonsLayout(recordClick, streamClick, switchClick, mainState)
      }
    }
  }
}

@Composable
fun ButtonsLayout(
  recordClick: () -> Unit,
  streamClick: () -> Unit,
  switchClick: () -> Unit,
  mainState: MainState
) {
  val configuration = LocalConfiguration.current

  if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
    SwitchButton { switchClick() }
  } else {
    RecordButton(enabled = mainState.recording) { recordClick() }
  }
  StreamButton(enabled = mainState.streaming) { streamClick() }
  if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
    RecordButton(enabled = mainState.recording) { recordClick() }
  } else {
    SwitchButton { switchClick() }
  }
}

@Composable
fun StreamButton(
  enabled: Boolean,
  onClick: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current

  CircleButton(
    modifier = Modifier.size(80.dp), enabled = enabled,
    enabledIcon = {
      DefaultEnabled()
    },
    disabledIcon = {
      DefaultDisabled()
    },
    onClick = {
      clickEffect(haptic, context)
      onClick()
    }
  )
}

@Composable
fun RecordButton(
  enabled: Boolean,
  onClick: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current

  CircleButton(
    modifier = Modifier.size(56.dp), enabled = enabled,
    enabledIcon = {
      DefaultEnabled()
    },
    disabledIcon = {
      Icon(
        modifier = Modifier.fillMaxSize(),
        imageVector = Icons.Outlined.Videocam,
        contentDescription = "record button",
        tint = Color.White
      )
    },
    onClick = {
      clickEffect(haptic, context)
      onClick()
    }
  )
}

@Composable
fun SwitchButton(
  onClick: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current

  IconButton(
    modifier = Modifier
      .size(56.dp)
      .background(Color.Transparent, shape = CircleShape)
      .border(2.dp, Color.White, CircleShape)
      .padding(8.dp),
    onClick = {
      clickEffect(haptic, context)
      onClick()
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

private fun clickEffect(haptic: HapticFeedback, context: Context) {
  val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK,1.0f)
  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  RootEncoderTheme {
    MainScreen(mainState = MainState(recording = false, streaming = false, url = ""))
  }
}