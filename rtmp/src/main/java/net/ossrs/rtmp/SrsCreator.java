package net.ossrs.rtmp;

import com.github.faucamp.simplertmp.RtmpHandler;

import java.io.IOException;
import java.net.SocketException;

/**
 * Created by pedro on 21/01/17.
 */

public class SrsCreator {

    private SrsFlvMuxer srsFlvMuxer = new SrsFlvMuxer(new RtmpHandler(new RtmpHandler.RtmpListener() {
        @Override
        public void onRtmpConnecting(String msg) {

        }

        @Override
        public void onRtmpConnected(String msg) {

        }

        @Override
        public void onRtmpVideoStreaming() {

        }

        @Override
        public void onRtmpAudioStreaming() {

        }

        @Override
        public void onRtmpStopped() {

        }

        @Override
        public void onRtmpDisconnected() {

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

        }

        @Override
        public void onRtmpIOException(IOException e) {

        }

        @Override
        public void onRtmpIllegalArgumentException(IllegalArgumentException e) {

        }

        @Override
        public void onRtmpIllegalStateException(IllegalStateException e) {

        }
    }));

    private SrsMp4Muxer srsMp4Muxer = new SrsMp4Muxer(new SrsRecordHandler(new SrsRecordHandler.SrsRecordListener() {
        @Override
        public void onRecordPause() {

        }

        @Override
        public void onRecordResume() {

        }

        @Override
        public void onRecordStarted(String msg) {

        }

        @Override
        public void onRecordFinished(String msg) {

        }

        @Override
        public void onRecordIllegalArgumentException(IllegalArgumentException e) {

        }

        @Override
        public void onRecordIOException(IOException e) {

        }
    }));

    public SrsFlvMuxer getSrsFlvMuxer() {
        return srsFlvMuxer;
    }

    public SrsMp4Muxer getSrsMp4Muxer() {
        return srsMp4Muxer;
    }
}
