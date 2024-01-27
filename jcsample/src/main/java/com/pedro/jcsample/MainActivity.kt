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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.pedro.jcsample.screen.MainScreen
import com.pedro.jcsample.ui.theme.RootEncoderTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

  private val mainViewModel: MainViewModel by inject()

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mainViewModel.init()
    setContent {
      RootEncoderTheme {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
              ModalDrawerSheet {

              }
            }
          ) {
            Scaffold(
              topBar = {
                TopAppBar(
                  title = {  },
                  navigationIcon = {
                    IconButton(onClick = {
                      scope.launch { drawerState.open() }
                    }) {
                      Icon(imageVector = Icons.Default.Menu, contentDescription = "menu")
                    }
                  }
                )
              }
            ) { padding ->
              MainScreen(
                modifier = Modifier.padding(padding),
                onStart = {
                  mainViewModel.startPreview(it)
                },
                onStop = {
                  mainViewModel.stopPreview()
                },
                recordClick = {},
                streamClick = {},
                switchClick = {
                  mainViewModel.switchCamera()
                }
              )
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) mainViewModel.release()
  }
}