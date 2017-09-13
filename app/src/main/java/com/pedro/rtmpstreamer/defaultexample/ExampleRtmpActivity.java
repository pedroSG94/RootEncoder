package com.pedro.rtmpstreamer.defaultexample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * This class is only for a simple example of library use with default stream values.
 * Video = 1280x720 resolution, 30fps, 1500 * 1024 bitrate, 0 rotation.
 * Audio = stereo, 128 * 1024 bitrate, 44100 sampleRate.
 */
public class ExampleRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener {

  private RtmpCamera1 rtmpCamera1;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_example);
    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    button = (Button) findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    rtmpCamera1 = new RtmpCamera1(surfaceView, this);
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtmpActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtmpActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(ExampleRtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtmpActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtmpActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
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
}