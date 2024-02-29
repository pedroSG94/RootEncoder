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

package com.pedro.streamer.defaultexample;

import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.library.base.recording.RecordController;
import com.pedro.library.generic.GenericCamera1;
import com.pedro.streamer.R;
import com.pedro.streamer.utils.PathUtils;
import com.pedro.streamer.utils.ScreenOrientation;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * More documentation see:
 * {@link com.pedro.library.base.Camera1Base}
 * {@link com.pedro.library.rtmp.RtmpCamera1}
 */
public class OldApiActivity extends AppCompatActivity
    implements ConnectChecker, View.OnClickListener, SurfaceHolder.Callback {

  private GenericCamera1 rtmpCamera1;
  private ImageView button;
  private ImageView bRecord;
  private EditText etUrl;
  private String currentDateAndTime = "";
  private File folder;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_example);
    folder = PathUtils.getRecordPath();
    SurfaceView surfaceView = findViewById(R.id.surfaceView);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    bRecord = findViewById(R.id.b_record);
    bRecord.setOnClickListener(this);
    ImageView switchCamera = findViewById(R.id.switch_camera);
    switchCamera.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    rtmpCamera1 = new GenericCamera1(surfaceView, this);
    surfaceView.getHolder().addCallback(this);
  }

  @Override
  public void onConnectionStarted(@NonNull String url) {
  }

  @Override
  public void onConnectionSuccess() {
    Toast.makeText(OldApiActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onConnectionFailed(@NonNull final String reason) {
    Toast.makeText(OldApiActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
        .show();
    rtmpCamera1.stopStream();
    ScreenOrientation.INSTANCE.unlockScreen(this);
    button.setImageResource(R.drawable.stream_icon);
  }

  @Override
  public void onNewBitrate(final long bitrate) {

  }

  @Override
  public void onDisconnect() {
    Toast.makeText(OldApiActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthError() {
    Toast.makeText(OldApiActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
    rtmpCamera1.stopStream();
    ScreenOrientation.INSTANCE.unlockScreen(this);
    button.setImageResource(R.drawable.stream_icon);
  }

  @Override
  public void onAuthSuccess() {
    Toast.makeText(OldApiActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onClick(View view) {
    int id = view.getId();
    if (id == R.id.b_start_stop) {
      if (!rtmpCamera1.isStreaming()) {
        if (rtmpCamera1.isRecording()
                || rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
          button.setImageResource(R.drawable.stream_stop_icon);
          rtmpCamera1.startStream(etUrl.getText().toString());
          ScreenOrientation.INSTANCE.lockScreen(this);
        } else {
          Toast.makeText(this, "Error preparing stream, This device cant do it",
                  Toast.LENGTH_SHORT).show();
        }
      } else {
        button.setImageResource(R.drawable.stream_icon);
        rtmpCamera1.stopStream();
        ScreenOrientation.INSTANCE.unlockScreen(this);
      }
    } else if (id == R.id.switch_camera) {
      try {
        rtmpCamera1.switchCamera();
      } catch (CameraOpenException e) {
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    } else if (id == R.id.b_record) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        if (!rtmpCamera1.isRecording()) {
          try {
            if (!folder.exists()) {
              folder.mkdir();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            currentDateAndTime = sdf.format(new Date());
            if (!rtmpCamera1.isStreaming()) {
              if (rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
                rtmpCamera1.startRecord(
                    folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4",
                    status -> {
                      if (status == RecordController.Status.RECORDING) {
                        bRecord.setBackgroundResource(R.drawable.stop_icon);
                      }
                    });
                ScreenOrientation.INSTANCE.lockScreen(this);
                bRecord.setBackgroundResource(R.drawable.pause_icon);
                Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
              } else {
                Toast.makeText(this, "Error preparing stream, This device cant do it",
                        Toast.LENGTH_SHORT).show();
              }
            } else {
              rtmpCamera1.startRecord(
                      folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              ScreenOrientation.INSTANCE.lockScreen(this);
              bRecord.setBackgroundResource(R.drawable.stop_icon);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            }
          } catch (IOException e) {
            rtmpCamera1.stopRecord();
            ScreenOrientation.INSTANCE.unlockScreen(this);
            PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            bRecord.setBackgroundResource(R.drawable.record_icon);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        } else {
          rtmpCamera1.stopRecord();
          ScreenOrientation.INSTANCE.unlockScreen(this);
          PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          bRecord.setBackgroundResource(R.drawable.record_icon);
          Toast.makeText(this,
                  "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
                  Toast.LENGTH_SHORT).show();
        }
      } else {
        Toast.makeText(this, "You need min JELLY_BEAN_MR2(API 18) for do it...",
                Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder surfaceHolder) {

  }

  @Override
  public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    rtmpCamera1.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && rtmpCamera1.isRecording()) {
      rtmpCamera1.stopRecord();
      PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
      bRecord.setBackgroundResource(R.drawable.record_icon);
      Toast.makeText(this,
          "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
          Toast.LENGTH_SHORT).show();
      currentDateAndTime = "";
    }
    if (rtmpCamera1.isStreaming()) {
      rtmpCamera1.stopStream();
      button.setImageResource(R.drawable.stream_icon);
    }
    ScreenOrientation.INSTANCE.unlockScreen(this);
    rtmpCamera1.stopPreview();
  }
}
