package com.pedro.rtmpstreamer;

import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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
    private Button clear, negative, sepia;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        clear = (Button) findViewById(R.id.clear);
        clear.setOnClickListener(this);
        negative = (Button) findViewById(R.id.negative);
        negative.setOnClickListener(this);
        sepia = (Button) findViewById(R.id.sepia);
        sepia.setOnClickListener(this);
        disableEffect();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);

        rtmpBuilder = new RtmpBuilder(surfaceView, this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                if (!rtmpBuilder.isStreaming()) {
                    rtmpBuilder.prepareAudio();
                    rtmpBuilder.prepareVideo();
                    rtmpBuilder.startStream(url);
                    enableEffect();
                    button.setText("Stop stream");
                } else {
                    rtmpBuilder.stopStream();
                    disableEffect();
                    button.setText("Start stream");
                }
                break;
            case R.id.clear:
                rtmpBuilder.setEffect(EffectManager.CLEAR);
                break;
            case R.id.negative:
                rtmpBuilder.setEffect(EffectManager.NEGATIVE);
                break;
            case R.id.sepia:
                rtmpBuilder.setEffect(EffectManager.SEPIA);
                break;
            default:
                break;
        }
    }


    private void enableEffect() {
        clear.setEnabled(true);
        negative.setEnabled(true);
        sepia.setEnabled(true);
    }

    private void disableEffect() {
        clear.setEnabled(false);
        negative.setEnabled(false);
        sepia.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtmpBuilder.isStreaming()) {
            rtmpBuilder.stopStream();
        }
    }

    @Override
    public void onConnectionSucces() {
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
                disableEffect();
                button.setText("Start stream");
            }
        });

    }
}
