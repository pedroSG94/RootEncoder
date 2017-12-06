package com.pedro.rtmpstreamer.openglexample;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtplibrary.rtsp.RtspCamera1;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener {

  private RtspCamera1 rtspCamera1;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_open_gl);
    OpenGlView openGlView = findViewById(R.id.surfaceView);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtsp);
    rtspCamera1 = new RtspCamera1(openGlView, this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.gl_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (rtspCamera1.isStreaming()) {
      switch (item.getItemId()) {
        case R.id.text:
          setTextToStream();
          return true;
        case R.id.image:
          setImageToStream();
          return true;
        case R.id.gif:
          setGifToStream();
          return true;
        case R.id.clear:
          rtspCamera1.clearStreamObject();
          return true;
        default:
          return false;
      }
    } else {
      return false;
    }
  }

  private void setTextToStream() {
    try {
      TextStreamObject textStreamObject = new TextStreamObject();
      textStreamObject.load("Hello world", 22, Color.RED);
      rtspCamera1.setTextStreamObject(textStreamObject);
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void setImageToStream() {
    try {
      ImageStreamObject imageStreamObject = new ImageStreamObject();
      imageStreamObject.load(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
      rtspCamera1.setImageStreamObject(imageStreamObject);
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void setGifToStream() {
    try {
      GifStreamObject gifStreamObject = new GifStreamObject();
      gifStreamObject.load(getResources().openRawResource(R.raw.banana));
      rtspCamera1.setGifStreamObject(gifStreamObject);
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
            .show();
        rtspCamera1.stopStream();
        rtspCamera1.stopPreview();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    if (!rtspCamera1.isStreaming()) {
      if (rtspCamera1.prepareAudio() && rtspCamera1.prepareVideo()) {
        button.setText(R.string.stop_button);
        rtspCamera1.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(R.string.start_button);
      rtspCamera1.stopStream();
      rtspCamera1.stopPreview();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (rtspCamera1.isStreaming()) {
      rtspCamera1.stopStream();
      rtspCamera1.stopPreview();
    }
  }
}
