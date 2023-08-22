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

package com.pedro.rtpstreamer.defaultexample;

import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pedro.rtplibrary.rtmp.RtmpCamera1;
import com.pedro.rtpstreamer.R;
import com.pedro.srt.srt.SrtClient;
import com.pedro.srt.utils.ConnectCheckerSrt;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 * {@link RtmpCamera1}
 */
public class ExampleSrtActivity extends AppCompatActivity
    implements ConnectCheckerSrt, View.OnClickListener, SurfaceHolder.Callback {

  private final SrtClient srtClient = new SrtClient(this);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_example);
  }

  @Override
  protected void onResume() {
    super.onResume();
    srtClient.connect("srt://192.168.0.191:8890/mystream1");
  }

  @Override
  protected void onPause() {
    super.onPause();
    srtClient.disconnect();
  }

  @Override
  public void onConnectionStartedSrt(String srtUrl) {
  }

  @Override
  public void onConnectionSuccessSrt() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleSrtActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedSrt(final String reason) {
    Toast.makeText(ExampleSrtActivity.this, "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onNewBitrateSrt(final long bitrate) {

  }

  @Override
  public void onDisconnectSrt() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleSrtActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorSrt() {
  }

  @Override
  public void onAuthSuccessSrt() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleSrtActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {

  }

  @Override
  public void surfaceCreated(SurfaceHolder surfaceHolder) {

  }

  @Override
  public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
  }
}
