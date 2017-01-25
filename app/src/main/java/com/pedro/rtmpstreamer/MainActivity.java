package com.pedro.rtmpstreamer;

import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.pedro.rtmpstreamer.input.video.EffectManager;
import com.pedro.rtmpstreamer.utils.RtmpBuilder;


public class MainActivity extends AppCompatActivity implements Button.OnClickListener {

    private Button button;
    private String url = "rtmp://yourendpoint";
    private RtmpBuilder rtmpBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        Button clear = (Button) findViewById(R.id.clear);
        clear.setOnClickListener(this);
        Button negative = (Button) findViewById(R.id.negative);
        negative.setOnClickListener(this);
        Button sepia = (Button) findViewById(R.id.sepia);
        sepia.setOnClickListener(this);

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);

        rtmpBuilder = new RtmpBuilder(surfaceView);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                if (!rtmpBuilder.isStreaming()) {
                    rtmpBuilder.prepareAudio();
                    rtmpBuilder.prepareVideo();
                    rtmpBuilder.startStream(url);
                    button.setText("Stop stream");
                } else {
                    rtmpBuilder.stopStream();
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


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtmpBuilder.isStreaming()) {
            rtmpBuilder.stopStream();
        }
    }
}
