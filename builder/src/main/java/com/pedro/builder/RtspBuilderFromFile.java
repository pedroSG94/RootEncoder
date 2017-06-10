package com.pedro.builder;

import android.media.MediaCodec;
import com.pedro.encoder.audio.GetAccData;
import com.pedro.encoder.input.decoder.AudioDecoder;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 10/06/17.
 */

public class RtspBuilderFromFile implements GetAccData, GetH264Data {

  private boolean streaming;
  private VideoDecoder videoDecoder;
  private AudioDecoder audioDecoder;
  private RtspClient rtspClient;

  public RtspBuilderFromFile(Protocol protocol, ConnectCheckerRtsp connectCheckerRtsp) {
    rtspClient = new RtspClient(connectCheckerRtsp, protocol);
    videoDecoder = new VideoDecoder(this);
    audioDecoder = new AudioDecoder(this);
    streaming = false;
  }

  public void setFile(String path) {
    try {
      audioDecoder.setFilePath(path);
      videoDecoder.setFilePath(path);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  public void setAuthorization(String user, String password) {
    rtspClient.setAuthorization(user, password);
  }

  public boolean prepareVideo() {
    return videoDecoder.prepareVideo();
  }

  public boolean prepareAudio() {
    boolean result = audioDecoder.prepareAudio();
    rtspClient.setSampleRate(audioDecoder.getSampleRate());
    rtspClient.setIsStereo(audioDecoder.isStereo());
    return result;
  }

  public void startStream(String url) {
    rtspClient.setUrl(url);
    audioDecoder.start();
    videoDecoder.start();
    streaming = true;
  }

  public void stopStream() {
    rtspClient.disconnect();
    audioDecoder.stop();
    videoDecoder.stop();
    streaming = false;
  }

  public boolean isStreaming() {
    return streaming;
  }

  @Override
  public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
    rtspClient.sendAudio(accBuffer, info);
  }

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    byte[] mSPS = new byte[sps.capacity() - 4];
    sps.position(4);
    sps.get(mSPS, 0, mSPS.length);
    byte[] mPPS = new byte[pps.capacity() - 4];
    pps.position(4);
    pps.get(mPPS, 0, mPPS.length);
    rtspClient.setSPSandPPS(mPPS, mSPS);
    rtspClient.connect();
  }
}
