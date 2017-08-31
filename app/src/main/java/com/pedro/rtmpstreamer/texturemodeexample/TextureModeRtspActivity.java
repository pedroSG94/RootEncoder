package com.pedro.rtmpstreamer.texturemodeexample;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.pedro.builder.rtsp.RtspBuilderSurfaceMode;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

/**
 * Unstable activity. See builder header.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class TextureModeRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener {

  private RtspBuilderSurfaceMode rtspBuilderSurfaceMode;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_texture_mode);
    TextureView textureView = (TextureView) findViewById(R.id.textureView);
    button = (Button) findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtsp);
    rtspBuilderSurfaceMode = new RtspBuilderSurfaceMode(textureView, Protocol.TCP, this);
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtspActivity.this, "Connection success", Toast.LENGTH_SHORT)
            .show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtspActivity.this, "Connection failed", Toast.LENGTH_SHORT)
            .show();
        rtspBuilderSurfaceMode.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    if (!rtspBuilderSurfaceMode.isStreaming()) {
      if (rtspBuilderSurfaceMode.prepareAudio() && rtspBuilderSurfaceMode.prepareVideo()) {
        button.setText(R.string.stop_button);
        rtspBuilderSurfaceMode.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(R.string.start_button);
      rtspBuilderSurfaceMode.stopStream();
    }
  }
}