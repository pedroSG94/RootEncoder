package com.pedro.rtmpstreamer;

import android.content.pm.ActivityInfo;
import android.media.MediaCodec;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.pedro.rtmpstreamer.encoder.audio.AudioEncoder;
import com.pedro.rtmpstreamer.encoder.audio.GetAccData;
import com.pedro.rtmpstreamer.input.audio.GetMicrophoneData;
import com.pedro.rtmpstreamer.input.audio.MicrophoneManager;
import com.pedro.rtmpstreamer.input.video.CameraManager;
import com.pedro.rtmpstreamer.input.video.EffectManager;
import com.pedro.rtmpstreamer.input.video.GetCameraData;
import com.pedro.rtmpstreamer.encoder.video.GetH264Data;
import com.pedro.rtmpstreamer.encoder.video.VideoEncoder;

import net.ossrs.rtmp.SrsCreator;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData, Button.OnClickListener {

    private AudioEncoder audioEncoder;
    private SrsCreator srsCreator;
    private SrsFlvMuxer srsFlvMuxer;

    private CameraManager cameraManager;
    private MicrophoneManager microphoneManager;
    private VideoEncoder videoEncoder;
    private boolean streaming;
    private Button button;
    private String url = "rtmp://yourendpoint";

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

        //create rtmp sender
        srsCreator = new SrsCreator();
        srsFlvMuxer = srsCreator.getSrsFlvMuxer();
        //create media input data
        cameraManager = new CameraManager(surfaceView, this);
        microphoneManager = new MicrophoneManager(this);
        //create encoders
        audioEncoder = new AudioEncoder(this);
        videoEncoder = new VideoEncoder(this);
        streaming = false;
    }

    @Override
    public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
        //send data in rtmp packet
        srsFlvMuxer.writeSampleData(101, accBuffer, info);
    }

    @Override
    public void inputYv12Data(byte[] buffer, int width, int height) {
        //encode data
        videoEncoder.inputYv12Data(buffer, width, height);
    }

    @Override
    public void inputNv21Data(byte[] buffer, int width, int height) {
        //encode data
        videoEncoder.inputNv21Data(buffer, width, height);
    }


    @Override
    public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        //send data in rtmp packet
        srsFlvMuxer.writeSampleData(100, h264Buffer, info);
    }

    @Override
    public void inputPcmData(byte[] buffer, int size) {
        //encode data
        audioEncoder.inputPcmData(buffer, size);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                if (!streaming) {
                    start();
                    button.setText("Stop stream");
                } else {
                    stop();
                    button.setText("Start stream");
                }
                break;
            case R.id.clear:
                cameraManager.setEffect(EffectManager.CLEAR);
                break;
            case R.id.negative:
                cameraManager.setEffect(EffectManager.NEGATIVE);
                break;
            case R.id.sepia:
                cameraManager.setEffect(EffectManager.SEPIA);
                break;
            default:
                break;
        }
    }

    public void start() {
        //init rtmp sender
        srsFlvMuxer.start(url);
        srsFlvMuxer.setVideoResolution(640, 480);

        //init encoders
        videoEncoder.prepareVideoEncoder();
        videoEncoder.start();
        audioEncoder.prepareAudioEncoder();
        audioEncoder.start();

        //init media input
        cameraManager.start();
        microphoneManager.createMicrophone();
        microphoneManager.start();
        streaming = true;
    }

    public void stop() {
        //stop rtmp sender
        srsFlvMuxer.stop();

        //stop media input, need stop it before stop encoders
        cameraManager.stop();
        microphoneManager.stop();

        //stop encoders
        videoEncoder.stop();
        audioEncoder.stop();
        streaming = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }
}
