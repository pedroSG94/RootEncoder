package com.github.faucamp.simplertmp;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.faucamp.simplertmp.io.RtmpConnection;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * Srs implementation of an RTMP publisher
 * 
 * @author francois, leoma, pedro
 */
public class DefaultRtmpPublisher implements RtmpPublisher {

    private RtmpConnection rtmpConnection;

    public DefaultRtmpPublisher(ConnectCheckerRtmp connectCheckerRtmp) {
        rtmpConnection = new RtmpConnection(connectCheckerRtmp);
    }

    public void setJksData(InputStream inputStreamJks, String passPhraseJks){
        rtmpConnection.setJksData(inputStreamJks, passPhraseJks);
    }

    @Override
    public boolean connect(String url) {
        return rtmpConnection.connect(url);
    }

    @Override
    public boolean publish(String publishType) {
        return rtmpConnection.publish(publishType);
    }

    @Override
    public void close() {
        rtmpConnection.close();
    }

    @Override
    public void publishVideoData(byte[] data, int size, int dts) {
        rtmpConnection.publishVideoData(data, size, dts);
    }

    @Override
    public void publishAudioData(byte[] data, int size, int dts) {
        rtmpConnection.publishAudioData(data, size, dts);
    }

    @Override
    public AtomicInteger getVideoFrameCacheNumber() {
        return rtmpConnection.getVideoFrameCacheNumber();
    }

    @Override
    public void setVideoResolution(int width, int height) {
        rtmpConnection.setVideoResolution(width, height);
    }

    @Override
    public void setAuthorization(String user, String password) {
        rtmpConnection.setAuthorization(user, password);
    }
}
