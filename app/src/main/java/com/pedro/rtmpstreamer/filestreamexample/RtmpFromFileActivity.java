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

import com.pedro.builder.rtmp.RtmpBuilderFromFile;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtmpstreamer.constants.Constants;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import java.io.IOException;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtmpFromFileActivity extends AppCompatActivity
   implements ConnectCheckerRtmp, View.OnClickListener, VideoDecoderInterface {

  private RtmpBuilderFromFile rtmpBuilderFromFile;
  private Button button, bSelectFile;
  private EditText etUrl;
  private TextView tvFile;
  private String filePath = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_from_file);
    getSupportActionBar().setTitle(getIntent().getStringExtra(Constants.LABEL));
    button = (Button) findViewById(R.id.b_start_stop);
    bSelectFile = (Button) findViewById(R.id.b_select_file);
    button.setOnClickListener(this);
    bSelectFile.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    tvFile = (TextView) findViewById(R.id.tv_file);
    rtmpBuilderFromFile = new RtmpBuilderFromFile(this, this);
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpFromFileActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpFromFileActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtmpBuilderFromFile.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpFromFileActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpFromFileActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpFromFileActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
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
          if (!rtmpBuilderFromFile.isStreaming()) {
            try {
              if (rtmpBuilderFromFile.prepareVideo(filePath, 1200 * 1024)) {
                button.setText(R.string.stop_button);
                rtmpBuilderFromFile.startStream(etUrl.getText().toString());
              } else {
                button.setText(R.string.start_button);
                rtmpBuilderFromFile.stopStream();
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
            button.setText(R.string.start_button);
            rtmpBuilderFromFile.stopStream();
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

  @Override
  public void onVideoDecoderFinished() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (rtmpBuilderFromFile.isStreaming()) {
          button.setText(R.string.start_button);
          Toast.makeText(RtmpFromFileActivity.this, "Video stream finished", Toast.LENGTH_SHORT)
             .show();
          rtmpBuilderFromFile.stopStream();
        }
      }
    });
  }
}

