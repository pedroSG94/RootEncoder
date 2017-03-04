package com.pedro.rtmpstreamer.ui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtmpstreamer.builders.RtmpBuilder;
import net.ossrs.rtmp.ConnectCheckerRtmp;

public class RtmpActivity extends AppCompatActivity
    implements Button.OnClickListener, ConnectCheckerRtmp {

  private RtmpBuilder rtmpBuilder;
  private Button bStartStop;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_rtmp);

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    rtmpBuilder = new RtmpBuilder(surfaceView, this);

    etUrl = (EditText) findViewById(R.id.et_rtmp_url);
    bStartStop = (Button) findViewById(R.id.b_start_stop);
    Button switchCamera = (Button) findViewById(R.id.switch_camera);
    bStartStop.setOnClickListener(this);
    switchCamera.setOnClickListener(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_effects, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.clear:
        rtmpBuilder.setEffect(EffectManager.CLEAR);
        return true;
      case R.id.grey_scale:
        rtmpBuilder.setEffect(EffectManager.GREYSCALE);
        return true;
      case R.id.sepia:
        rtmpBuilder.setEffect(EffectManager.SEPIA);
        return true;
      case R.id.negative:
        rtmpBuilder.setEffect(EffectManager.NEGATIVE);
        return true;
      case R.id.aqua:
        rtmpBuilder.setEffect(EffectManager.AQUA);
        return true;
      case R.id.posterize:
        rtmpBuilder.setEffect(EffectManager.POSTERIZE);
        return true;
      case R.id.solarize:
        rtmpBuilder.setEffect(EffectManager.SOLARIZE);
        return true;
      case R.id.whiteboard:
        rtmpBuilder.setEffect(EffectManager.WHITEBOARD);
        return true;
      case R.id.blackboard:
        rtmpBuilder.setEffect(EffectManager.BLACKBOARD);
        return true;
      default:
        return false;
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.b_start_stop:
        if (!rtmpBuilder.isStreaming()) {
          bStartStop.setText(getResources().getString(R.string.stop_button));
          rtmpBuilder.prepareAudio();
          rtmpBuilder.prepareVideo();
          rtmpBuilder.startStream(etUrl.getText().toString());
        } else {
          bStartStop.setText(getResources().getString(R.string.start_button));
          rtmpBuilder.stopStream();
        }
        break;
      case R.id.switch_camera:
        rtmpBuilder.switchCamera();
        break;
      default:
        break;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (rtmpBuilder.isStreaming()) {
      rtmpBuilder.stopStream();
      bStartStop.setText(getResources().getString(R.string.start_button));
    }
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtmpBuilder.stopStream();
        bStartStop.setText(getResources().getString(R.string.start_button));
      }
    });
  }

  @Override
  public void onDisconnectRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }
}
