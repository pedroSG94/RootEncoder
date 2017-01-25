package net.ossrs.rtmp;

import android.util.Log;

import com.github.faucamp.simplertmp.RtmpHandler;

import java.io.IOException;
import java.net.SocketException;

/**
 * Created by pedro on 21/01/17.
 */

public class SrsCreator {

    private String TAG = "SrsCreator";
    private SrsFlvMuxer srsFlvMuxer = new SrsFlvMuxer(new RtmpHandler(new RtmpHandler.RtmpListener() {
        @Override
        public void onRtmpConnecting(String msg) {
            Log.i(TAG, msg);
        }

        @Override
        public void onRtmpConnected(String msg) {
            Log.i(TAG, msg);
        }

        @Override
        public void onRtmpVideoStreaming() {

        }

        @Override
        public void onRtmpAudioStreaming() {

        }

        @Override
        public void onRtmpStopped() {
            Log.i(TAG, "rtmp stopped");
        }

        @Override
        public void onRtmpDisconnected() {
            Log.i(TAG, "rtmp disconnected");
        }

        @Override
        public void onRtmpVideoFpsChanged(double fps) {

        }

        @Override
        public void onRtmpVideoBitrateChanged(double bitrate) {

        }

        @Override
        public void onRtmpAudioBitrateChanged(double bitrate) {

        }

        @Override
        public void onRtmpSocketException(SocketException e) {
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onRtmpIOException(IOException e) {
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onRtmpIllegalStateException(IllegalStateException e) {
            Log.e(TAG, e.getMessage());
        }
    }));

    public SrsFlvMuxer getSrsFlvMuxer() {
        return srsFlvMuxer;
    }

}
