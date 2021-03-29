package com.pedro.rtplibrary.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.rtsp.utils.RtpConstants;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 08/03/19.
 *
 * Class to control video recording with MediaMuxer.
 */
public class RecordController {

  private static final String TAG = "RecordController";
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
  private String videoMime = CodecUtil.H264_MIME;
  private boolean isOnlyAudio = false;

  public enum Status {
    STARTED, STOPPED, RECORDING, PAUSED, RESUMED
  }

  public interface Listener {
    void onStatusChange(Status status);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(@NonNull String path, @Nullable Listener listener) throws IOException {
    mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    this.listener = listener;
    status = Status.STARTED;
    if (listener != null) listener.onStatusChange(status);
    if (isOnlyAudio && audioFormat != null) init();
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void startRecord(@NonNull FileDescriptor fd, @Nullable Listener listener) throws IOException {
    mediaMuxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    this.listener = listener;
    status = Status.STARTED;
    if(listener != null) listener.onStatusChange(status);
    if(isOnlyAudio && audioFormat != null) init();
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

  public void setVideoMime(String videoMime) {
    this.videoMime = videoMime;
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

  private boolean isKeyFrame(ByteBuffer videoBuffer) {
    byte[] header = new byte[5];
    videoBuffer.duplicate().get(header, 0, header.length);
    if (videoMime.equals(CodecUtil.H264_MIME) && (header[4] & 0x1F) == RtpConstants.IDR) {  //h264
      return true;
    } else { //h265
      return videoMime.equals(CodecUtil.H265_MIME)
          && ((header[4] >> 1) & 0x3f) == RtpConstants.IDR_W_DLP
          || ((header[4] >> 1) & 0x3f) == RtpConstants.IDR_N_LP;
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void write(int track, ByteBuffer byteBuffer, MediaCodec.BufferInfo info) {
    try {
      mediaMuxer.writeSampleData(track, byteBuffer, info);
    } catch (IllegalStateException | IllegalArgumentException e) {
      Log.i(TAG, "Write error", e);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void init() {
    audioTrack = mediaMuxer.addTrack(audioFormat);
    mediaMuxer.start();
    status = Status.RECORDING;
    if (listener != null) listener.onStatusChange(status);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordVideo(ByteBuffer videoBuffer, MediaCodec.BufferInfo videoInfo) {
    if (status == Status.STARTED && videoFormat != null && audioFormat != null) {
      if (videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME || isKeyFrame(videoBuffer)) {
        videoTrack = mediaMuxer.addTrack(videoFormat);
        init();
      }
    } else if (status == Status.RESUMED && (videoInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
        || isKeyFrame(videoBuffer))) {
      status = Status.RECORDING;
      if (listener != null) listener.onStatusChange(status);
    }
    if (status == Status.RECORDING) {
      updateFormat(this.videoInfo, videoInfo);
      write(videoTrack, videoBuffer, this.videoInfo);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void recordAudio(ByteBuffer audioBuffer, MediaCodec.BufferInfo audioInfo) {
    if (status == Status.RECORDING) {
      updateFormat(this.audioInfo, audioInfo);
      write(audioTrack, audioBuffer, this.audioInfo);
    }
  }

  public void setVideoFormat(MediaFormat videoFormat) {
    this.videoFormat = videoFormat;
  }

  public void setAudioFormat(MediaFormat audioFormat, boolean isOnlyAudio) {
    this.audioFormat = audioFormat;
    this.isOnlyAudio = isOnlyAudio;
    if (isOnlyAudio && status == Status.STARTED
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      init();
    }
  }

  public void setAudioFormat(MediaFormat audioFormat) {
    setAudioFormat(audioFormat, false);
  }

  //We can't reuse info because could produce stream issues
  private void updateFormat(MediaCodec.BufferInfo newInfo, MediaCodec.BufferInfo oldInfo) {
    newInfo.flags = oldInfo.flags;
    newInfo.offset = oldInfo.offset;
    newInfo.size = oldInfo.size;
    newInfo.presentationTimeUs = oldInfo.presentationTimeUs - pauseTime;
  }
}
