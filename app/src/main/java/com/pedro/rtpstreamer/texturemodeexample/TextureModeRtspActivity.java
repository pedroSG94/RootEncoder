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
import com.pedro.rtplibrary.rtsp.RtspCamera2;
import com.pedro.rtplibrary.view.AutoFitTextureView;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera2Base}
 * {@link com.pedro.rtplibrary.rtsp.RtspCamera2}
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class TextureModeRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener, TextureView.SurfaceTextureListener {

  private RtspCamera2 rtspCamera2;
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
    etUrl.setHint(R.string.hint_rtsp);
    rtspCamera2 = new RtspCamera2(textureView, this);
    textureView.setSurfaceTextureListener(this);
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
  public void onConnectionFailedRtsp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(TextureModeRtspActivity.this, "Connection failed. " + reason,
            Toast.LENGTH_SHORT).show();
        rtspCamera2.stopStream();
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
    if (!rtspCamera2.isStreaming()) {
      if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
        button.setText(R.string.stop_button);
        rtspCamera2.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(R.string.start_button);
      rtspCamera2.stopStream();
    }
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
    textureView.setAspectRatio(480, 640);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
    rtspCamera2.startPreview();
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
    if (rtspCamera2.isStreaming()) rtspCamera2.stopStream();
    rtspCamera2.stopPreview();
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

  }

  @Override
  public void onPointerCaptureChanged(boolean hasCapture) {

  }
}
