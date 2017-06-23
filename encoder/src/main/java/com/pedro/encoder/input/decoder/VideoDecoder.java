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

  private MediaExtractor videoExtractor;
  private MediaCodec videoDecoder;
  private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private boolean eosReceived;
  private Thread thread;
  private MediaFormat videoFormat;
  private String mime;
  private int width;
  private int height;
  private int fps;

  public VideoDecoder() {
  }

  public void initExtractor(String filePath) throws IOException {
    eosReceived = false;
    videoExtractor = new MediaExtractor();
    videoExtractor.setDataSource(filePath);
    for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
      videoFormat = videoExtractor.getTrackFormat(i);
      mime = videoFormat.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
        videoExtractor.selectTrack(i);
        break;
      }
    }
    width = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
    height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
    fps = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
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
    eosReceived = true;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join();
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
    long startMs = System.currentTimeMillis();
    while (!eosReceived) {
      int inIndex = videoDecoder.dequeueInputBuffer(-1);
      if (inIndex >= 0) {
        ByteBuffer buffer = inputBuffers[inIndex];
        int sampleSize = videoExtractor.readSampleData(buffer, 0);
        if (sampleSize < 0) {
          Log.i(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
          videoDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
          videoDecoder.queueInputBuffer(inIndex, 0, sampleSize, videoExtractor.getSampleTime(), 0);
          videoExtractor.advance();
        }

        int outIndex = videoDecoder.dequeueOutputBuffer(videoInfo, 0);
        if (outIndex >= 0) {
          //needed for fix decode speed
          while (videoInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              thread.interrupt();
              break;
            }
          }
          //true because I want draw in the surface
          videoDecoder.releaseOutputBuffer(outIndex, true);
        }
        // All decoded frames have been rendered, we can stop playing now
        if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          Log.i(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
          stop();
          break;
        }
      }
    }
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getFps() {
    return fps;
  }
}
