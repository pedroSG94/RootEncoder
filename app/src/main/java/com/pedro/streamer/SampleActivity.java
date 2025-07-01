package com.pedro.streamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import com.pedro.library.rtmp.RtmpCamera2;
import com.pedro.library.view.OpenGlView;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SampleActivity extends AppCompatActivity implements ConnectChecker, SurfaceHolder.Callback {

    private RtmpCamera2 rtmpCamera2;
    private OpenGlView openGlView;
    private Button streamButton;

    private boolean isStreaming = false;
    private final String rtmpUrl = "rtmp://192.168.1.11/live/pedro";
    AndroidViewFilterRender androidViewFilterRender = new AndroidViewFilterRender();
    AndroidViewFilterRender audioFilterRender = new AndroidViewFilterRender();

    private Button toggleAudioFilterButton;
    private Button toggleAndroidViewFilterButton;

    private boolean isAudioFilterAdded = false;
    private boolean isAndroidViewFilterAdded = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

        openGlView = findViewById(R.id.open_gl_view);
        streamButton = findViewById(R.id.button_stream);

        toggleAudioFilterButton = findViewById(R.id.button_toggle_audio_filter);
        toggleAndroidViewFilterButton = findViewById(R.id.button_toggle_android_view_filter);

        toggleAudioFilterButton.setOnClickListener(v -> toggleAudioFilter());
        toggleAndroidViewFilterButton.setOnClickListener(v -> toggleAndroidViewFilter());


        rtmpCamera2 = new RtmpCamera2(openGlView, this);
        setUp();
        streamButton.setOnClickListener(view -> {
            if (!isStreaming) {
                if (checkPermissions()) {
                    startStream();
                } else {
                    requestPermissions();
                }
            } else {
                stopStream();
            }
        });
    }

    private void toggleAudioFilter() {
        if (!isAudioFilterAdded) {
            audioFilterRender.setPreviewSize(100, 100);
            audioFilterRender.setView(findViewById(R.id.bakingGenericMuteLayout));
            if (rtmpCamera2.getGlInterface().filtersCount() < 2) {
                rtmpCamera2.getGlInterface().addFilter(1, audioFilterRender);
                isAudioFilterAdded = true;
//                Toast.makeText(this, "Audio filter added", Toast.LENGTH_SHORT).show();
            }
        } else {
            rtmpCamera2.getGlInterface().removeFilter(audioFilterRender);
            isAudioFilterAdded = false;
//            Toast.makeText(this, "Audio filter removed", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleAndroidViewFilter() {
        if (!isAndroidViewFilterAdded) {
            androidViewFilterRender.setPreviewSize(100, 100);
            androidViewFilterRender.setView(findViewById(R.id.newBakingscoreboardLayout));
            rtmpCamera2.getGlInterface().setFilter(androidViewFilterRender);
            isAndroidViewFilterAdded = true;
//            Toast.makeText(this, "AndroidView filter set", Toast.LENGTH_SHORT).show();
        } else {
            rtmpCamera2.getGlInterface().setFilter(new NoFilterRender());
            isAndroidViewFilterAdded = false;
//            Toast.makeText(this, "AndroidView filter removed", Toast.LENGTH_SHORT).show();
        }
    }

    private void startStream() {
        if (!rtmpCamera2.isStreaming()) {
            if (rtmpCamera2.prepareAudio() && rtmpCamera2.prepareVideo()) {
                rtmpCamera2.startStream(rtmpUrl);
                streamButton.setText("Stop");
                isStreaming = true;
            } else {
                Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show();
            }
        }
    }
    public void setUp() {
        try {
            openGlView.getHolder().addCallback(this);
            openGlView.setEncoderSize(1280, 720);

            runOnUiThread(new Runnable() {
                public void run() {
                    if (!checkPermissions()) {
                        return;
                    }

                    rtmpCamera2 = new RtmpCamera2(openGlView, SampleActivity.this);
                    rtmpCamera2.getStreamClient().setLogs(false);
//                    rtmpCamera2.prepareVideo(1920, 1080, 4608000);
                    rtmpCamera2.prepareVideo(1280, 720,  3360000);
                    rtmpCamera2.getStreamClient().setReTries(10);
                    rtmpCamera2.enableAutoFocus();

//                    AndroidMuxerRecordControllerModified recordControllerModified = new AndroidMuxerRecordControllerModified();
//                    rtmpCamera2.setRecordController(recordControllerModified);
//                    recordControllerModified.setOnBufferDataListener((OnBufferDataListener) SampleActivity.this);

                }
            });
            //openGlView.setOnTouchListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    private void stopStream() {
        if (rtmpCamera2.isStreaming()) {
            rtmpCamera2.stopStream();
            streamButton.setText("Start");
            isStreaming = false;
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtmpCamera2.isStreaming()) rtmpCamera2.stopStream();
    }

    @Override
    public void onConnectionStarted(@NonNull String s) {
        System.out.println("onConnectionStarted :: "+s);
        runOnUiThread(() -> Toast.makeText(SampleActivity.this, "Connection Started :: ", Toast.LENGTH_SHORT).show());

    }

    @Override
    public void onConnectionSuccess() {
        System.out.println("onConnectionSuccess :: ");
        runOnUiThread(() -> Toast.makeText(SampleActivity.this, "Connection Success", Toast.LENGTH_SHORT).show());

    }

    @Override
    public void onConnectionFailed(@NonNull String s) {
        System.out.println("onConnectionFailed :: "+s);
        runOnUiThread(() -> {
            Toast.makeText(SampleActivity.this, "Connection Failed: " + s, Toast.LENGTH_SHORT).show();
            stopStream();
        });
    }

    @Override
    public void onDisconnect() {
        System.out.println("onDisconnect :: ");
        runOnUiThread(() -> Toast.makeText(SampleActivity.this, "Disconnected", Toast.LENGTH_SHORT).show());

    }

    @Override
    public void onAuthError() {
        System.out.println("onAuthError :: ");
    }

    @Override
    public void onAuthSuccess() {
        System.out.println("onAuthSuccess :: ");
    }

    private PointF calculateOriginalSize(float originalWidth, float originalHeight, float streamWidth, float streamHeight, boolean isPortrait) {
        float w = isPortrait ? streamHeight : streamWidth;
        float h = isPortrait ? streamWidth : streamHeight;
        PointF point = new PointF();
        point.x = originalWidth * 100f / w;
        point.y = originalHeight * 100f / h;
        return point;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        try {
            runOnUiThread(new Runnable() {
                public void run() {
                    rtmpCamera2.startPreview();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (rtmpCamera2.isStreaming()) {
                        rtmpCamera2.stopStream();
                    }
                    rtmpCamera2.stopPreview();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}