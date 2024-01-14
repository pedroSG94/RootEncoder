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

package com.pedro.streamer;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.pedro.streamer.backgroundexample.BackgroundActivity;
import com.pedro.streamer.customexample.RtmpActivity;
import com.pedro.streamer.customexample.RtspActivity;
import com.pedro.streamer.defaultexample.ExampleRtmpActivity;
import com.pedro.streamer.defaultexample.ExampleRtspActivity;
import com.pedro.streamer.defaultexample.ExampleSrtActivity;
import com.pedro.streamer.displayexample.DisplayActivity;
import com.pedro.streamer.filestreamexample.RtmpFromFileActivity;
import com.pedro.streamer.filestreamexample.RtspFromFileActivity;
import com.pedro.streamer.openglexample.OpenGlGenericActivity;
import com.pedro.streamer.openglexample.OpenGlRtmpActivity;
import com.pedro.streamer.openglexample.OpenGlRtspActivity;
import com.pedro.streamer.openglexample.OpenGlSrtActivity;
import com.pedro.streamer.rotation.RotationExampleActivity;
import com.pedro.streamer.surfacemodeexample.SurfaceModeRtmpActivity;
import com.pedro.streamer.surfacemodeexample.SurfaceModeRtspActivity;
import com.pedro.streamer.texturemodeexample.TextureModeRtmpActivity;
import com.pedro.streamer.texturemodeexample.TextureModeRtspActivity;
import com.pedro.streamer.utils.ActivityLink;
import com.pedro.streamer.utils.ImageAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

  private GridView list;
  private List<ActivityLink> activities;

  private final String[] PERMISSIONS = {
      Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
  };

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  private final String[] PERMISSIONS_A_13 = {
          Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
          Manifest.permission.POST_NOTIFICATIONS
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    overridePendingTransition(R.transition.slide_in, R.transition.slide_out);
    TextView tvVersion = findViewById(R.id.tv_version);
    tvVersion.setText(getString(R.string.version, BuildConfig.VERSION_NAME));

    list = findViewById(R.id.list);
    createList();
    setListAdapter(activities);
    requestPermissions();
  }

  private void requestPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (!hasPermissions(this)) {
        ActivityCompat.requestPermissions(this, PERMISSIONS_A_13, 1);
      }
    } else {
      if (!hasPermissions(this)) {
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
      }
    }
  }

  @SuppressLint("NewApi")
  private void createList() {
    activities = new ArrayList<>();
    activities.add(new ActivityLink(new Intent(this, RtmpActivity.class),
        getString(R.string.rtmp_streamer), JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, RtspActivity.class),
        getString(R.string.rtsp_streamer), JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, ExampleRtmpActivity.class),
        getString(R.string.default_rtmp), JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, ExampleRtspActivity.class),
        getString(R.string.default_rtsp), JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, ExampleSrtActivity.class),
            getString(R.string.default_srt), JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, RtmpFromFileActivity.class),
        getString(R.string.from_file_rtmp), JELLY_BEAN_MR2));
    activities.add(new ActivityLink(new Intent(this, RtspFromFileActivity.class),
        getString(R.string.from_file_rtsp), JELLY_BEAN_MR2));
    activities.add(new ActivityLink(new Intent(this, SurfaceModeRtmpActivity.class),
        getString(R.string.surface_mode_rtmp), LOLLIPOP));
    activities.add(new ActivityLink(new Intent(this, SurfaceModeRtspActivity.class),
        getString(R.string.surface_mode_rtsp), LOLLIPOP));
      activities.add(new ActivityLink(new Intent(this, TextureModeRtmpActivity.class),
              getString(R.string.texture_mode_rtmp), LOLLIPOP));
      activities.add(new ActivityLink(new Intent(this, TextureModeRtspActivity.class),
              getString(R.string.texture_mode_rtsp), LOLLIPOP));
      activities.add(new ActivityLink(new Intent(this, OpenGlRtmpActivity.class),
              getString(R.string.opengl_rtmp), JELLY_BEAN_MR2));
      activities.add(new ActivityLink(new Intent(this, OpenGlRtspActivity.class),
              getString(R.string.opengl_rtsp), JELLY_BEAN_MR2));
      activities.add(new ActivityLink(new Intent(this, OpenGlSrtActivity.class),
              getString(R.string.opengl_srt), JELLY_BEAN_MR2));
      activities.add(new ActivityLink(new Intent(this, OpenGlGenericActivity.class),
              getString(R.string.opengl_generic), JELLY_BEAN_MR2));
      activities.add(new ActivityLink(new Intent(this, DisplayActivity.class),
              getString(R.string.display_rtmp), LOLLIPOP));
      activities.add(new ActivityLink(new Intent(this, BackgroundActivity.class),
              getString(R.string.service_rtp), LOLLIPOP));
      activities.add(new ActivityLink(new Intent(this, RotationExampleActivity.class),
              getString(R.string.rotation_rtmp), LOLLIPOP));
  }

  private void setListAdapter(List<ActivityLink> activities) {
    list.setAdapter(new ImageAdapter(activities));
    list.setOnItemClickListener(this);
  }

  @Override
  public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
    if (hasPermissions(this)) {
      ActivityLink link = activities.get(i);
      int minSdk = link.getMinSdk();
      if (Build.VERSION.SDK_INT >= minSdk) {
        startActivity(link.getIntent());
        overridePendingTransition(R.transition.slide_in, R.transition.slide_out);
      } else {
        showMinSdkError(minSdk);
      }
    } else {
      showPermissionsErrorAndRequest();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data != null && (requestCode == 0 && resultCode == Activity.RESULT_OK) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      ActivityLink link = new ActivityLink(new Intent(this, RotationExampleActivity.class),
          getString(R.string.rotation_rtmp), LOLLIPOP);
      startActivity(link.getIntent());
      overridePendingTransition(R.transition.slide_in, R.transition.slide_out);
    }
  }

  private void showMinSdkError(int minSdk) {
    String named;
    switch (minSdk) {
      case JELLY_BEAN_MR2:
        named = "JELLY_BEAN_MR2";
        break;
      case LOLLIPOP:
        named = "LOLLIPOP";
        break;
      default:
        named = "JELLY_BEAN";
        break;
    }
    Toast.makeText(this, "You need min Android " + named + " (API " + minSdk + " )",
        Toast.LENGTH_SHORT).show();
  }

  private void showPermissionsErrorAndRequest() {
    Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
    requestPermissions();
  }

  private boolean hasPermissions(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return hasPermissions(context, PERMISSIONS_A_13);
    } else {
      return hasPermissions(context, PERMISSIONS);
    }
  }

  private boolean hasPermissions(Context context, String... permissions) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
      for (String permission : permissions) {
        if (ActivityCompat.checkSelfPermission(context, permission)
            != PackageManager.PERMISSION_GRANTED) {
          return false;
        }
      }
    }
    return true;
  }
}