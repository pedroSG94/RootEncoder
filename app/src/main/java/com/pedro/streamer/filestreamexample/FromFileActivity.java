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

package com.pedro.streamer.filestreamexample;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.library.generic.GenericFromFile;
import com.pedro.streamer.R;
import com.pedro.streamer.utils.PathUtils;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * More documentation see:
 * {@link com.pedro.library.base.FromFileBase}
 * {@link com.pedro.library.rtmp.RtmpFromFile}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class FromFileActivity extends AppCompatActivity
    implements ConnectChecker, View.OnClickListener, VideoDecoderInterface,
    AudioDecoderInterface, SeekBar.OnSeekBarChangeListener {

  private GenericFromFile genericFromFile;
  private Button button, bSelectFile, bReSync, bRecord;
  private SeekBar seekBar;
  private EditText etUrl;
  private TextView tvFile;
  private Uri filePath;
  private boolean touching = false;

  private String currentDateAndTime = "";
  private File folder;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_from_file);
    folder = PathUtils.getRecordPath();
    button = findViewById(R.id.b_start_stop);
    bSelectFile = findViewById(R.id.b_select_file);
    button.setOnClickListener(this);
    bSelectFile.setOnClickListener(this);
    bReSync = findViewById(R.id.b_re_sync);
    bReSync.setOnClickListener(this);
    bRecord = findViewById(R.id.b_record);
    bRecord.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    seekBar = findViewById(R.id.seek_bar);
    seekBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
    tvFile = findViewById(R.id.tv_file);
    genericFromFile = new GenericFromFile(this, this, this);
    seekBar.setOnSeekBarChangeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (genericFromFile.isRecording()) {
      genericFromFile.stopRecord();
      PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
      bRecord.setText(R.string.start_record);
    }
    if (genericFromFile.isStreaming()) {
      genericFromFile.stopStream();
      button.setText(getResources().getString(R.string.start_button));
    }
  }

  @Override
  public void onConnectionStarted(@NonNull String url) {
  }

  @Override
  public void onConnectionSuccess() {
    Toast.makeText(FromFileActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onConnectionFailed(@NonNull final String reason) {
    Toast.makeText(FromFileActivity.this, "Connection failed. " + reason,
        Toast.LENGTH_SHORT).show();
    genericFromFile.stopStream();
    button.setText(R.string.start_button);
  }

  @Override
  public void onNewBitrate(long bitrate) {

  }

  @Override
  public void onDisconnect() {
    Toast.makeText(FromFileActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthError() {
    Toast.makeText(FromFileActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthSuccess() {
    Toast.makeText(FromFileActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 5 && data != null) {
      filePath = data.getData();
      if (filePath != null) {
        Toast.makeText(this, filePath.getPath(), Toast.LENGTH_SHORT).show();
        tvFile.setText(filePath.getPath());
      }
    }
  }

  @Override
  public void onClick(View view) {
    int id = view.getId();
    if (id == R.id.b_start_stop) {
      if (!genericFromFile.isStreaming()) {
        try {
          if (!genericFromFile.isRecording()) {
            if (prepare()) {
              button.setText(R.string.stop_button);
              genericFromFile.startStream(etUrl.getText().toString());
              seekBar.setMax(Math.max((int) genericFromFile.getVideoDuration(),
                      (int) genericFromFile.getAudioDuration()));
              updateProgress();
            } else {
              button.setText(R.string.start_button);
              genericFromFile.stopStream();
                /*This error could be 2 things.
                 Your device cant decode or encode this file or
                 the file is not supported for the library.
                The file need has h264 video codec and acc audio codec*/
              Toast.makeText(this, "Error: unsupported file", Toast.LENGTH_SHORT).show();
            }
          } else {
            button.setText(R.string.stop_button);
            genericFromFile.startStream(etUrl.getText().toString());
          }
        } catch (IOException e) {
          //Normally this error is for file not found or read permissions
          Toast.makeText(this, "Error: file not found", Toast.LENGTH_SHORT).show();
        }
      } else {
        button.setText(R.string.start_button);
        genericFromFile.stopStream();
      }
    } else if (id == R.id.b_select_file) {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("*/*");
      startActivityForResult(intent, 5);
      //sometimes async is produced when you move in file several times
    } else if (id == R.id.b_re_sync) {
      genericFromFile.reSyncFile();
    } else if (id == R.id.b_record) {
      if (!genericFromFile.isRecording()) {
        try {
          if (!folder.exists()) {
            folder.mkdir();
          }
          SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
          currentDateAndTime = sdf.format(new Date());
          if (!genericFromFile.isStreaming()) {
            if (prepare()) {
              genericFromFile.startRecord(
                      folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              seekBar.setMax(Math.max((int) genericFromFile.getVideoDuration(),
                      (int) genericFromFile.getAudioDuration()));
              updateProgress();
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            } else {
              Toast.makeText(this, "Error preparing stream, This device cant do it",
                      Toast.LENGTH_SHORT).show();
            }
          } else {
            genericFromFile.startRecord(
                    folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            bRecord.setText(R.string.stop_record);
            Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
          }
        } catch (IOException e) {
          genericFromFile.stopRecord();
          PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          bRecord.setText(R.string.start_record);
          Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
      } else {
        genericFromFile.stopRecord();
        PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
        bRecord.setText(R.string.start_record);
        Toast.makeText(this,
                "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
                Toast.LENGTH_SHORT).show();
        currentDateAndTime = "";
      }
    }
  }

  private boolean prepare() throws IOException {
    boolean result = genericFromFile.prepareVideo(getApplicationContext(), filePath);
    result |= genericFromFile.prepareAudio(getApplicationContext(), filePath);
    return result;
  }

  private void updateProgress() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (genericFromFile.isStreaming() || genericFromFile.isRecording()) {
          try {
            Thread.sleep(1000);
            if (!touching) {
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  seekBar.setProgress(Math.max((int) genericFromFile.getVideoTime(),
                      (int) genericFromFile.getAudioTime()));
                }
              });
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }).start();
  }

  @Override
  public void onVideoDecoderFinished() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (genericFromFile.isRecording()) {
          genericFromFile.stopRecord();
          PathUtils.updateGallery(getApplicationContext(), folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          bRecord.setText(R.string.start_record);
          Toast.makeText(FromFileActivity.this,
              "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
              Toast.LENGTH_SHORT).show();
          currentDateAndTime = "";
        }
        if (genericFromFile.isStreaming()) {
          button.setText(R.string.start_button);
          Toast.makeText(FromFileActivity.this, "Video stream finished", Toast.LENGTH_SHORT)
              .show();
          genericFromFile.stopStream();
        }
      }
    });
  }

  @Override
  public void onAudioDecoderFinished() {

  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    touching = true;
  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {
    if (genericFromFile.isStreaming() || genericFromFile.isRecording()) {
      genericFromFile.moveTo(seekBar.getProgress());
      //re sync after move to avoid async
      new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
          genericFromFile.reSyncFile();
        }
      }, 500);
    }
    touching = false;
  }
}

