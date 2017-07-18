package com.pedro.rtmpstreamer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.pedro.rtmpstreamer.customexample.RtmpActivity;
import com.pedro.rtmpstreamer.customexample.RtspActivity;
import com.pedro.rtmpstreamer.defaultexample.ExampleRtmpActivity;
import com.pedro.rtmpstreamer.defaultexample.ExampleRtspActivity;
import com.pedro.rtmpstreamer.filestreamexample.RtmpFromFileActivity;
import com.pedro.rtmpstreamer.filestreamexample.RtspFromFileActivity;
import com.pedro.rtmpstreamer.surfacemodeexample.SurfaceModeRtmpActivity;
import com.pedro.rtmpstreamer.surfacemodeexample.SurfaceModeRtspActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

  private final String[] PERMISSIONS = {
      Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    overridePendingTransition(R.transition.slide_in, R.transition.slide_out);

    Button bRtmp = (Button) findViewById(R.id.b_rtmp);
    Button bRtsp = (Button) findViewById(R.id.b_rtsp);
    bRtmp.setOnClickListener(this);
    bRtsp.setOnClickListener(this);
    Button bDefaultRtmp = (Button) findViewById(R.id.b_default_rtmp);
    Button bDefaultRtsp = (Button) findViewById(R.id.b_default_rtsp);
    bDefaultRtmp.setOnClickListener(this);
    bDefaultRtsp.setOnClickListener(this);
    Button bFromFileRtsp = (Button) findViewById(R.id.b_from_file_rtsp);
    Button bFromFileRtmp = (Button) findViewById(R.id.b_from_file_rtmp);
    bFromFileRtsp.setOnClickListener(this);
    bFromFileRtmp.setOnClickListener(this);
    Button bSurfaceModeRtsp = (Button) findViewById(R.id.b_surface_mode_rtsp);
    Button bSurfaceModeRtmp = (Button) findViewById(R.id.b_surface_mode_rtmp);
    bSurfaceModeRtsp.setOnClickListener(this);
    bSurfaceModeRtmp.setOnClickListener(this);
    if (!hasPermissions(this, PERMISSIONS)) {
      ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.b_rtmp:
        if (hasPermissions(this, PERMISSIONS)) {
          startActivity(new Intent(this, RtmpActivity.class));
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_rtsp:
        if (hasPermissions(this, PERMISSIONS)) {
          startActivity(new Intent(this, RtspActivity.class));
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_default_rtmp:
        if (hasPermissions(this, PERMISSIONS)) {
          startActivity(new Intent(this, ExampleRtmpActivity.class));
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_default_rtsp:
        if (hasPermissions(this, PERMISSIONS)) {
          startActivity(new Intent(this, ExampleRtspActivity.class));
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_from_file_rtmp:
        if (hasPermissions(this, PERMISSIONS)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            startActivity(new Intent(this, RtmpFromFileActivity.class));
          } else {
            Toast.makeText(this, "You need min Android JellyBean MR2(API 18)", Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_from_file_rtsp:
        if (hasPermissions(this, PERMISSIONS)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            startActivity(new Intent(this, RtspFromFileActivity.class));
          } else {
            Toast.makeText(this, "You need min Android JellyBean MR2(API 18)", Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_surface_mode_rtmp:
        if (hasPermissions(this, PERMISSIONS)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(this, SurfaceModeRtmpActivity.class));
          } else {
            Toast.makeText(this, "You need min Android LOLLIPOP(API 21)", Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      case R.id.b_surface_mode_rtsp:
        if (hasPermissions(this, PERMISSIONS)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(this, SurfaceModeRtspActivity.class));
          } else {
            Toast.makeText(this, "You need min Android LOLLIPOP(API 21)", Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          Toast.makeText(this, "You need permissions before", Toast.LENGTH_SHORT).show();
          ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }
        break;
      default:
        break;
    }
  }

  public boolean hasPermissions(Context context, String... permissions) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && context != null
        && permissions != null) {
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
