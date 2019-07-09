package com.pedro.rtplibrary.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import androidx.annotation.RequiresApi;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 08/03/19.
 *
 * Class to control video recording with MediaMuxer.
 */
public class RecordController {

  private Status status = Status.STOPPED;
  private MediaMuxer mediaMuxer;
  private MediaFormat videoFormat, audioFormat;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private Listener listener;
  //Pause/Resume
  private long pauseMoment = 0;
  private long pauseTime = 0;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();

  public enum Status {
    STARTED, STOPPED, RECORDING, PAUSED, RESUMED
  }

  public interface Listener {
    void onStatusChange(Status status);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(String path, Listener listener) throws IOException {
    mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    this.listener = listener;
    status = Status.STARTED;
    if (listener != null) listener.onStatusChange(status);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    status = Status.STOPPED;
    if (mediaMuxer != null) {
      try {
        mediaMuxer.stop();
        mediaMuxer.release();
      } catch (Exception ignored) {
      }
    }
    mediaMuxer = null;
    videoTrack = -1;
    audioTrack = -1;
    pauseMoment = 0;
    pauseTime = 0;
    if (listener != null) listener.onStatusChange(status);
  }

  public boolean isRunning() {
    return status == Status.STARTED
        || status == Status.RECORDING
        || status == Status.RESUMED
        || status == Status.PAUSED;
  }

  public boolean isRecording() {
    return status == Status.RECORDING;
  }

  public Status getStatus() {
    return status;
  }

  public void resetFormats() {
    videoFormat = null;
    audioFormat = null;
  }

  public void pauseRecord() {
    if (status == Status.RECORDING) {
      pauseMoment = System.nanoTime() / 1000;
      status = Status.PAUSED;
      if (listener != null) listener.onStatusChange(status);
    }
  }

  public void resumeRecord() {
    if (status == Status.PAUSED) {
      pauseTime += System.nanoTime() / 1000 - pauseMoment;
      status = Status.RESUMED;
      if (listener != null) listener.onStatusChange(status);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo) {
    if (status == Status.STARTED
        && videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
        && videoFormat != null
        && audioFormat != null) {
      videoTrack = mediaMuxer.addTrack(videoFormat);
      audioTrack = mediaMuxer.addTrack(audioFormat);
      mediaMuxer.start();
      status = Status.RECORDING;
      if (listener != null) listener.onStatusChange(status);
    } else if (status == Status.RESUMED && videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
      status = Status.RECORDING;
      if (listener != null) listener.onStatusChange(status);
    }
    if (status == Status.RECORDING) {
      updateFormat(this.videoInfo, videoInfo);
      mediaMuxer.writeSampleData(videoTrack, videoBuffer, this.videoInfo);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo) {
    if (status == Status.RECORDING) {
      updateFormat(this.audioInfo, audioInfo);
      mediaMuxer.writeSampleData(audioTrack, audioBuffer, this.audioInfo);
    }
  }

  public void setVideoFormat(MediaFormat videoFormat) {
    this.videoFormat = videoFormat;
  }

  public void setAudioFormat(MediaFormat audioFormat) {
    this.audioFormat = audioFormat;
  }

  //We can't reuse info because could produce stream issues
  private void updateFormat(MediaCodec.BufferInfo newInfo, MediaCodec.BufferInfo oldInfo) {
    newInfo.flags = oldInfo.flags;
    newInfo.offset = oldInfo.offset;
    newInfo.size = oldInfo.size;
    newInfo.presentationTimeUs = oldInfo.presentationTimeUs - pauseTime;
  }
}
