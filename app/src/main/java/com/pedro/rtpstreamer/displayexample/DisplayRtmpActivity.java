package com.pedro.rtpstreamer.displayexample;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.rtplibrary.rtmp.RtmpDisplay;
import com.pedro.rtpstreamer.R;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.DisplayBase}
 * {@link com.pedro.rtplibrary.rtmp.RtmpDisplay}
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DisplayRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener {

  private static RtmpDisplay rtmpDisplay;
  private Button button;
  private Button bRecord;
  private EditText etUrl;
  private final int REQUEST_CODE_STREAM = 179; //random num
  private final int REQUEST_CODE_RECORD = 180; //random num

  private String currentDateAndTime = "";
  private File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
      + "/rtmp-rtsp-stream-client-java");
  private NotificationManager notificationManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_display);
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    bRecord = findViewById(R.id.b_record);
    bRecord.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    rtmpDisplay = getInstance();

    if (rtmpDisplay.isStreaming()) {
      button.setText(R.string.stop_button);
    } else {
      button.setText(R.string.start_button);
    }
    if (rtmpDisplay.isRecording()) {
      bRecord.setText(R.string.stop_record);
    } else {
      bRecord.setText(R.string.start_record);
    }
  }

  private RtmpDisplay getInstance() {
    if (rtmpDisplay == null) {
      return new RtmpDisplay(this, false, this);
    } else {
      return rtmpDisplay;
    }
  }

  /**
   * This notification is to solve MediaProjection problem that only render surface if something changed.
   * It could produce problem in some server like in Youtube that need send video and audio all time to work.
   */
  private void initNotification() {
    Notification.Builder notificationBuilder =
        new Notification.Builder(this).setSmallIcon(R.drawable.notification_anim)
            .setContentTitle("Streaming")
            .setContentText("Display mode stream")
            .setTicker("Stream in progress");
    notificationBuilder.setAutoCancel(true);
    if (notificationManager != null) notificationManager.notify(12345, notificationBuilder.build());
  }

  private void stopNotification() {
    if (notificationManager != null) {
      notificationManager.cancel(12345);
    }
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
  public void onConnectionFailedRtmp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtmpActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
            .show();
        stopNotification();
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
    if (requestCode == REQUEST_CODE_STREAM
        || requestCode == REQUEST_CODE_RECORD && resultCode == Activity.RESULT_OK) {
      if (rtmpDisplay.prepareAudio() && rtmpDisplay.prepareVideo()) {
        initNotification();
        rtmpDisplay.setIntentResult(resultCode, data);
        if (requestCode == REQUEST_CODE_STREAM) {
          rtmpDisplay.startStream(etUrl.getText().toString());
        } else {
          try {
            rtmpDisplay.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          } catch (IOException e) {
            rtmpDisplay.stopRecord();
            bRecord.setText(R.string.start_record);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        }
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      Toast.makeText(this, "No permissions available", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.b_start_stop:
        if (!rtmpDisplay.isStreaming()) {
          if (rtmpDisplay.isRecording()) {
            button.setText(R.string.stop_button);
            rtmpDisplay.startStream(etUrl.getText().toString());
          } else {
            button.setText(R.string.stop_button);
            startActivityForResult(rtmpDisplay.sendIntent(), REQUEST_CODE_STREAM);
          }
        } else {
          button.setText(R.string.start_button);
          rtmpDisplay.stopStream();
        }
        if (!rtmpDisplay.isStreaming() && !rtmpDisplay.isRecording()) stopNotification();
        break;
      case R.id.b_record:
        if (!rtmpDisplay.isRecording()) {
          try {
            if (!folder.exists()) {
              folder.mkdir();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            currentDateAndTime = sdf.format(new Date());
            if (!rtmpDisplay.isStreaming()) {
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
              startActivityForResult(rtmpDisplay.sendIntent(), REQUEST_CODE_RECORD);
            } else {
              rtmpDisplay.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            }
          } catch (IOException e) {
            rtmpDisplay.stopRecord();
            bRecord.setText(R.string.start_record);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        } else {
          rtmpDisplay.stopRecord();
          bRecord.setText(R.string.start_record);
          Toast.makeText(this,
              "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
              Toast.LENGTH_SHORT).show();
          currentDateAndTime = "";
        }
        if (!rtmpDisplay.isStreaming() && !rtmpDisplay.isRecording()) stopNotification();
        break;
      default:
        break;
    }
  }
}
