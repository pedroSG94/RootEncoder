package com.pedro.rtmpstreamer;

import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.pedro.rtmpstreamer.input.video.EffectManager;
import com.pedro.rtmpstreamer.utils.RtmpBuilder;

import net.ossrs.rtmp.ConnectChecker;

public class MainActivity extends AppCompatActivity implements Button.OnClickListener, ConnectChecker {

    private Button button;
    private String url = "rtmp://yourendpoint";
    private RtmpBuilder rtmpBuilder;
    private Button switchCamera, lantern;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        switchCamera = (Button) findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(this);
        lantern = (Button) findViewById(R.id.lantern);
        lantern.setOnClickListener(this);
        disableControls();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);

        rtmpBuilder = new RtmpBuilder(surfaceView, this);
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
            case R.id.button:
                if (!rtmpBuilder.isStreaming()) {
                    rtmpBuilder.prepareAudio();
                    rtmpBuilder.prepareVideo();
                    rtmpBuilder.startStream(url);
                    enableControls();
                    button.setText("Stop stream");
                } else {
                    rtmpBuilder.stopStream();
                    disableControls();
                    button.setText("Start stream");
                }
                break;
            case R.id.switch_camera:
                rtmpBuilder.switchCamera();
                break;
            case R.id.lantern:
                rtmpBuilder.enableDisableLantern();
                break;
            default:
                break;
        }
    }


    private void enableControls() {
        switchCamera.setEnabled(true);
        lantern.setEnabled(true);
    }

    private void disableControls() {
        switchCamera.setEnabled(false);
        lantern.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtmpBuilder.isStreaming()) {
            rtmpBuilder.stopStream();
        }
    }

    @Override
    public void onConnectionSuccess() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onConnectionFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
                rtmpBuilder.stopStream();
                disableControls();
                button.setText("Start stream");
            }
        });
    }
}
