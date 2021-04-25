package com.pedro.rtpstreamer.displayexample;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.rtpstreamer.R;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.DisplayBase}
 * {@link com.pedro.rtplibrary.rtmp.RtmpDisplay}
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DisplayActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener {

  private Button button;
  private EditText etUrl;
  private final int REQUEST_CODE_STREAM = 179; //random num
  private final int REQUEST_CODE_RECORD = 180; //random num
  private NotificationManager notificationManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_display);
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtmp);
    getInstance();

    if (DisplayService.Companion.isStreaming()) {
      button.setText(R.string.stop_button);
    } else {
      button.setText(R.string.start_button);
    }
  }

  private void getInstance() {
    DisplayService.Companion.init(this);
  }

  /**
   * This notification is to solve MediaProjection problem that only render surface if something
   * changed.
   * It could produce problem in some server like in Youtube that need send video and audio all time
   * to work.
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
  public void onConnectionStartedRtmp(String rtmpUrl) {
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
            .show();
        stopNotification();
        stopService(new Intent(DisplayActivity.this, DisplayService.class));
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
        Toast.makeText(DisplayActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(DisplayActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data != null && (requestCode == REQUEST_CODE_STREAM
        || requestCode == REQUEST_CODE_RECORD && resultCode == Activity.RESULT_OK)) {
      initNotification();
      DisplayService.Companion.setData(resultCode, data);
      Intent intent = new Intent(this, DisplayService.class);
      intent.putExtra("endpoint", etUrl.getText().toString());
      startService(intent);
    } else {
      Toast.makeText(this, "No permissions available", Toast.LENGTH_SHORT).show();
      button.setText(R.string.start_button);
    }
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.b_start_stop:
        if (!DisplayService.Companion.isStreaming()) {
          button.setText(R.string.stop_button);
          startActivityForResult(DisplayService.Companion.sendIntent(), REQUEST_CODE_STREAM);
        } else {
          button.setText(R.string.start_button);
          stopService(new Intent(DisplayActivity.this, DisplayService.class));
        }
        if (!DisplayService.Companion.isStreaming() && !DisplayService.Companion.isRecording()) {
          stopNotification();
        }
        break;
      default:
        break;
    }
  }
}
