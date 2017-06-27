package com.pedro.builder;

import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAccData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.decoder.AudioDecoder;
import com.pedro.encoder.input.decoder.VideoDecoder;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 4/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtspBuilderFromFile
    implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData {

  private AudioDecoder audioDecoder;
  private VideoDecoder videoDecoder;
  private VideoEncoder videoEncoder;
  private AudioEncoder audioEncoder;
  private boolean streaming;

  private RtspClient rtspClient;
  private boolean videoEnabled = true;

  public RtspBuilderFromFile(Protocol protocol, ConnectCheckerRtsp connectCheckerRtsp) {
    rtspClient = new RtspClient(connectCheckerRtsp, protocol);
    videoEncoder = new VideoEncoder(this);
    audioEncoder = new AudioEncoder(this);
    audioDecoder = new AudioDecoder(this);
    videoDecoder = new VideoDecoder();
    streaming = false;
  }

  public void setAuthorization(String user, String password) {
    rtspClient.setAuthorization(user, password);
  }

  public boolean prepareAudio(String filePath) throws IOException {
    if (!audioDecoder.initExtractor(filePath)) {
      return false;
    }
    rtspClient.setSampleRate(audioDecoder.getSampleRate());
    rtspClient.setIsStereo(audioDecoder.isStereo());
    audioDecoder.prepareAudio();
    return audioEncoder.prepareAudioEncoder(128 * 1024, audioDecoder.getSampleRate(),
        audioDecoder.isStereo());
  }

  public boolean prepareVideo(String filePath, int bitRate) throws IOException {
    if (!videoDecoder.initExtractor(filePath)) {
      return false;
    }
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
            bitRate, 0, true, FormatVideoEncoder.SURFACE);
    videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    return result;
  }

  public boolean prepareAudio(String filePath, boolean isStereo, int sampleRate)
      throws IOException {
    if (!audioDecoder.initExtractor(filePath, isStereo, sampleRate)) return false;
    rtspClient.setSampleRate(sampleRate);
    rtspClient.setIsStereo(isStereo);
    audioDecoder.prepareAudio();
    return audioEncoder.prepareAudioEncoder(128 * 1024, audioDecoder.getSampleRate(),
        audioDecoder.isStereo());
  }

  public boolean prepareVideo(String filePath, int bitRate, int width, int height)
      throws IOException {
    if (!videoDecoder.initExtractor(filePath, width, height)) return false;
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(), 30,
            bitRate, 0, true, FormatVideoEncoder.SURFACE);
    videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    return result;
  }

  public void startStream(String url) {
    rtspClient.setUrl(url);
    audioEncoder.start();
    videoEncoder.start();
    audioDecoder.start();
    videoDecoder.start();
    streaming = true;
  }

  public void stopStream() {
    rtspClient.disconnect();
    videoDecoder.stop();
    audioDecoder.stop();
    videoEncoder.stop();
    audioEncoder.stop();
    streaming = false;
  }

  public void setLoopMode(boolean loopMode) {
    audioDecoder.setLoopMode(loopMode);
    videoDecoder.setLoopMode(loopMode);
  }

  public void disableAudio() {
    audioDecoder.mute();
  }

  public void enableAudio() {
    audioDecoder.unMute();
  }

  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  /** need min API 19 */
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
  }

  public boolean isStreaming() {
    return streaming;
  }

  @Override
  public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
    rtspClient.sendAudio(accBuffer, info);
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

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }

  @Override
  public void inputPcmData(byte[] buffer, int size) {
    audioEncoder.inputPcmData(buffer, size);
  }

  @Override
  public void inputYv12Data(byte[] buffer) {
    videoEncoder.inputYv12Data(buffer);
  }

  @Override
  public void inputNv21Data(byte[] buffer) {
    videoEncoder.inputNv21Data(buffer);
  }
}

