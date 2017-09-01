package com.pedro.rtmpstreamer.displayexample;

import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.rtplibrary.rtsp.RtspDisplay;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DisplayRtspActivity extends AppCompatActivity implements ConnectCheckerRtsp, View.OnClickListener {

  private RtspDisplay rtspBuilderDisplay;
  private Button button;
  private EditText etUrl;
  private final int REQUEST_CODE = 1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_example);
    button = (Button) findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtsp);
    rtspBuilderDisplay = new RtspDisplay(this, Protocol.TCP, this);
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtspBuilderDisplay.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE) {
      if (rtspBuilderDisplay.prepareAudio() && rtspBuilderDisplay.prepareVideo()) {
        if (Build.VERSION.SDK_INT >= 21) {
          rtspBuilderDisplay.startStream(etUrl.getText().toString(), resultCode, data);
        }
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    }
  }

  @Override
  public void onClick(View view) {
    if (!rtspBuilderDisplay.isStreaming()) {
      button.setText(R.string.stop_button);
      startActivityForResult(rtspBuilderDisplay.sendIntent(), REQUEST_CODE);
    } else {
      button.setText(R.string.start_button);
      rtspBuilderDisplay.stopStream();
    }
  }
}
