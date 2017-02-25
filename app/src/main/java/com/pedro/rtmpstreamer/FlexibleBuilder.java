package com.pedro.rtmpstreamer;

import android.util.Log;
import android.view.SurfaceView;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * Created by pedro on 19/02/17.
 */

public class FlexibleBuilder {

  private final String TAG = "FlexibleBuilder";
  private String url;

  private RtspBuilder rtspBuilder;
  private RtmpBuilder rtmpBuilder;

  public FlexibleBuilder(SurfaceView surfaceView, ConnectCheckerRtmp connectChecker, ConnectCheckerRtsp connectCheckerRtsp) {
    rtmpBuilder = new RtmpBuilder(surfaceView, connectChecker);
    rtspBuilder = new RtspBuilder(surfaceView, Protocol.TCP, connectCheckerRtsp);
  }

  /**
   * only supported for rtsp atm
   */
  public void setAuthorization(String user, String password){
    rtspBuilder.setAuthorization(user, password);
  }

  public void prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
    rtmpBuilder.prepareVideo(width, height, fps, bitrate, rotation);
    rtspBuilder.prepareVideo(width, height, fps, bitrate, rotation);
  }

  public void prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
    rtmpBuilder.prepareAudio(bitrate, sampleRate, isStereo);
    rtspBuilder.prepareAudio(bitrate, sampleRate, isStereo);
  }

  public void prepareVideo() {
    rtmpBuilder.prepareVideo();
    rtspBuilder.prepareVideo();
  }

  public void prepareAudio() {
    rtmpBuilder.prepareAudio();
    rtspBuilder.prepareAudio();
  }

  public void startStream(String url) {
    this.url = url;
    if (url.startsWith("rtmp")) {
      rtmpBuilder.startStream(url);
    } else if (url.startsWith("rtsp")) {
      rtspBuilder.startStream(url);
    } else {
      Log.e(TAG, "malformed endPoint");
    }
  }

  public void stopStream() {
    if (url == null) return;
    if (url.startsWith("rtmp")) {
      rtmpBuilder.stopStream();
    } else if (url.startsWith("rtsp")) {
      rtspBuilder.stopStream();
    } else {
      Log.e(TAG, "malformed endPoint");
    }
  }

  public void enableDisableLantern() {
    if (url == null) return;
    if (url.startsWith("rtmp")) {
      rtmpBuilder.enableDisableLantern();
    } else if (url.startsWith("rtsp")) {
      rtspBuilder.enableDisableLantern();
    } else {
      Log.e(TAG, "malformed endPoint");
    }
  }

  public void switchCamera() {
    if (url == null) return;
    if (url.startsWith("rtmp")) {
      rtmpBuilder.switchCamera();
    } else if (url.startsWith("rtsp")) {
      rtspBuilder.switchCamera();
    } else {
      Log.e(TAG, "malformed endPoint");
    }
  }

  public boolean isStreaming() {
    if (url == null) {
      return false;
    } else {
      if (url.startsWith("rtmp")) {
        return rtmpBuilder.isStreaming();
      } else if (url.startsWith("rtsp")) {
        return rtspBuilder.isStreaming();
      } else {
        Log.e(TAG, "malformed endPoint");
        return false;
      }
    }
  }

  //only for rtsp
  public void updateDestination(){
    if(url == null) return;
    if(url.startsWith("rtsp")){
      rtspBuilder.updateDestination();
    }
  }
  public void setEffect(EffectManager effect) {
    if (url == null) return;
    if (url.startsWith("rtmp")) {
      rtmpBuilder.setEffect(effect);
    } else if (url.startsWith("rtsp")) {
      rtspBuilder.setEffect(effect);
    } else {
      Log.e(TAG, "malformed endPoint");
    }
  }
}