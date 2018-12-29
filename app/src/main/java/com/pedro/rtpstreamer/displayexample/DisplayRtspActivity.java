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
import com.pedro.rtplibrary.rtsp.RtspDisplay;
import com.pedro.rtpstreamer.R;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.DisplayBase}
 * {@link com.pedro.rtplibrary.rtsp.RtspDisplay}
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DisplayRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener {

  private static RtspDisplay rtspDisplay;
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
    etUrl.setHint(R.string.hint_rtsp);
    rtspDisplay = getInstance();

    if (rtspDisplay.isStreaming()) {
      button.setText(R.string.stop_button);
    } else {
      button.setText(R.string.start_button);
    }
    if (rtspDisplay.isRecording()) {
      bRecord.setText(R.string.stop_record);
    } else {
      bRecord.setText(R.string.start_record);
    }
  }

  private RtspDisplay getInstance() {
    if (rtspDisplay == null) {
      return new RtspDisplay(this, false, this);
    } else {
      return rtspDisplay;
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
    if (notificationManager != null) notificationManager.notify(1234, notificationBuilder.build());
  }

  private void stopNotification() {
    if (notificationManager != null) {
      notificationManager.cancel(1234);
    }
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
            .show();
        stopNotification();
        rtspDisplay.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE_STREAM
        || requestCode == REQUEST_CODE_RECORD && resultCode == Activity.RESULT_OK) {
      if (rtspDisplay.prepareAudio() && rtspDisplay.prepareVideo()) {
        initNotification();
        rtspDisplay.setIntentResult(resultCode, data);
        if (requestCode == REQUEST_CODE_STREAM) {
          rtspDisplay.startStream(etUrl.getText().toString());
        } else {
          try {
            rtspDisplay.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          } catch (IOException e) {
            rtspDisplay.stopRecord();
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
        if (!rtspDisplay.isStreaming()) {
          if (rtspDisplay.isRecording()) {
            button.setText(R.string.stop_button);
            rtspDisplay.startStream(etUrl.getText().toString());
          } else {
            button.setText(R.string.stop_button);
            startActivityForResult(rtspDisplay.sendIntent(), REQUEST_CODE_STREAM);
          }
        } else {
          button.setText(R.string.start_button);
          rtspDisplay.stopStream();
        }
        if (!rtspDisplay.isStreaming() && !rtspDisplay.isRecording()) stopNotification();
        break;
      case R.id.b_record:
        if (!rtspDisplay.isRecording()) {
          try {
            if (!folder.exists()) {
              folder.mkdir();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            currentDateAndTime = sdf.format(new Date());
            if (!rtspDisplay.isStreaming()) {
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
              startActivityForResult(rtspDisplay.sendIntent(), REQUEST_CODE_RECORD);
            } else {
              rtspDisplay.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            }
          } catch (IOException e) {
            rtspDisplay.stopRecord();
            bRecord.setText(R.string.start_record);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        } else {
          rtspDisplay.stopRecord();
          bRecord.setText(R.string.start_record);
          Toast.makeText(this,
              "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
              Toast.LENGTH_SHORT).show();
          currentDateAndTime = "";
        }
        if (!rtspDisplay.isStreaming() && !rtspDisplay.isRecording()) stopNotification();
        break;
      default:
        break;
    }
  }
}
