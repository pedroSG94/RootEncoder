package com.pedro.rtpstreamer;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.rtplibrary.rtmp.GlRtmp;
import com.pedro.rtplibrary.view.CustomGlSurfaceView;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * Demonstration activity to work with a custom GlSurfaceView
 * {@link com.pedro.rtplibrary.view.CustomGlSurfaceView}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CubeTestRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener {

  private GlRtmp rtmpCamera1;
  private Button button;
  private EditText etUrl;
  private CustomGlSurfaceView customGlSurfaceView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_cube_test_rtmp);
    customGlSurfaceView = findViewById(R.id.surfaceView);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    rtmpCamera1 = new GlRtmp(customGlSurfaceView, this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.gl_menu, menu);
    return true;
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CubeTestRtmpActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CubeTestRtmpActivity.this, "Connection failed. " + reason,
            Toast.LENGTH_SHORT).show();
        rtmpCamera1.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CubeTestRtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CubeTestRtmpActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CubeTestRtmpActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    if (!rtmpCamera1.isStreaming()) {
      if (rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
        button.setText(R.string.stop_button);
        rtmpCamera1.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(R.string.start_button);
      rtmpCamera1.stopStream();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    customGlSurfaceView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    customGlSurfaceView.onPause();
    if (rtmpCamera1.isStreaming()) {
      rtmpCamera1.stopStream();
    }
  }
}
