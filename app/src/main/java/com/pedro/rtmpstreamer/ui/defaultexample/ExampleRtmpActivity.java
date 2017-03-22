package com.pedro.rtmpstreamer.ui.defaultexample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtmpstreamer.builders.RtmpBuilder;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * This class is only for a simple example of library use with default stream values
 * Video = 1280x720 resolution, 30fps, 1500 * 1024 bitrate, 0 rotation. See VideoEncoder class for more info
 * Audio = stereo, 128 * 1024 bitrate, 44100 sampleRate. See AudioEncoder class for more info
 */
public class ExampleRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener {

  private RtmpBuilder rtmpBuilder;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_example_rtmp);
    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    button = (Button) findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtmp_url);
    rtmpBuilder = new RtmpBuilder(surfaceView, this);
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
        rtmpBuilder.stopStream();
        button.setText(getResources().getString(R.string.start_button));
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
  public void onClick(View view) {
    if (!rtmpBuilder.isStreaming()) {
      if (rtmpBuilder.prepareAudio() && rtmpBuilder.prepareVideo()) {
        button.setText(getResources().getString(R.string.stop_button));
        rtmpBuilder.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(getResources().getString(R.string.start_button));
      rtmpBuilder.stopStream();
    }
  }
}