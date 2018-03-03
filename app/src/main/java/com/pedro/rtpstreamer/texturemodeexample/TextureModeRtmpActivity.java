package com.pedro.rtpstreamer.texturemodeexample;

import android.graphics.SurfaceTexture;
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
import com.pedro.rtpstreamer.R;
import com.pedro.rtplibrary.rtmp.RtmpCamera2;
import com.pedro.rtplibrary.view.AutoFitTextureView;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera2Base}
 * {@link com.pedro.rtplibrary.rtmp.RtmpCamera2}
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class TextureModeRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener, TextureView.SurfaceTextureListener {

  private RtmpCamera2 rtmpCamera2;
  private AutoFitTextureView textureView;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_texture_mode);
    textureView = findViewById(R.id.textureView);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    rtmpCamera2 = new RtmpCamera2(textureView, this);
    textureView.setSurfaceTextureListener(this);
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtmpActivity.this, "Connection success", Toast.LENGTH_SHORT)
            .show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtmpActivity.this, "Connection failed. " + reason,
            Toast.LENGTH_SHORT).show();
        rtmpCamera2.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtmpActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtmpActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    if (!rtmpCamera2.isStreaming()) {
      if (rtmpCamera2.prepareAudio() && rtmpCamera2.prepareVideo()) {
        button.setText(R.string.stop_button);
        rtmpCamera2.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(R.string.start_button);
      rtmpCamera2.stopStream();
    }
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
    textureView.setAspectRatio(480, 640);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
    rtmpCamera2.startPreview();
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
    if (rtmpCamera2.isStreaming()) rtmpCamera2.stopStream();
    rtmpCamera2.stopPreview();
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

  }
}