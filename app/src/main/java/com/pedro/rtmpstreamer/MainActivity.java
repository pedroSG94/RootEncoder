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
import com.pedro.rtmpstreamer.input.video.GetCameraData;
import com.pedro.rtmpstreamer.encoder.video.GetH264Data;
import com.pedro.rtmpstreamer.encoder.video.VideoEncoder;

import net.ossrs.rtmp.SrsCreator;
import net.ossrs.rtmp.SrsFlvMuxer;
import net.ossrs.rtmp.SrsMp4Muxer;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData, Button.OnClickListener {

    private AudioEncoder audioEncoder;
    private SrsCreator srsCreator;
    private SrsFlvMuxer srsFlvMuxer;
    private SrsMp4Muxer srsMp4Muxer;

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

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);

        //create rtmp senders
        srsCreator = new SrsCreator();
        srsFlvMuxer = srsCreator.getSrsFlvMuxer();
        srsMp4Muxer = srsCreator.getSrsMp4Muxer();
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
        srsMp4Muxer.writeSampleData(101, accBuffer.duplicate(), info);
        srsFlvMuxer.writeSampleData(101, accBuffer, info);
    }

    @Override
    public void inputYv12Data(byte[] buffer, int width, int height) {
        //encode data
        videoEncoder.inputYv12Data(buffer, width, height);
    }


    @Override
    public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        //send data in rtmp packet
        srsMp4Muxer.writeSampleData(100, h264Buffer.duplicate(), info);
        srsFlvMuxer.writeSampleData(100, h264Buffer, info);
    }

    @Override
    public void inputPcmData(byte[] buffer, int size) {
        //encode data
        audioEncoder.inputPcmData(buffer, size);
    }

    @Override
    public void onClick(View v) {
        if (!streaming) {
            start();
            button.setText("Stop stream");
        } else {
            stop();
            button.setText("Start stream");
        }
    }

    public void start() {
        //init rtmp senders
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
        //stop rtmp senders
        srsFlvMuxer.stop();
        srsMp4Muxer.stop();

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
