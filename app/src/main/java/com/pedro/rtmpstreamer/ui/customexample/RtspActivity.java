package com.pedro.rtmpstreamer.ui.customexample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
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
import com.pedro.rtmpstreamer.builders.RtspBuilder;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

public class RtspActivity extends AppCompatActivity
    implements Button.OnClickListener, ConnectCheckerRtsp {

  private Integer[] orientations = new Integer[] { 0, 90, 180, 270 };

  private RtspBuilder rtspBuilder;
  private SurfaceView surfaceView;
  private Button bStartStop;
  private EditText etUrl;
  //options menu
  private DrawerLayout drawerLayout;
  private NavigationView navigationView;
  private ActionBarDrawerToggle actionBarDrawerToggle;
  private RadioGroup rgChannel;
  private RadioButton rbTcp, rbUdp;
  private Spinner spResolution, spOrientation;
  private EditText etVideoBitrate, etFps, etAudioBitrate, etSampleRate;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_rtsp);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);

    surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    rtspBuilder = new RtspBuilder(surfaceView, Protocol.TCP, this);
    prepareOptionsMenuViews();

    etUrl = (EditText) findViewById(R.id.et_rtsp_url);
    bStartStop = (Button) findViewById(R.id.b_start_stop);
    Button switchCamera = (Button) findViewById(R.id.switch_camera);
    bStartStop.setOnClickListener(this);
    switchCamera.setOnClickListener(this);
  }

  private void prepareOptionsMenuViews() {
    drawerLayout = (DrawerLayout) findViewById(R.id.activity_rtsp);
    navigationView = (NavigationView) findViewById(R.id.nv_rtsp);
    actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.rtsp_streamer,
        R.string.rtsp_streamer) {

      public void onDrawerOpened(View drawerView) {
        actionBarDrawerToggle.syncState();
      }

      public void onDrawerClosed(View view) {
        actionBarDrawerToggle.syncState();
        rtspBuilder.setVideoBitrateOnFly(Integer.parseInt(etVideoBitrate.getText().toString()) * 1024);
      }
    };
    drawerLayout.addDrawerListener(actionBarDrawerToggle);
    //radiobuttons
    rbTcp = (RadioButton) navigationView.getMenu().findItem(R.id.rb_tcp).getActionView();
    rbUdp = (RadioButton) navigationView.getMenu().findItem(R.id.rb_udp).getActionView();
    rgChannel = (RadioGroup) navigationView.getMenu().findItem(R.id.channel).getActionView();
    rbTcp.setChecked(true);
    rbTcp.setOnClickListener(this);
    rbUdp.setOnClickListener(this);
    //spinners
    spResolution = (Spinner) navigationView.getMenu().findItem(R.id.sp_resolution).getActionView();
    spOrientation =
        (Spinner) navigationView.getMenu().findItem(R.id.sp_orientation).getActionView();

    ArrayAdapter<Integer> orientationAdapter =
        new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item);
    orientationAdapter.addAll(orientations);
    spOrientation.setAdapter(orientationAdapter);
    spOrientation.setSelection(0);

    ArrayAdapter<String> resolutionAdapter =
        new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item);
    resolutionAdapter.addAll(rtspBuilder.getResolutions());
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
  protected void onPostCreate(@Nullable Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    actionBarDrawerToggle.syncState();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_effects, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        if (!drawerLayout.isDrawerOpen(Gravity.START)) {
          drawerLayout.openDrawer(Gravity.START);
        } else {
          drawerLayout.closeDrawer(Gravity.START);
        }
        return true;
      case R.id.clear:
        rtspBuilder.setEffect(EffectManager.CLEAR);
        return true;
      case R.id.grey_scale:
        rtspBuilder.setEffect(EffectManager.GREYSCALE);
        return true;
      case R.id.sepia:
        rtspBuilder.setEffect(EffectManager.SEPIA);
        return true;
      case R.id.negative:
        rtspBuilder.setEffect(EffectManager.NEGATIVE);
        return true;
      case R.id.aqua:
        rtspBuilder.setEffect(EffectManager.AQUA);
        return true;
      case R.id.posterize:
        rtspBuilder.setEffect(EffectManager.POSTERIZE);
        return true;
      default:
        return false;
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.b_start_stop:
        if (!rtspBuilder.isStreaming()) {
          bStartStop.setText(getResources().getString(R.string.stop_button));
          if (rbTcp.isChecked()) {
            rtspBuilder = new RtspBuilder(surfaceView, Protocol.TCP, this);
          } else {
            rtspBuilder = new RtspBuilder(surfaceView, Protocol.UDP, this);
          }
          String resolution =
              rtspBuilder.getResolutions().get(spResolution.getSelectedItemPosition());
          int width = Integer.parseInt(resolution.split("X")[0]);
          int height = Integer.parseInt(resolution.split("X")[1]);

          if (rtspBuilder.prepareAudio(Integer.parseInt(etAudioBitrate.getText().toString()) * 1024,
              Integer.parseInt(etSampleRate.getText().toString()),
              rgChannel.getCheckedRadioButtonId() == R.id.rb_stereo) && rtspBuilder.prepareVideo(
              width, height, Integer.parseInt(etFps.getText().toString()),
              Integer.parseInt(etVideoBitrate.getText().toString()) * 1024,
              orientations[spOrientation.getSelectedItemPosition()])) {
            rtspBuilder.startStream(etUrl.getText().toString());
          } else {
            //If you see this all time when you start stream,
            //it is because your encoder device dont support the configuration
            //in video encoder maybe color format.
            //If you have more encoder go to VideoEncoder or AudioEncoder class,
            //change encoder and try
            Toast.makeText(this, "Error preparing stream, This device cant do it",
                Toast.LENGTH_SHORT).show();
            bStartStop.setText(getResources().getString(R.string.start_button));
          }
        } else {
          bStartStop.setText(getResources().getString(R.string.start_button));
          rtspBuilder.stopStream();
        }
        break;
      case R.id.switch_camera:
        rtspBuilder.switchCamera();
        break;
      //options menu
      case R.id.rb_tcp:
        if (rbUdp.isChecked()) {
          rbUdp.setChecked(false);
          rbTcp.setChecked(true);
        }
        break;
      case R.id.rb_udp:
        if (rbTcp.isChecked()) {
          rbTcp.setChecked(false);
          rbUdp.setChecked(true);
        }
        break;
      default:
        break;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (rtspBuilder.isStreaming()) {
      rtspBuilder.stopStream();
      bStartStop.setText(getResources().getString(R.string.start_button));
    }
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
        rtspBuilder.updateDestination();  //only for rtsp
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtspBuilder.stopStream();
        bStartStop.setText(getResources().getString(R.string.start_button));
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(RtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }
}
