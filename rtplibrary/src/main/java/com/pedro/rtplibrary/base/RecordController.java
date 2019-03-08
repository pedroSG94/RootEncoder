package com.pedro.rtplibrary.base;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 08/03/19.
 *
 * Class to control video recording with MediaMuxer.
 */
public class RecordController {

  private boolean recording;
  private MediaMuxer mediaMuxer;
  private MediaFormat videoFormat, audioFormat;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private boolean isFirstFrame = true;

  public RecordController() {
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(String path) throws IOException {
    mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    recording = true;
    isFirstFrame = true;
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    if (mediaMuxer != null) {
      mediaMuxer.stop();
      mediaMuxer.release();
    }
    mediaMuxer = null;
    recording = false;
    isFirstFrame = true;
    videoTrack = -1;
    audioTrack = -1;
  }

  public boolean isRecording() {
    return recording;
  }

  public void resetFormats() {
    videoFormat = null;
    audioFormat = null;
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo) {
    if (mediaMuxer != null && recording) {
      if (isFirstFrame && videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
          && videoFormat != null && audioFormat != null) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
        audioTrack = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();
        isFirstFrame = false;
      }
      if (!isFirstFrame) mediaMuxer.writeSampleData(videoTrack, videoBuffer, videoInfo);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo) {
    if (mediaMuxer != null && recording && !isFirstFrame) {
      mediaMuxer.writeSampleData(audioTrack, audioBuffer, audioInfo);
    }
  }

  public void setVideoFormat(MediaFormat videoFormat) {
    this.videoFormat = videoFormat;
  }

  public void setAudioFormat(MediaFormat audioFormat) {
    this.audioFormat = audioFormat;
  }
}
