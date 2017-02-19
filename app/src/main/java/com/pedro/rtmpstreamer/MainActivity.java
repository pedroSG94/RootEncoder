package com.pedro.rtmpstreamer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.encoder.input.video.EffectManager;
import net.ossrs.rtmp.ConnectChecker;

public class MainActivity extends AppCompatActivity implements Button.OnClickListener, ConnectChecker {

    private String url = "rtmp://yourendpoint";
    private FlexibleBuilder flexibleBuilder;
    private Button bStartStop, switchCamera, lantern;
    private EditText etUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        flexibleBuilder = new FlexibleBuilder(surfaceView, this);

        etUrl = (EditText) findViewById(R.id.et_rtmp_url);
        switchCamera = (Button) findViewById(R.id.switch_camera);
        bStartStop = (Button) findViewById(R.id.b_start_stop);
        lantern = (Button) findViewById(R.id.lantern);
        switchCamera.setOnClickListener(this);
        lantern.setOnClickListener(this);
        bStartStop.setOnClickListener(this);
        disableControls();
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
                flexibleBuilder.setEffect(EffectManager.CLEAR);
                return true;
            case R.id.grey_scale:
                flexibleBuilder.setEffect(EffectManager.GREYSCALE);
                return true;
            case R.id.sepia:
                flexibleBuilder.setEffect(EffectManager.SEPIA);
                return true;
            case R.id.negative:
                flexibleBuilder.setEffect(EffectManager.NEGATIVE);
                return true;
            case R.id.aqua:
                flexibleBuilder.setEffect(EffectManager.AQUA);
                return true;
            case R.id.posterize:
                flexibleBuilder.setEffect(EffectManager.POSTERIZE);
                return true;
            case R.id.solarize:
                flexibleBuilder.setEffect(EffectManager.SOLARIZE);
                return true;
            case R.id.whiteboard:
                flexibleBuilder.setEffect(EffectManager.WHITEBOARD);
                return true;
            case R.id.blackboard:
                flexibleBuilder.setEffect(EffectManager.BLACKBOARD);
                return true;
            default:
                return false;
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.b_start_stop:
                if (!flexibleBuilder.isStreaming()) {
                    flexibleBuilder.prepareAudio();
                    flexibleBuilder.prepareVideo();
                    url = etUrl.getText().toString();
                    flexibleBuilder.startStream(url);
                    enableControls();
                    bStartStop.setText("Stop stream");
                } else {
                    flexibleBuilder.stopStream();
                    disableControls();
                    bStartStop.setText("Start stream");
                }
                break;
            case R.id.switch_camera:
                flexibleBuilder.switchCamera();
                break;
            case R.id.lantern:
                flexibleBuilder.enableDisableLantern();
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
        if (flexibleBuilder.isStreaming()) {
            flexibleBuilder.stopStream();
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
                flexibleBuilder.stopStream();
                disableControls();
                bStartStop.setText("Start stream");
            }
        });
    }

    @Override
    public void onDisconnect() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
