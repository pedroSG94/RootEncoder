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

package com.pedro.jcsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pedro.jcsample.screen.MainScreen
import com.pedro.jcsample.ui.theme.RootEncoderTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

  private val mainViewModel: MainViewModel by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mainViewModel.init()
    mainViewModel.prepare()
    setContent {
      val state by mainViewModel.mainState.collectAsState()
      RootEncoderTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainScreen(
            modifier = Modifier,
            onStart = {
              mainViewModel.startPreview(it)
            },
            onStop = {
              mainViewModel.stopPreview()
            },
            recordClick = {
              mainViewModel.onRecordClick()
            },
            streamClick = {
              mainViewModel.onStreamClick()
            },
            switchClick = {
              mainViewModel.switchCamera()
            },
            onTextChanged = {
              mainViewModel.updateText(it)
            },
            mainState = state
          )
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) mainViewModel.release()
  }
}