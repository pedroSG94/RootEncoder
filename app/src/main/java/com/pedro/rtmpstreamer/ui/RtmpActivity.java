package com.pedro.rtmpstreamer.ui;

import android.support.design.widget.NavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtmpstreamer.builders.RtmpBuilder;
import net.ossrs.rtmp.ConnectCheckerRtmp;

public class RtmpActivity extends AppCompatActivity
    implements Button.OnClickListener, ConnectCheckerRtmp {

  private Integer[] orientations = new Integer[] { 0, 90, 180, 270 };

  private RtmpBuilder rtmpBuilder;
  private Button bStartStop;
  private EditText etUrl;
  //options menu
  private NavigationView navigationView;
  private RadioGroup rgChannel;
  private Spinner spResolution, spOrientation;
  private EditText etVideoBitrate, etFps, etAudioBitrate, etSampleRate;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_rtmp);

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    rtmpBuilder = new RtmpBuilder(surfaceView, this);
    prepareOptionsMenuViews();

    etUrl = (EditText) findViewById(R.id.et_rtmp_url);
    bStartStop = (Button) findViewById(R.id.b_start_stop);
    Button switchCamera = (Button) findViewById(R.id.switch_camera);
    bStartStop.setOnClickListener(this);
    switchCamera.setOnClickListener(this);
  }

  private void prepareOptionsMenuViews() {
    navigationView = (NavigationView) findViewById(R.id.nv_rtmp);
    //radiobuttons
    RadioButton rbTcp =
        (RadioButton) navigationView.getMenu().findItem(R.id.rb_tcp).getActionView();
    rgChannel = (RadioGroup) navigationView.getMenu().findItem(R.id.channel).getActionView();
    rbTcp.setChecked(true);
    //spinners
    spResolution = (Spinner) navigationView.getMenu().findItem(R.id.sp_resolution).getActionView();
    spOrientation =
        (Spinner) navigationView.getMenu().findItem(R.id.sp_orientation).getActionView();

    ArrayAdapter<Integer> orientationAdapter =
        new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item);
    orientationAdapter.addAll(orientations);
    spOrientation.setAdapter(orientationAdapter);
    spOrientation.setSelection(1);

    ArrayAdapter<String> resolutionAdapter =
        new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item);
    resolutionAdapter.addAll(rtmpBuilder.getResolutions());
    spResolution.setAdapter(resolutionAdapter);
    //edittexts
    etVideoBitrate =
        (EditText) navigationView.getMenu().findItem(R.id.et_video_bitrate).getActionView();
    etFps = (EditText) navigationView.getMenu().findItem(R.id.et_fps).getActionView();
    etAudioBitrate =
        (EditText) navigationView.getMenu().findItem(R.id.et_audio_bitrate).getActionView();
    etSampleRate = (EditText) navigationView.getMenu().findItem(R.id.et_samplerate).getActionView();
    etVideoBitrate.setText("2500");
    etFps.setText("30");
    etAudioBitrate.setText("128");
    etSampleRate.setText("44100");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_effects, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.clear:
        rtmpBuilder.setEffect(EffectManager.CLEAR);
        return true;
      case R.id.grey_scale:
        rtmpBuilder.setEffect(EffectManager.GREYSCALE);
        return true;
      case R.id.sepia:
        rtmpBuilder.setEffect(EffectManager.SEPIA);
        return true;
      case R.id.negative:
        rtmpBuilder.setEffect(EffectManager.NEGATIVE);
        return true;
      case R.id.aqua:
        rtmpBuilder.setEffect(EffectManager.AQUA);
        return true;
      case R.id.posterize:
        rtmpBuilder.setEffect(EffectManager.POSTERIZE);
        return true;
      case R.id.solarize:
        rtmpBuilder.setEffect(EffectManager.SOLARIZE);
        return true;
      case R.id.whiteboard:
        rtmpBuilder.setEffect(EffectManager.WHITEBOARD);
        return true;
      case R.id.blackboard:
        rtmpBuilder.setEffect(EffectManager.BLACKBOARD);
        return true;
      default:
        return false;
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.b_start_stop:
        if (!rtmpBuilder.isStreaming()) {
          bStartStop.setText(getResources().getString(R.string.stop_button));
          rtmpBuilder.prepareAudio(Integer.parseInt(etAudioBitrate.getText().toString()) * 1024,
              Integer.parseInt(etSampleRate.getText().toString()),
              rgChannel.getCheckedRadioButtonId() == R.id.rb_stereo);
          String resolution =
              rtmpBuilder.getResolutions().get(spResolution.getSelectedItemPosition());
          int width = Integer.parseInt(resolution.split("X")[0]);
          int height = Integer.parseInt(resolution.split("X")[1]);
          rtmpBuilder.prepareVideo(width, height, Integer.parseInt(etFps.getText().toString()),
              Integer.parseInt(etVideoBitrate.getText().toString()) * 1024,
              orientations[spOrientation.getSelectedItemPosition()]);
          rtmpBuilder.startStream(etUrl.getText().toString());
        } else {
          bStartStop.setText(getResources().getString(R.string.start_button));
          rtmpBuilder.stopStream();
        }
        break;
      case R.id.switch_camera:
        rtmpBuilder.switchCamera();
        break;
      default:
        break;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (rtmpBuilder.isStreaming()) {
      rtmpBuilder.stopStream();
      bStartStop.setText(getResources().getString(R.string.start_button));
    }
  }

  @Override
  public void onConnectionSuccessRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtmpBuilder.stopStream();
        bStartStop.setText(getResources().getString(R.string.start_button));
      }
    });
  }

  @Override
  public void onDisconnectRtmp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }
}
