package com.pedro.rtmpstreamer.filestreamexample;

import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.builder.RtspBuilderFromFile;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtspFromFileActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener {

  private RtspBuilderFromFile rtspBuilderFromFile;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_rtsp_from_file);
    button = (Button) findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtsp_url);
    rtspBuilderFromFile = new RtspBuilderFromFile(Protocol.UDP, this);
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspFromFileActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspFromFileActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtspBuilderFromFile.stopStream();
        button.setText(getResources().getString(R.string.start_button));
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspFromFileActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspFromFileActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspFromFileActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/hola.mp4";
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      if (!rtspBuilderFromFile.isStreaming()) {
        if (rtspBuilderFromFile.prepareVideo(filePath, 1200 * 1024)
            && rtspBuilderFromFile.prepareAudio(filePath)) {
          button.setText(getResources().getString(R.string.stop_button));
          rtspBuilderFromFile.startStream(etUrl.getText().toString());
        }
      } else {
        button.setText(getResources().getString(R.string.start_button));
        rtspBuilderFromFile.stopStream();
      }
    }
  }
}
