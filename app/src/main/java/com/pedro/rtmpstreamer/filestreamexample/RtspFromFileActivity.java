package com.pedro.rtmpstreamer.filestreamexample;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.pedro.builder.RtspBuilderFromFile;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtspFromFileActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener {

  private RtspBuilderFromFile rtspBuilderFromFile;
  private Button button, bSelectFile;
  private EditText etUrl;
  private TextView tvFile;
  private String filePath = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_rtsp_from_file);
    button = (Button) findViewById(R.id.b_start_stop);
    bSelectFile = (Button) findViewById(R.id.b_select_file);
    button.setOnClickListener(this);
    bSelectFile.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtsp_url);
    tvFile = (TextView) findViewById(R.id.tv_file);
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
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 5 && data != null) {
      filePath = PathUtils.getPath(this, data.getData());
      Toast.makeText(this, filePath, Toast.LENGTH_SHORT).show();
      tvFile.setText(filePath);
    }
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.b_start_stop:
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          if (!rtspBuilderFromFile.isStreaming()) {
            try {
              if (rtspBuilderFromFile.prepareVideo(filePath, 1200 * 1024)
                  && rtspBuilderFromFile.prepareAudio(filePath)) {
                button.setText(getResources().getString(R.string.stop_button));
                rtspBuilderFromFile.startStream(etUrl.getText().toString());
              } else {
                button.setText(getResources().getString(R.string.start_button));
                rtspBuilderFromFile.stopStream();
                /*This error could be 2 things.
                 Your device cant decode or encode this file or
                 the file is not supported for the library.
                The file need has h264 video codec and acc audio codec*/
                Toast.makeText(this, "Error: unsupported file", Toast.LENGTH_SHORT).show();
              }
            } catch (IOException e) {
              //Normally this error is for file not found or read permissions
              Toast.makeText(this, "Error: file not found", Toast.LENGTH_SHORT).show();
            }
          } else {
            button.setText(getResources().getString(R.string.start_button));
            rtspBuilderFromFile.stopStream();
          }
        }
        break;
      case R.id.b_select_file:
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/mp4");
        startActivityForResult(intent, 5);
        break;
      default:
        break;
    }
  }
}
