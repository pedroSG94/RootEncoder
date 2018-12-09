package com.pedro.encoder.input.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/06/17.
 */
public class VideoDecoder {

  private final String TAG = "VideoDecoder";

  private VideoDecoderInterface videoDecoderInterface;
  private LoopFileInterface loopFileInterface;
  private MediaExtractor videoExtractor;
  private MediaCodec videoDecoder;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private boolean decoding;
  private Thread thread;
  private MediaFormat videoFormat;
  private String mime = "";
  private int width;
  private int height;
  private long duration;
  private static boolean loopMode = false;
  private volatile long seekTime = 0;
  private volatile long startMs = 0;

  public VideoDecoder(VideoDecoderInterface videoDecoderInterface,
      LoopFileInterface loopFileInterface) {
    this.videoDecoderInterface = videoDecoderInterface;
    this.loopFileInterface = loopFileInterface;
  }

  public boolean initExtractor(String filePath) throws IOException {
    decoding = false;
    videoExtractor = new MediaExtractor();
    videoExtractor.setDataSource(filePath);
    for (int i = 0; i < videoExtractor.getTrackCount() && !mime.startsWith("video/"); i++) {
      videoFormat = videoExtractor.getTrackFormat(i);
      mime = videoFormat.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
        videoExtractor.selectTrack(i);
      } else {
        videoFormat = null;
      }
    }
    if (videoFormat != null) {
      width = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
      height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
      duration = videoFormat.getLong(MediaFormat.KEY_DURATION);
      return true;
      //video decoder not supported
    } else {
      mime = "";
      videoFormat = null;
      return false;
    }
  }

  public boolean prepareVideo(Surface surface) {
    try {
      videoDecoder = MediaCodec.createDecoderByType(mime);
      videoDecoder.configure(videoFormat, surface, null, 0);
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Prepare decoder error:", e);
      return false;
    }
  }

  public void start() {
    decoding = true;
    videoDecoder.start();
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        decodeVideo();
      }
    });
    thread.start();
  }

  public void stop() {
    decoding = false;
    seekTime = 0;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(100);
      } catch (InterruptedException e) {
        thread.interrupt();
      }
      thread = null;
    }
    if (videoDecoder != null) {
      videoDecoder.stop();
      videoDecoder.release();
      videoDecoder = null;
    }
    if (videoExtractor != null) {
      videoExtractor.release();
      videoExtractor = null;
    }
  }

  private void decodeVideo() {
    ByteBuffer[] inputBuffers = videoDecoder.getInputBuffers();
    startMs = System.currentTimeMillis();
    while (decoding) {
      int inIndex = videoDecoder.dequeueInputBuffer(10000);
      if (inIndex >= 0) {
        ByteBuffer buffer = inputBuffers[inIndex];
        int sampleSize = videoExtractor.readSampleData(buffer, 0);
        if (sampleSize < 0) {
          videoDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          videoDecoder.queueInputBuffer(inIndex, 0, sampleSize, videoExtractor.getSampleTime(), 0);
          videoExtractor.advance();
        }
      }
      int outIndex = videoDecoder.dequeueOutputBuffer(videoInfo, 10000);
      if (outIndex >= 0) {
        while (videoExtractor.getSampleTime() / 1000
            > System.currentTimeMillis() - startMs + seekTime) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            thread.interrupt();
            return;
          }
        }
        videoDecoder.releaseOutputBuffer(outIndex, videoInfo.size != 0);
      }
      if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        seekTime = 0;
        Log.i(TAG, "end of file out");
        if (loopMode) {
          loopFileInterface.onReset(true);
        } else {
          videoDecoderInterface.onVideoDecoderFinished();
        }
      }
    }
  }

  public double getTime() {
    if (decoding) {
      return videoExtractor.getSampleTime() / 10E5;
    } else {
      return 0;
    }
  }

  public void moveTo(double time) {
    videoExtractor.seekTo((long) (time * 10E5), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    seekTime = videoExtractor.getSampleTime() / 1000;
    startMs = System.currentTimeMillis();
  }

  public void setLoopMode(boolean loopMode) {
    this.loopMode = loopMode;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public double getDuration() {
    return duration / 10E5;
  }
}
