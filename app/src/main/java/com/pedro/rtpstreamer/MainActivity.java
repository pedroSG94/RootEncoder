package com.pedro.rtpstreamer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import com.pedro.rtpstreamer.backgroundexample.BackgroundActivity;
import com.pedro.rtpstreamer.customexample.RtmpActivity;
import com.pedro.rtpstreamer.customexample.RtspActivity;
import com.pedro.rtpstreamer.defaultexample.ExampleRtmpActivity;
import com.pedro.rtpstreamer.defaultexample.ExampleRtspActivity;
import com.pedro.rtpstreamer.displayexample.DisplayActivity;
import com.pedro.rtpstreamer.filestreamexample.RtmpFromFileActivity;
import com.pedro.rtpstreamer.filestreamexample.RtspFromFileActivity;
import com.pedro.rtpstreamer.openglexample.OpenGlRtmpActivity;
import com.pedro.rtpstreamer.openglexample.OpenGlRtspActivity;
import com.pedro.rtpstreamer.surfacemodeexample.SurfaceModeRtmpActivity;
import com.pedro.rtpstreamer.surfacemodeexample.SurfaceModeRtspActivity;
import com.pedro.rtpstreamer.texturemodeexample.TextureModeRtmpActivity;
import com.pedro.rtpstreamer.texturemodeexample.TextureModeRtspActivity;
import com.pedro.rtpstreamer.utils.ActivityLink;
import com.pedro.rtpstreamer.utils.ImageAdapter;
import java.util.ArrayList;
import java.util.List;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

  private GridView list;
  private List<ActivityLink> activities;

  private final String[] PERMISSIONS = {
      Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
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

    if (!hasPermissions(this, PERMISSIONS)) {
      ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
    }
  }

  private void createList() {
    activities = new ArrayList<>();
    activities.add(
        new ActivityLink(new Intent(this, RtmpActivity.class), getString(R.string.rtmp_streamer),
            JELLY_BEAN));
    activities.add(
        new ActivityLink(new Intent(this, RtspActivity.class), getString(R.string.rtsp_streamer),
            JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, ExampleRtmpActivity.class),
        getString(R.string.default_rtmp), JELLY_BEAN));
    activities.add(new ActivityLink(new Intent(this, ExampleRtspActivity.class),
        getString(R.string.default_rtsp), JELLY_BEAN));
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
    activities.add(new ActivityLink(new Intent(this, DisplayActivity.class),
        getString(R.string.display_rtmp), LOLLIPOP));
    activities.add(new ActivityLink(new Intent(this, BackgroundActivity.class),
        getString(R.string.service_rtp), LOLLIPOP));
  }

  private void setListAdapter(List<ActivityLink> activities) {
    list.setAdapter(new ImageAdapter(activities));
    list.setOnItemClickListener(this);
  }

  @Override
  public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
    if (hasPermissions(this, PERMISSIONS)) {
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
    ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
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