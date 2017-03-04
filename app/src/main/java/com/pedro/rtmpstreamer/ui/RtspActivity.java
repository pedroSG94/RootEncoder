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
import com.pedro.rtmpstreamer.builders.RtspBuilder;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

public class RtspActivity extends AppCompatActivity
    implements Button.OnClickListener, ConnectCheckerRtsp {

  private RtspBuilder rtspBuilder;
  private Button bStartStop;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_rtsp);

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    rtspBuilder = new RtspBuilder(surfaceView, Protocol.TCP, this);

    etUrl = (EditText) findViewById(R.id.et_rtsp_url);
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
        rtspBuilder.setEffect(EffectManager.CLEAR);
        return true;
      case R.id.grey_scale:
        rtspBuilder.setEffect(EffectManager.GREYSCALE);
        return true;
      case R.id.sepia:
        rtspBuilder.setEffect(EffectManager.SEPIA);
        return true;
      case R.id.negative:
        rtspBuilder.setEffect(EffectManager.NEGATIVE);
        return true;
      case R.id.aqua:
        rtspBuilder.setEffect(EffectManager.AQUA);
        return true;
      case R.id.posterize:
        rtspBuilder.setEffect(EffectManager.POSTERIZE);
        return true;
      case R.id.solarize:
        rtspBuilder.setEffect(EffectManager.SOLARIZE);
        return true;
      case R.id.whiteboard:
        rtspBuilder.setEffect(EffectManager.WHITEBOARD);
        return true;
      case R.id.blackboard:
        rtspBuilder.setEffect(EffectManager.BLACKBOARD);
        return true;
      default:
        return false;
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.b_start_stop:
        if (!rtspBuilder.isStreaming()) {
          bStartStop.setText(getResources().getString(R.string.stop_button));
          rtspBuilder.prepareAudio();
          rtspBuilder.prepareVideo();
          rtspBuilder.startStream(etUrl.getText().toString());
        } else {
          bStartStop.setText(getResources().getString(R.string.start_button));
          rtspBuilder.stopStream();
        }
        break;
      case R.id.switch_camera:
        rtspBuilder.switchCamera();
        break;
      default:
        break;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (rtspBuilder.isStreaming()) {
      rtspBuilder.stopStream();
      bStartStop.setText(getResources().getString(R.string.start_button));
    }
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
        rtspBuilder.updateDestination();  //only for rtsp
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtspBuilder.stopStream();
        bStartStop.setText(getResources().getString(R.string.start_button));
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }
}
