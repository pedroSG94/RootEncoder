package com.pedro.rtmpstreamer.encoder.audio.api21;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.pedro.rtmpstreamer.encoder.audio.GetAccData;
import com.pedro.rtmpstreamer.input.audio.MicrophoneManager;

import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 * Useless atm
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AudioEncoderCallback extends MediaCodec.Callback {

    private String TAG = "AudioEncoderCallback";

    private MicrophoneManager microphoneManager;
    private GetAccData getAccData;
    private long mPresentTimeUs;

    public AudioEncoderCallback(MicrophoneManager microphoneManager, GetAccData getAccData) {
        this.microphoneManager = microphoneManager;
        this.getAccData = getAccData;
        mPresentTimeUs = System.nanoTime() / 1000;
    }

    public void start() {
        microphoneManager.start();
    }

    @Override
    public void onInputBufferAvailable(MediaCodec codec, int index) {
//        if (index >= 0) {
//            DataTaken dataTaken = microphoneManager.read();
//            ByteBuffer byteBuffer = codec.getInputBuffer(index);
//            if (byteBuffer != null && dataTaken != null) {
//                byteBuffer.clear();
//                byteBuffer.put(dataTaken.getPcmBuffer(), 0, dataTaken.getSize());
//                long pts = System.nanoTime() / 1000 - mPresentTimeUs;
//                codec.queueInputBuffer(index, 0, dataTaken.getSize(), pts, 0);
//            }
//        }
    }

    @Override
    public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
        //This ByteBuffer is ACC
        ByteBuffer byteBuffer = codec.getOutputBuffer(index);
        if (index >= 0) {
            getAccData.getAccData(byteBuffer, info);
            codec.releaseOutputBuffer(index, false);
        }
    }

    @Override
    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
        Log.e(TAG, e.getMessage());
    }

    @Override
    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

    }
}
