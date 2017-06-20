package com.pedro.encoder.input.decoder;

import android.view.Surface;
import com.pedro.encoder.input.video.GetCameraData;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

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
  private GetCameraData getCameraData;
  private int width;
  private int height;

  public VideoDecoder(GetCameraData getCameraData) {
    this.getCameraData = getCameraData;
  }

  public boolean prepareVideo(Surface surface, String filePath) {
    try {
      eosReceived = false;
      videoExtractor = new MediaExtractor();
      try {
        videoExtractor.setDataSource(filePath);
      } catch (IOException e) {
        e.printStackTrace();
      }

      MediaFormat format = null;
      String mime = "video/avc";
      for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
        format = videoExtractor.getTrackFormat(i);
        mime = format.getString(MediaFormat.KEY_MIME);
        if (mime.startsWith("video/")) {
          videoExtractor.selectTrack(i);
          break;
        }
      }
      width = format.getInteger(MediaFormat.KEY_WIDTH);
      height = format.getInteger(MediaFormat.KEY_HEIGHT);
      videoDecoder = MediaCodec.createDecoderByType(mime);
      videoDecoder.configure(format, surface, null, 0);
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
    ByteBuffer[] outputBuffers = videoDecoder.getOutputBuffers();
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
        switch (outIndex) {
          case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
            Log.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
            outputBuffers = videoDecoder.getOutputBuffers();
            break;
          case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
            break;
          case MediaCodec.INFO_TRY_AGAIN_LATER:
            break;
          default:
            ByteBuffer outBuffer = outputBuffers[outIndex];
            //needed for fix decode speed
            while (videoInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                e.printStackTrace();
                break;
              }
            }
            byte[] yuvBuffer = new byte[videoInfo.size];
            outBuffer.get(yuvBuffer);
            getCameraData.inputNv21Data(yuvBuffer);
            outBuffer.clear();
            videoDecoder.releaseOutputBuffer(outIndex, false);
            break;
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
}
