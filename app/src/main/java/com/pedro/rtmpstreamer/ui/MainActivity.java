package com.pedro.rtmpstreamer.ui;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.pedro.rtmpstreamer.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Button bRtmp = (Button) findViewById(R.id.b_rtmp);
    Button bRtsp = (Button) findViewById(R.id.b_rtsp);
    bRtmp.setOnClickListener(this);
    bRtsp.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.b_rtmp:
        startActivity(new Intent(this, RtmpActivity.class));
        break;
      case R.id.b_rtsp:
        startActivity(new Intent(this, RtspActivity.class));
        break;
      default:
        break;
    }
  }
}
