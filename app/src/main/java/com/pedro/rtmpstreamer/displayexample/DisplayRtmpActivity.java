package com.pedro.rtmpstreamer.displayexample;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.pedro.rtplibrary.rtmp.RtmpDisplay;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtmpstreamer.constants.Constants;

import net.ossrs.rtmp.ConnectCheckerRtmp;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DisplayRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener {

  private RtmpDisplay rtmpDisplay;
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
    etUrl.setHint(R.string.hint_rtmp);
    rtmpDisplay = new RtmpDisplay(this, this);
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtmpActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtmpActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtmpDisplay.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtmpActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtmpActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE) {
      if (rtmpDisplay.prepareAudio() && rtmpDisplay.prepareVideo()) {
        if (Build.VERSION.SDK_INT >= 21) {
          rtmpDisplay.startStream(etUrl.getText().toString(), resultCode, data);
        }
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    }
  }

  @Override
  public void onClick(View view) {
    if (!rtmpDisplay.isStreaming()) {
      button.setText(R.string.stop_button);
      startActivityForResult(rtmpDisplay.sendIntent(), REQUEST_CODE);
    } else {
      button.setText(R.string.start_button);
      rtmpDisplay.stopStream();
    }
  }
}
