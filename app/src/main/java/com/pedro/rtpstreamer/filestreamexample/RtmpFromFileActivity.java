package com.pedro.rtpstreamer.filestreamexample;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.rtplibrary.rtmp.RtmpFromFile;
import com.pedro.rtpstreamer.R;
import com.pedro.rtpstreamer.utils.PathUtils;
import java.io.IOException;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.FromFileBase}
 * {@link com.pedro.rtplibrary.rtmp.RtmpFromFile}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtmpFromFileActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener, VideoDecoderInterface,
    AudioDecoderInterface {

  private RtmpFromFile rtmpFromFile;
  private Button button, bSelectFile;
  private EditText etUrl;
  private TextView tvFile;

  //default file to stream, audio from web normally use mime audio/mpeg-L1, audio/mpeg-L2, audio/x-ms-wma or audio/x-ima that should be unsupported
  private String filePath =
      "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_from_file);
    button = findViewById(R.id.b_start_stop);
    bSelectFile = findViewById(R.id.b_select_file);
    button.setOnClickListener(this);
    bSelectFile.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    tvFile = findViewById(R.id.tv_file);
    rtmpFromFile = new RtmpFromFile(this, this, this);
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
  public void onConnectionFailedRtmp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpFromFileActivity.this, "Connection failed. " + reason,
            Toast.LENGTH_SHORT).show();
        rtmpFromFile.stopStream();
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
        if (!rtmpFromFile.isStreaming()) {
          try {
            if (rtmpFromFile.prepareVideo(filePath, 1200 * 1024) && rtmpFromFile.prepareAudio(
                filePath, 64 * 1024)) {
              button.setText(R.string.stop_button);
              rtmpFromFile.startStream(etUrl.getText().toString());
            } else {
              button.setText(R.string.start_button);
              rtmpFromFile.stopStream();
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
          rtmpFromFile.stopStream();
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

  }

  @Override
  public void onAudioDecoderFinished() {

  }
}

