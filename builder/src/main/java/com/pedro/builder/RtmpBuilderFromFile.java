package com.pedro.builder;

/**
 * Created by pedro on 26/06/17.
 */

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
import java.io.IOException;
import java.nio.ByteBuffer;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

/**
 * Created by pedro on 26/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RtmpBuilderFromFile
    implements GetAccData, GetCameraData, GetH264Data, GetMicrophoneData {

  private AudioDecoder audioDecoder;
  private VideoDecoder videoDecoder;
  private VideoEncoder videoEncoder;
  private AudioEncoder audioEncoder;
  private boolean streaming;
  private SrsFlvMuxer srsFlvMuxer;
  private boolean videoEnabled = true;

  public RtmpBuilderFromFile(ConnectCheckerRtmp connectCheckerRtmp) {
    srsFlvMuxer = new SrsFlvMuxer(connectCheckerRtmp);
    videoEncoder = new VideoEncoder(this);
    audioEncoder = new AudioEncoder(this);
    audioDecoder = new AudioDecoder(this);
    videoDecoder = new VideoDecoder();
    streaming = false;
  }

  public void setAuthorization(String user, String password) {
    srsFlvMuxer.setAuthorization(user, password);
  }

  public boolean prepareAudio(String filePath) throws IOException {
    audioDecoder.initExtractor(filePath);
    srsFlvMuxer.setAsample_rate(audioDecoder.getSampleRate());
    srsFlvMuxer.setIsStereo(audioDecoder.isStereo());
    audioDecoder.prepareAudio();
    return audioEncoder.prepareAudioEncoder(audioDecoder.getBitRate(), audioDecoder.getSampleRate(),
        audioDecoder.isStereo());
  }

  public boolean prepareVideo(String filePath, int bitRate) throws IOException {
    videoDecoder.initExtractor(filePath);
    boolean result =
        videoEncoder.prepareVideoEncoder(videoDecoder.getWidth(), videoDecoder.getHeight(),
            videoDecoder.getFps(), bitRate, 0, true, FormatVideoEncoder.SURFACE);
    videoDecoder.prepareVideo(videoEncoder.getInputSurface());
    return result;
  }

  public void startStream(String url) {
    audioEncoder.start();
    videoEncoder.start();
    audioDecoder.start();
    videoDecoder.start();
    srsFlvMuxer.start(url);
    streaming = true;
  }

  public void stopStream() {
    srsFlvMuxer.stop();
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
    srsFlvMuxer.sendAudio(accBuffer, info);
  }

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    srsFlvMuxer.setSpsPPs(sps, pps);
  }

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    srsFlvMuxer.sendVideo(h264Buffer, info);
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


