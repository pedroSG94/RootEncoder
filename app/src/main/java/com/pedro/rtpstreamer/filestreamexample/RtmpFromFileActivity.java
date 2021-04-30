package com.pedro.rtpstreamer.filestreamexample;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.pedro.encoder.input.decoder.AudioDecoderInterface;
import com.pedro.encoder.input.decoder.VideoDecoderInterface;
import com.pedro.rtplibrary.rtmp.RtmpFromFile;
import com.pedro.rtpstreamer.R;
import com.pedro.rtpstreamer.utils.PathUtils;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.FromFileBase}
 * {@link com.pedro.rtplibrary.rtmp.RtmpFromFile}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtmpFromFileActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener, VideoDecoderInterface,
    AudioDecoderInterface, SeekBar.OnSeekBarChangeListener {

  private RtmpFromFile rtmpFromFile;
  private Button button, bSelectFile, bReSync, bRecord;
  private SeekBar seekBar;
  private EditText etUrl;
  private TextView tvFile;
  private String filePath = "";
  private boolean touching = false;

  private String currentDateAndTime = "";
  private File folder;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_from_file);
    folder = PathUtils.getRecordPath(this);
    button = findViewById(R.id.b_start_stop);
    bSelectFile = findViewById(R.id.b_select_file);
    button.setOnClickListener(this);
    bSelectFile.setOnClickListener(this);
    bReSync = findViewById(R.id.b_re_sync);
    bReSync.setOnClickListener(this);
    bRecord = findViewById(R.id.b_record);
    bRecord.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    seekBar = findViewById(R.id.seek_bar);
    seekBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
    tvFile = findViewById(R.id.tv_file);
    rtmpFromFile = new RtmpFromFile(this, this, this);
    seekBar.setOnSeekBarChangeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (rtmpFromFile.isRecording()) {
      rtmpFromFile.stopRecord();
      bRecord.setText(R.string.start_record);
    }
    if (rtmpFromFile.isStreaming()) {
      rtmpFromFile.stopStream();
      button.setText(getResources().getString(R.string.start_button));
    }
  }

  @Override
  public void onConnectionStartedRtmp(String rtmpUrl) {
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
  public void onNewBitrateRtmp(long bitrate) {

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
            if (!rtmpFromFile.isRecording()) {
              if (prepare()) {
                button.setText(R.string.stop_button);
                rtmpFromFile.startStream(etUrl.getText().toString());
                seekBar.setMax(Math.max((int) rtmpFromFile.getVideoDuration(),
                    (int) rtmpFromFile.getAudioDuration()));
                updateProgress();
              } else {
                button.setText(R.string.start_button);
                rtmpFromFile.stopStream();
                /*This error could be 2 things.
                 Your device cant decode or encode this file or
                 the file is not supported for the library.
                The file need has h264 video codec and acc audio codec*/
                Toast.makeText(this, "Error: unsupported file", Toast.LENGTH_SHORT).show();
              }
            } else {
              button.setText(R.string.stop_button);
              rtmpFromFile.startStream(etUrl.getText().toString());
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
        intent.setType("*/*");
        startActivityForResult(intent, 5);
        break;
      //sometimes async is produced when you move in file several times
      case R.id.b_re_sync:
        rtmpFromFile.reSyncFile();
        break;
      case R.id.b_record:
        if (!rtmpFromFile.isRecording()) {
          try {
            if (!folder.exists()) {
              folder.mkdir();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            currentDateAndTime = sdf.format(new Date());
            if (!rtmpFromFile.isStreaming()) {
              if (prepare()) {
                rtmpFromFile.startRecord(
                    folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
                seekBar.setMax(Math.max((int) rtmpFromFile.getVideoDuration(),
                    (int) rtmpFromFile.getAudioDuration()));
                updateProgress();
                bRecord.setText(R.string.stop_record);
                Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
              } else {
                Toast.makeText(this, "Error preparing stream, This device cant do it",
                    Toast.LENGTH_SHORT).show();
              }
            } else {
              rtmpFromFile.startRecord(
                  folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            }
          } catch (IOException e) {
            rtmpFromFile.stopRecord();
            bRecord.setText(R.string.start_record);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        } else {
          rtmpFromFile.stopRecord();
          bRecord.setText(R.string.start_record);
          Toast.makeText(this,
              "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
              Toast.LENGTH_SHORT).show();
          currentDateAndTime = "";
        }
        break;
      default:
        break;
    }
  }

  private boolean prepare() throws IOException {
    boolean result = rtmpFromFile.prepareVideo(filePath);
    result |= rtmpFromFile.prepareAudio(filePath);
    return result;
  }

  private void updateProgress() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (rtmpFromFile.isStreaming() || rtmpFromFile.isRecording()) {
          try {
            Thread.sleep(1000);
            if (!touching) {
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  seekBar.setProgress(Math.max((int) rtmpFromFile.getVideoTime(),
                      (int) rtmpFromFile.getAudioTime()));
                }
              });
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }).start();
  }

  @Override
  public void onVideoDecoderFinished() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (rtmpFromFile.isRecording()) {
          rtmpFromFile.stopRecord();
          bRecord.setText(R.string.start_record);
          Toast.makeText(RtmpFromFileActivity.this,
              "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
              Toast.LENGTH_SHORT).show();
          currentDateAndTime = "";
        }
        if (rtmpFromFile.isStreaming()) {
          button.setText(R.string.start_button);
          Toast.makeText(RtmpFromFileActivity.this, "Video stream finished", Toast.LENGTH_SHORT)
              .show();
          rtmpFromFile.stopStream();
        }
      }
    });
  }

  @Override
  public void onAudioDecoderFinished() {

  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    touching = true;
  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {
    if (rtmpFromFile.isStreaming()) rtmpFromFile.moveTo(seekBar.getProgress());
    touching = false;
  }
}

